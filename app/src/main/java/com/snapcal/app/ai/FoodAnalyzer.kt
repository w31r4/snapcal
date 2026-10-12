package com.snapcal.app.ai

/** A JPEG already downscaled for upload. */
class ImagePayload(val bytes: ByteArray, val mediaType: String = "image/jpeg")

/** Parsed result plus the model's raw text output, which is kept for later analysis. */
data class AnalyzerResult(val analysis: FoodAnalysis, val rawText: String)

interface FoodAnalyzer {
    suspend fun analyze(images: List<ImagePayload>, note: String): AnalyzerResult
}

data class ProviderConfig(
    val provider: Provider,
    val apiKey: String,
    val model: String,
    val baseUrl: String,
) {
    val effectiveBaseUrl: String get() = baseUrl.ifBlank { provider.defaultBaseUrl }.trimEnd('/')
}

object Analyzers {
    fun create(config: ProviderConfig): FoodAnalyzer {
        if (config.apiKey.isBlank()) throw AnalysisException("还没有填写 ${config.provider.label} 的 API Key，请到设置里填写")
        return when (config.provider) {
            Provider.ANTHROPIC -> AnthropicAnalyzer(config)
            Provider.OPENAI -> OpenAiCompatAnalyzer(config, strictSchema = true)
            // Mistral's chat completions API is OpenAI-compatible, including image_url and json_schema.
            Provider.MISTRAL -> OpenAiCompatAnalyzer(config, strictSchema = true)
            Provider.DEEPSEEK -> OpenAiCompatAnalyzer(config, strictSchema = false)
            Provider.GEMINI -> GeminiAnalyzer(config)
        }
    }
}
