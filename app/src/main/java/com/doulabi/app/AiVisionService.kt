package com.doulabi.app

import android.util.Base64
import android.graphics.BitmapFactory
import java.io.ByteArrayOutputStream
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.Locale
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/** Provider-agnostic AI Vision layer. Keys are supplied at runtime from the encrypted local store. */
enum class AiProvider(val title: String, val defaultModel: String, val defaultBaseUrl: String, val kind: Kind) {
    OPENAI("OpenAI / ChatGPT API", "gpt-5.6-luna", "https://api.openai.com", Kind.OPENAI_RESPONSES),
    GROQ("Groq", "qwen/qwen3.6-27b", "https://api.groq.com", Kind.OPENAI_RESPONSES),
    GEMINI("Google Gemini", "gemini-3.6-flash", "https://generativelanguage.googleapis.com", Kind.GEMINI),
    ANTHROPIC("Anthropic Claude", "claude-sonnet-5", "https://api.anthropic.com", Kind.ANTHROPIC),
    OPENROUTER("OpenRouter", "openai/gpt-5.6-luna", "https://openrouter.ai/api", Kind.OPENAI_CHAT),
    CUSTOM("Custom OpenAI-compatible", "", "https://", Kind.OPENAI_CHAT);

    enum class Kind { OPENAI_RESPONSES, OPENAI_CHAT, GEMINI, ANTHROPIC }
}

data class AiProviderConfig(
    val enabled: Boolean = false,
    val apiKey: String = "",
    val model: String = "",
    val baseUrl: String = ""
)

data class AiSettings(
    val activeProvider: AiProvider = AiProvider.OPENAI,
    val providers: Map<AiProvider, AiProviderConfig> = defaultAiProviders()
) {
    fun activeConfig(): AiProviderConfig = providers[activeProvider] ?: AiProviderConfig()
}

fun defaultAiProviders(): Map<AiProvider, AiProviderConfig> = AiProvider.values().associateWith {
    AiProviderConfig(model = it.defaultModel, baseUrl = it.defaultBaseUrl)
}

data class AiAnalysis(
    val name: String,
    val category: Category,
    val season: Season,
    val occasion: Occasion,
    val color: String,
    val material: String,
    val confidence: Double,
    val notes: String
)

class AiVisionException(message: String) : Exception(message)

object AiVisionService {
    private val client = OkHttpClient.Builder().build()
    private val jsonMedia = "application/json; charset=utf-8".toMediaType()

    fun analyze(imagePath: String, settings: AiSettings, lang: Lang): AiAnalysis {
        val provider = settings.activeProvider
        val config = settings.activeConfig()
        if (!config.enabled) throw AiVisionException("${provider.title}: provider is disabled")
        if (config.apiKey.isBlank()) throw AiVisionException("${provider.title}: API key is missing")
        val file = File(imagePath)
        if (!file.exists()) throw AiVisionException("Image file not found")
        val prepared = prepareImage(file)
        val b64 = Base64.encodeToString(prepared, Base64.NO_WRAP)
        val mime = "image/jpeg"
        val prompt = buildPrompt(lang)
        val raw = when (provider.kind) {
            AiProvider.Kind.GEMINI -> callGemini(provider, config, b64, mime, prompt)
            AiProvider.Kind.ANTHROPIC -> callAnthropic(provider, config, b64, mime, prompt)
            AiProvider.Kind.OPENAI_RESPONSES -> callResponses(provider, config, b64, mime, prompt)
            AiProvider.Kind.OPENAI_CHAT -> callChat(provider, config, b64, mime, prompt)
        }
        return parseAnalysis(raw, lang)
    }

    fun test(provider: AiProvider, config: AiProviderConfig, lang: Lang): String {
        if (config.apiKey.isBlank()) throw AiVisionException("API key is missing")
        val prompt = if (lang == Lang.AR) "Reply with exactly the word OK." else "Reply with exactly the word OK."
        val raw = when (provider.kind) {
            AiProvider.Kind.GEMINI -> callGemini(provider, config, onePixelBase64(), "image/png", prompt)
            AiProvider.Kind.ANTHROPIC -> callAnthropic(provider, config, onePixelBase64(), "image/png", prompt)
            AiProvider.Kind.OPENAI_RESPONSES -> callResponses(provider, config, onePixelBase64(), "image/png", prompt)
            AiProvider.Kind.OPENAI_CHAT -> callChat(provider, config, onePixelBase64(), "image/png", prompt)
        }
        return raw.trim().ifEmpty { "OK" }
    }

