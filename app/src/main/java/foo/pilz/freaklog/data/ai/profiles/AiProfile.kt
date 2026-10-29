package foo.pilz.freaklog.data.ai.profiles

import foo.pilz.freaklog.data.ai.provider.AiApiStandard
import foo.pilz.freaklog.data.ai.provider.AiChatClient
import foo.pilz.freaklog.data.ai.provider.AiEndpoint
import foo.pilz.freaklog.data.ai.provider.AnthropicMessagesClient
import foo.pilz.freaklog.data.ai.provider.GeminiClient
import foo.pilz.freaklog.data.ai.provider.OpenAiChatClient
import kotlinx.serialization.Serializable
import java.net.URI

/**
 * Starting points for a profile. A preset fills in the API standard, base URL and, where the id
 * is stable, a model. "Fetch models" in the editor lists what the endpoint really offers.
 */
enum class AiProviderPreset(
    val label: String,
    val standard: AiApiStandard,
    val baseUrl: String,
    val suggestedModel: String,
    val requiresApiKey: Boolean,
    val hint: String,
) {
    GEMINI(
        "Google Gemini", AiApiStandard.GEMINI, "https://generativelanguage.googleapis.com/v1beta",
        "gemini-2.5-flash", true, "Create a key at aistudio.google.com.",
    ),
    OPENAI(
        "OpenAI", AiApiStandard.OPENAI_CHAT, "https://api.openai.com/v1",
        "", true, "Create a key at platform.openai.com, then fetch the models.",
    ),
    ANTHROPIC(
        "Anthropic", AiApiStandard.ANTHROPIC_MESSAGES, "https://api.anthropic.com/v1",
        "claude-opus-5-5", true, "Create a key at console.anthropic.com.",
    ),
    OPENROUTER(
        "OpenRouter", AiApiStandard.OPENAI_CHAT, "https://openrouter.ai/api/v1",
        "openrouter/auto", true, "One key for models from many providers (openrouter.ai).",
    ),
    MISTRAL(
        "Mistral", AiApiStandard.OPENAI_CHAT, "https://api.mistral.ai/v1",
        "mistral-large-latest", true, "Create a key at console.mistral.ai.",
    ),
    GROQ(
        "Groq", AiApiStandard.OPENAI_CHAT, "https://api.groq.com/openai/v1",
        "", true, "Create a key at console.groq.com, then fetch the models.",
    ),
    DEEPSEEK(
        "DeepSeek", AiApiStandard.OPENAI_CHAT, "https://api.deepseek.com/v1",
        "deepseek-chat", true, "Create a key at platform.deepseek.com.",
    ),
    OLLAMA(
        "Ollama (self-hosted)", AiApiStandard.OPENAI_CHAT, "http://localhost:11434/v1",
        "", false,
        "For another computer, use an HTTPS reverse proxy in front of Ollama. " +
            "HTTP localhost is only for a server on this device. The Anthropic standard uses the same /v1 URL.",
    ),
    LM_STUDIO(
        "LM Studio (self-hosted)", AiApiStandard.OPENAI_CHAT, "http://localhost:1234/v1",
        "", false,
        "For another computer, use an HTTPS reverse proxy in front of LM Studio. HTTP localhost is only for this device.",
    ),
    CUSTOM_OPENAI(
        "Custom OpenAI-compatible", AiApiStandard.OPENAI_CHAT, "",
        "", false, "Any server with /chat/completions, e.g. vLLM, llama.cpp or LiteLLM. Include /v1 if it uses it.",
    ),
    CUSTOM_ANTHROPIC(
        "Custom Anthropic-compatible", AiApiStandard.ANTHROPIC_MESSAGES, "",
        "", false, "Any server with the Anthropic /messages endpoint. Include /v1 if it uses it.",
    ),
    ;

    val isCustom: Boolean get() = this == CUSTOM_OPENAI || this == CUSTOM_ANTHROPIC

    companion object {
        fun fromId(id: String): AiProviderPreset =
            entries.firstOrNull { it.name == id } ?: CUSTOM_OPENAI
    }
}

/** A saved provider configuration. The API key is stored separately and encrypted. */
@Serializable
data class AiProfile(
    val id: String,
    val name: String,
    val presetId: String,
    val standard: AiApiStandard,
    val baseUrl: String,
    val model: String,
    val maxOutputTokens: Int = DEFAULT_MAX_OUTPUT_TOKENS,
) {
    val preset: AiProviderPreset get() = AiProviderPreset.fromId(presetId)

    /** True when requests would travel unencrypted to another machine. */
    val isInsecureRemote: Boolean get() = isInsecureRemoteUrl(baseUrl)

    companion object {
        const val DEFAULT_MAX_OUTPUT_TOKENS = 16_000
    }
}

/** `http://` URLs that don't point at this device itself. */
fun isInsecureRemoteUrl(url: String): Boolean {
    val uri = runCatching { URI(url.trim()) }.getOrNull() ?: return false
    if (!uri.scheme.equals("http", ignoreCase = true)) return false
    val host = uri.host?.lowercase() ?: return false
    return host != "localhost" && host != "127.0.0.1" && host != "::1" && host != "[::1]"
}

/** Creates the client that speaks [standard]. */
fun aiClientFor(standard: AiApiStandard, endpoint: AiEndpoint): AiChatClient = when (standard) {
    AiApiStandard.OPENAI_CHAT -> OpenAiChatClient(endpoint)
    AiApiStandard.ANTHROPIC_MESSAGES -> AnthropicMessagesClient(endpoint)
    AiApiStandard.GEMINI -> GeminiClient(endpoint)
}
