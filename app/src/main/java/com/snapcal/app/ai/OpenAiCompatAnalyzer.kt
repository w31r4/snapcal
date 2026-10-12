package com.snapcal.app.ai

import android.util.Base64
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/**
 * Chat Completions — used by OpenAI and by DeepSeek (whose API is OpenAI-compatible).
 * OpenAI gets a strict json_schema; DeepSeek only supports json_object mode, so the schema goes in the prompt.
 */
class OpenAiCompatAnalyzer(
    private val config: ProviderConfig,
    private val strictSchema: Boolean,
) : FoodAnalyzer {

    override suspend fun analyze(images: List<ImagePayload>, note: String): AnalyzerResult {
        val body = buildRequest(config.model, images.map { it.mediaType to b64(it.bytes) }, note, strictSchema)
        val resp = Http.postJson(
            url = "${config.effectiveBaseUrl}/chat/completions",
            headers = mapOf("Authorization" to "Bearer ${config.apiKey}"),
            body = body,
        )
        val text = extractText(resp)
        return AnalyzerResult(FoodAnalysis.parse(text), text)
    }

    companion object {
        private fun b64(bytes: ByteArray) = Base64.encodeToString(bytes, Base64.NO_WRAP)

        /** Pure function so it can be unit-tested. [images] are (mediaType, base64) pairs. */
        fun buildRequest(model: String, images: List<Pair<String, String>>, note: String, strictSchema: Boolean): JsonObject =
            buildJsonObject {
                put("model", model)
                putJsonArray("messages") {
                    addJsonObject {
                        put("role", "system")
                        put(
                            "content",
                            if (strictSchema) Prompt.SYSTEM
                            else Prompt.SYSTEM + "\n\n输出 JSON 必须符合这个 JSON Schema：\n" + Prompt.SCHEMA_JSON,
                        )
                    }
                    addJsonObject {
                        put("role", "user")
                        putJsonArray("content") {
                            images.forEach { (mediaType, data) ->
                                addJsonObject {
                                    put("type", "image_url")
                                    putJsonObject("image_url") { put("url", "data:$mediaType;base64,$data") }
                                }
                            }
                            addJsonObject {
                                put("type", "text")
                                put("text", Prompt.user(note, images.size))
                            }
                        }
                    }
                }
                putJsonObject("response_format") {
                    if (strictSchema) {
                        put("type", "json_schema")
                        putJsonObject("json_schema") {
                            put("name", "food_analysis")
                            put("strict", true)
                            put("schema", Prompt.schema)
                        }
                    } else {
                        put("type", "json_object")
                    }
                }
            }

        fun extractText(resp: JsonObject): String {
            val message = resp["choices"]?.jsonArray?.firstOrNull()?.jsonObject?.get("message")?.jsonObject
                ?: throw AnalysisException("返回里没有 choices：${resp.toString().take(300)}")
            message["refusal"]?.jsonPrimitive?.contentOrNull?.let { throw AnalysisException("模型拒绝了请求：$it") }
            return message["content"]?.jsonPrimitive?.contentOrNull
                ?: throw AnalysisException("模型返回为空")
        }
    }
}