    private fun buildPrompt(lang: Lang): String {
        val outputLanguage = if (lang == Lang.AR) "Arabic" else "English"
        return """
You are Doulabi's professional fashion vision classifier. Analyze the clothing item in the image.
Return ONLY one valid JSON object, with no markdown and no extra text.
Use these exact enum values:
category = TOP | BOTTOM | SHOES | OUTERWEAR | ACCESSORY
season = SUMMER | WINTER | ALL
occasion = CASUAL | FORMAL | WORK | SPORT | PARTY
Fields:
{name, category, season, occasion, color, material, confidence, notes}
Name, color, material and notes must be in $outputLanguage.
confidence must be a number from 0 to 1.
If the item is ambiguous, choose the closest category and lower confidence.
""".trimIndent()
    }

    private fun callResponses(provider: AiProvider, config: AiProviderConfig, b64: String, mime: String, prompt: String): String {
        val base = normalizeBase(config.baseUrl, provider.defaultBaseUrl)
        val url = if (base.endsWith("/v1")) "$base/responses" else "$base/v1/responses"
        val input = JSONArray().put(JSONObject().apply {
            put("role", "user")
            put("content", JSONArray()
                .put(JSONObject().put("type", "input_text").put("text", prompt))
                .put(JSONObject().put("type", "input_image").put("detail", "auto").put("image_url", "data:$mime;base64,$b64")))
        })
        val body = JSONObject().apply { put("model", config.model); put("input", input) }
        val response = post(url, mapOf("Authorization" to "Bearer ${config.apiKey}"), body)
        val obj = JSONObject(response)
        obj.optString("output_text").takeIf { it.isNotBlank() }?.let { return it }
        val output = obj.optJSONArray("output") ?: throw AiVisionException("No output returned by ${provider.title}")
        for (i in 0 until output.length()) {
            val content = output.optJSONObject(i)?.optJSONArray("content") ?: continue
            for (j in 0 until content.length()) {
                val text = content.optJSONObject(j)?.optString("text") ?: ""
                if (text.isNotBlank()) return text
            }
        }
        throw AiVisionException("Could not read ${provider.title} response")
    }

    private fun callChat(provider: AiProvider, config: AiProviderConfig, b64: String, mime: String, prompt: String): String {
        val base = normalizeBase(config.baseUrl, provider.defaultBaseUrl)
        val url = when {
            base.endsWith("/chat/completions") -> base
            base.endsWith("/v1") -> "$base/chat/completions"
            else -> "$base/v1/chat/completions"
        }
        val content = JSONArray()
            .put(JSONObject().put("type", "text").put("text", prompt))
            .put(JSONObject().put("type", "image_url").put("image_url", JSONObject().put("url", "data:$mime;base64,$b64")))
        val messages = JSONArray().put(JSONObject().put("role", "user").put("content", content))
        val body = JSONObject().apply { put("model", config.model); put("messages", messages); put("temperature", 0.1); put("max_tokens", 700) }
        val headers = mutableMapOf("Authorization" to "Bearer ${config.apiKey}")
        if (provider == AiProvider.OPENROUTER) {
            headers["HTTP-Referer"] = "https://doulabi.app"
            headers["X-Title"] = "Doulabi"
        }
        val response = post(url, headers, body)
        val obj = JSONObject(response)
        val choices = obj.optJSONArray("choices") ?: throw AiVisionException("No choices returned by ${provider.title}")
        val message = choices.optJSONObject(0)?.optJSONObject("message") ?: throw AiVisionException("Empty response from ${provider.title}")
        return extractChatContent(message.opt("content"))
    }

    private fun callGemini(provider: AiProvider, config: AiProviderConfig, b64: String, mime: String, prompt: String): String {
        val base = normalizeBase(config.baseUrl, provider.defaultBaseUrl).trimEnd('/')
        val url = "$base/v1beta/models/${config.model}:generateContent?key=${config.apiKey}"
        val parts = JSONArray()
            .put(JSONObject().put("text", prompt))
            .put(JSONObject().put("inline_data", JSONObject().put("mime_type", mime).put("data", b64)))
        val body = JSONObject().put("contents", JSONArray().put(JSONObject().put("parts", parts)))
        val response = post(url, emptyMap(), body)
        val candidates = JSONObject(response).optJSONArray("candidates") ?: throw AiVisionException("No candidates returned by Gemini")
        val content = candidates.optJSONObject(0)?.optJSONObject("content")?.optJSONArray("parts") ?: throw AiVisionException("Empty Gemini response")
        for (i in 0 until content.length()) {
            val text = content.optJSONObject(i)?.optString("text") ?: ""
            if (text.isNotBlank()) return text
        }
        throw AiVisionException("Could not read Gemini response")
    }

