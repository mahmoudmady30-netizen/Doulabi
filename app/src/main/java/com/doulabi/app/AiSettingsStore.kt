package com.doulabi.app

import android.content.Context
import android.util.Base64
import org.json.JSONObject
import java.nio.charset.StandardCharsets
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties

/** Stores provider configuration locally; API keys are encrypted with an Android Keystore AES-GCM key. */
class AiSettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("doulabi_ai", Context.MODE_PRIVATE)
    private val alias = "doulabi_ai_key_v1"

    fun load(): AiSettings {
        val active = runCatching { AiProvider.valueOf(prefs.getString("active", AiProvider.OPENAI.name)!!) }.getOrDefault(AiProvider.OPENAI)
        val map = defaultAiProviders().toMutableMap()
        AiProvider.values().forEach { provider ->
            val raw = prefs.getString("provider_${provider.name}", null) ?: return@forEach
            runCatching {
                val o = JSONObject(raw)
                map[provider] = AiProviderConfig(
                    enabled = o.optBoolean("enabled", false),
                    apiKey = decrypt(o.optString("key", "")),
                    model = o.optString("model", provider.defaultModel),
                    baseUrl = o.optString("baseUrl", provider.defaultBaseUrl)
                )
            }
        }
        return AiSettings(active, map)
    }

    fun save(settings: AiSettings) {
        val edit = prefs.edit().putString("active", settings.activeProvider.name)
        settings.providers.forEach { (provider, config) ->
            edit.putString("provider_${provider.name}", JSONObject().apply {
                put("enabled", config.enabled)
                put("key", encrypt(config.apiKey))
                put("model", config.model)
                put("baseUrl", config.baseUrl)
            }.toString())
        }
        edit.apply()
    }

    private fun getKey(): SecretKey {
        val ks = java.security.KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val existing = ks.getKey(alias, null) as? SecretKey
        if (existing != null) return existing
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setUserAuthenticationRequired(false)
            .build())
        return generator.generateKey()
    }

    private fun encrypt(value: String): String {
        if (value.isBlank()) return ""
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, getKey())
        val encrypted = cipher.doFinal(value.toByteArray(StandardCharsets.UTF_8))
        return Base64.encodeToString(cipher.iv + encrypted, Base64.NO_WRAP)
    }

    private fun decrypt(value: String): String {
        if (value.isBlank()) return ""
        return runCatching {
            val all = Base64.decode(value, Base64.NO_WRAP)
            val iv = all.copyOfRange(0, 12)
            val data = all.copyOfRange(12, all.size)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, getKey(), GCMParameterSpec(128, iv))
            String(cipher.doFinal(data), StandardCharsets.UTF_8)
        }.getOrDefault("")
    }
}
