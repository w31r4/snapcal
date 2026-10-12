package com.snapcal.app.ai

import com.anthropic.client.okhttp.AnthropicOkHttpClient
import com.anthropic.core.JsonValue
import com.anthropic.errors.AnthropicServiceException
import com.anthropic.models.beta.messages.BetaBase64ImageSource
import com.anthropic.models.beta.messages.BetaContentBlockParam
import com.anthropic.models.beta.messages.BetaImageBlockParam
import com.anthropic.models.beta.messages.BetaJsonOutputFormat
import com.anthropic.models.beta.messages.BetaOutputConfig
import com.anthropic.models.beta.messages.BetaStopReason
import com.anthropic.models.beta.messages.MessageCreateParams
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull
import java.time.Duration
import java.util.Base64

/** Claude via the official Anthropic Java SDK, with JSON-schema structured output. */
class AnthropicAnalyzer(private val config: ProviderConfig) : FoodAnalyzer {

    private val client by lazy {
        AnthropicOkHttpClient.builder()
            .apiKey(config.apiKey)
            .apply { if (config.baseUrl.isNotBlank()) baseUrl(config.baseUrl) }
            .timeout(Duration.ofMinutes(5))
            .build()
    }

    override suspend fun analyze(images: List<ImagePayload>, note: String): AnalyzerResult = withContext(Dispatchers.IO) {
        val blocks = images.map { img ->
            BetaContentBlockParam.ofImage(
                BetaImageBlockParam.builder()
                    .source(
                        BetaBase64ImageSource.builder()
                            .mediaType(BetaBase64ImageSource.MediaType.of(img.mediaType))
                            .data(Base64.getEncoder().encodeToString(img.bytes))
                            .build(),
                    )
                    .build(),
            )
        } + BetaContentBlockParam.ofText(Prompt.user(note, images.size))

        val params = MessageCreateParams.builder()
            .model(config.model)
            .maxTokens(16000L)
            .system(Prompt.SYSTEM)
            .addUserMessageOfBetaContentBlockParams(blocks)
            .outputConfig(
                BetaOutputConfig.builder()
                    .effort(BetaOutputConfig.Effort.MEDIUM)
                    .format(
                        BetaJsonOutputFormat.builder()
                            .schema(
                                BetaJsonOutputFormat.Schema.builder()
                                    .putAllAdditionalProperties(Prompt.schema.mapValues { toJsonValue(it.value) })
                                    .build(),
                            )
                            .build(),
                    )
                    .build(),
            )
            .apply {
                // On a safety-classifier refusal, let the server retry on a suitable fallback model.
                if (config.baseUrl.isBlank() && config.model in FALLBACK_MODELS) {
                    addBeta("server-side-fallback-2026-07-01")
                    fallbacksDefault()
                }
            }
            .build()

        val message = try {
            client.beta().messages().create(params)
        } catch (e: AnthropicServiceException) {
            val detail = Regex("\"message\":\"([^\"]*)\"").find(e.message.orEmpty())?.groupValues?.get(1) ?: e.message
            throw AnalysisException("HTTP ${e.statusCode()}: $detail", e)
        } catch (e: com.anthropic.errors.AnthropicIoException) {
            throw AnalysisException(Http.networkHint(config.baseUrl.ifBlank { config.provider.defaultBaseUrl }, e.cause ?: e), e)
        }

        if (message.stopReason().orElse(null) == BetaStopReason.REFUSAL) {
            val why = message.stopDetails().flatMap { it.explanation() }.orElse("")
            throw AnalysisException("Claude 拒绝了这次请求 $why")
        }
        val text = message.content().mapNotNull { it.text().orElse(null)?.text() }.joinToString("")
        if (text.isBlank()) throw AnalysisException("Claude 返回为空（stop_reason=${message.stopReason().orElse(null)}）")
        AnalyzerResult(FoodAnalysis.parse(text), text)
    }

    companion object {
        private val FALLBACK_MODELS = setOf("claude-fable-5-1", "claude-opus-5-5", "claude-opus-5", "claude-sonnet-5-5")

        private fun toJsonValue(e: JsonElement): JsonValue = when (e) {
            is JsonNull -> JsonValue.from(null)
            is JsonObject -> JsonValue.from(e.mapValues { toJsonValue(it.value) })
            is JsonArray -> JsonValue.from(e.map { toJsonValue(it) })
            is JsonPrimitive -> when {
                e.isString -> JsonValue.from(e.content)
                e.booleanOrNull != null -> JsonValue.from(e.booleanOrNull)
                e.longOrNull != null -> JsonValue.from(e.longOrNull)
                else -> JsonValue.from(e.doubleOrNull)
            }
        }
    }
}
