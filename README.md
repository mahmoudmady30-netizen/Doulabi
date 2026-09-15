# دولابي | Doulabi

Premium bilingual Android wardrobe app built with Kotlin + Jetpack Compose.

## Included
- Arabic RTL and English LTR UI.
- First-run onboarding with avatar style and optional profile photo.
- Add clothing from camera or gallery.
- Local wardrobe storage with categories, seasons, occasions, colors, materials and favorites.
- Search, filtering, edit, delete and favorites.
- Offline smart outfit recommendation engine.
- Occasion and wardrobe analytics.
- Dark premium UI with gold accents.
- **AI Vision provider manager** with runtime API-key configuration.
- Supported AI providers: OpenAI / ChatGPT API, Groq, Google Gemini, Anthropic Claude, OpenRouter, and Custom OpenAI-compatible endpoints.
- Multiple providers can be saved; one can be selected as the active provider.
- API keys are encrypted locally with Android Keystore (AES-GCM) and are never hard-coded in source code.
- AI Vision can analyze a clothing photo and automatically fill item name, category, season, occasion, color, material and confidence.
- Images are resized/compressed before upload to reduce payload size.
- Connection-test button for every configured provider.
- Unit tests for recommendation logic and AI provider configuration.
- GitHub Actions build + test workflow.

## AI provider setup
Open **Settings → AI Vision → Manage AI providers & API keys**.

1. Select a provider.
2. Enable it.
3. Paste your API key.
4. Confirm the model and, for Custom/OpenRouter, the base URL.
5. Tap **Test**.
6. Choose the provider as the active provider.
7. Add a clothing photo and tap **Analyze with AI Vision**.

### Security note
The app stores keys encrypted on the device, but a mobile app that calls a provider directly cannot make a user-supplied API key completely invisible to the device owner. For a public production release, use a backend/proxy with server-side secrets and provider-level quotas/restrictions. Never commit a real API key to GitHub.

## Current provider defaults
- OpenAI: `gpt-5.6-luna`
- Groq: `qwen/qwen3.6-27b`
- Gemini: `gemini-3.6-flash`
- Anthropic: `claude-sonnet-5`
- OpenRouter: `openai/gpt-5.6-luna`

Models can be edited in the app because providers change model availability over time.

## Build on GitHub
Push the project to a GitHub repository. The workflow in `.github/workflows/android.yml` runs automatically and uploads `app-debug.apk` as an artifact.