    private fun callAnthropic(provider: AiProvider, config: AiProviderConfig, b64: String, mime: String, prompt: String): String {
        val base = normalizeBase(config.baseUrl, provider.defaultBaseUrl).trimEnd('/')
        val url = if (base.endsWith("/v1")) "$base/messages" else "$base/v1/messages"
        val content = JSONArray()
            .put(JSONObject().put("type", "image").put("source", JSONObject().put("type", "base64").put("media_type", mime).put("data", b64)))
            .put(JSONObject().put("type", "text").put("text", prompt))
        val body = JSONObject().apply { put("model", config.model); put("max_tokens", 900); put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", content))) }
        val response = post(url, mapOf("x-api-key" to config.apiKey, "anthropic-version" to "2023-06-01"), body)
        val contentOut = JSONObject(response).optJSONArray("content") ?: throw AiVisionException("No content returned by Claude")
        for (i in 0 until contentOut.length()) {
            val text = contentOut.optJSONObject(i)?.optString("text") ?: ""
            if (text.isNotBlank()) return text
        }
        throw AiVisionException("Could not read Claude response")
    }

    private fun extractChatContent(value: Any?): String = when (value) {
        is String -> value
        is JSONArray -> buildString {
            for (i in 0 until value.length()) append(value.optJSONObject(i)?.optString("text") ?: "")
        }
        else -> ""
    }.trim()

    private fun post(url: String, headers: Map<String, String>, body: JSONObject): String {
        val request = Request.Builder().url(url).post(body.toString().toRequestBody(jsonMedia)).apply { headers.forEach { (k, v) -> addHeader(k, v) } }.build()
        client.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                val detail = runCatching { JSONObject(text).optString("message").ifBlank { JSONObject(text).optString("error") } }.getOrDefault("")
                throw AiVisionException("HTTP ${response.code}${if (detail.isNotBlank()) ": $detail" else ""}")
            }
            return text
        }
    }

    private fun normalizeBase(value: String, fallback: String): String = (value.ifBlank { fallback }).trimEnd('/')

    private fun parseAnalysis(raw: String, lang: Lang): AiAnalysis {
        val cleaned = raw.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
        val start = cleaned.indexOf('{'); val end = cleaned.lastIndexOf('}')
        if (start < 0 || end <= start) throw AiVisionException("AI returned invalid JSON")
        val o = JSONObject(cleaned.substring(start, end + 1))
        val category = parseCategory(o.optString("category"))
        val season = parseSeason(o.optString("season"))
        val occasion = parseOccasion(o.optString("occasion"))
        val name = o.optString("name").ifBlank { if (lang == Lang.AR) "قطعة ملابس" else "Clothing item" }
        return AiAnalysis(name, category, season, occasion, o.optString("color"), o.optString("material"), o.optDouble("confidence", 0.0).coerceIn(0.0, 1.0), o.optString("notes"))
    }

    private fun parseCategory(v: String) = when (v.uppercase(Locale.US)) {
        "BOTTOM", "PANTS", "TROUSERS" -> Category.BOTTOM
        "SHOES", "FOOTWEAR" -> Category.SHOES
        "OUTERWEAR", "JACKET", "COAT" -> Category.OUTERWEAR
        "ACCESSORY", "ACCESSORIES" -> Category.ACCESSORY
        else -> Category.TOP
    }
    private fun parseSeason(v: String) = when (v.uppercase(Locale.US)) { "SUMMER" -> Season.SUMMER; "WINTER" -> Season.WINTER; else -> Season.ALL }
    private fun parseOccasion(v: String) = when (v.uppercase(Locale.US)) { "FORMAL" -> Occasion.FORMAL; "WORK", "BUSINESS" -> Occasion.WORK; "SPORT", "SPORTS" -> Occasion.SPORT; "PARTY", "EVENT" -> Occasion.PARTY; else -> Occasion.CASUAL }


    private fun prepareImage(file: File): ByteArray {
        val bitmap = BitmapFactory.decodeFile(file.absolutePath) ?: throw AiVisionException("Unsupported image format")
        val maxSide = 1600
        val scale = minOf(1f, maxSide.toFloat() / maxOf(bitmap.width, bitmap.height).toFloat())
        val resized = if (scale < 1f) android.graphics.Bitmap.createScaledBitmap(bitmap, (bitmap.width * scale).toInt().coerceAtLeast(1), (bitmap.height * scale).toInt().coerceAtLeast(1), true) else bitmap
        val out = ByteArrayOutputStream()
        if (!resized.compress(android.graphics.Bitmap.CompressFormat.JPEG, 82, out)) throw AiVisionException("Could not compress image")
        if (resized !== bitmap) resized.recycle()
        bitmap.recycle()
        return out.toByteArray()
    }

    private fun onePixelBase64(): String = "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII="
}
