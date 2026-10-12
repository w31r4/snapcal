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

/** Gemini REST `generateContent` with inline images and JSON-schema constrained output. */
class GeminiAnalyzer(private val config: ProviderConfig) : FoodAnalyzer {

    override suspend fun analyze(images: List<ImagePayload>, note: String): AnalyzerResult {
        val body = buildRequest(images.map { it.mediaType to Base64.encodeToString(it.bytes, Base64.NO_WRAP) }, note)
        val resp = Http.postJson(
            url = "${config.effectiveBaseUrl}/models/${config.model}:generateContent",
            headers = mapOf("x-goog-api-key" to config.apiKey),
            body = body,
        )
        val text = extractText(resp)
        return AnalyzerResult(FoodAnalysis.parse(text), text)
    }

    companion object {
        fun buildRequest(images: List<Pair<String, String>>, note: String): JsonObject = buildJsonObject {
            putJsonObject("systemInstruction") {
                putJsonArray("parts") { addJsonObject { put("text", Prompt.SYSTEM) } }
            }
            putJsonArray("contents") {
                addJsonObject {
                    put("role", "user")
                    putJsonArray("parts") {
                        images.forEach { (mediaType, data) ->
                            addJsonObject {
                                putJsonObject("inlineData") {
                                    put("mimeType", mediaType)
                                    put("data", data)
                                }
                            }
                        }
                        addJsonObject { put("text", Prompt.user(note, images.size)) }
                    }
                }
            }
            putJsonObject("generationConfig") {
                put("responseMimeType", "application/json")
                put("responseJsonSchema", Prompt.schema)
            }
        }

        fun extractText(resp: JsonObject): String {
            val candidate = resp["candidates"]?.jsonArray?.firstOrNull()?.jsonObject
            if (candidate == null) {
                val reason = resp["promptFeedback"]?.jsonObject?.get("blockReason")?.jsonPrimitive?.contentOrNull
                throw AnalysisException(if (reason != null) "请求被拦截：$reason" else "Gemini 没有返回结果")
            }
            val text = candidate["content"]?.jsonObject?.get("parts")?.jsonArray
                ?.mapNotNull { part ->
                    val p = part.jsonObject
                    // Skip thought summaries if the model includes them.
                    if (p["thought"]?.jsonPrimitive?.contentOrNull == "true") null
                    else p["text"]?.jsonPrimitive?.contentOrNull
                }
                ?.joinToString("")
            if (text.isNullOrBlank()) {
                val finish = candidate["finishReason"]?.jsonPrimitive?.contentOrNull
                throw AnalysisException("Gemini 返回为空（finishReason=$finish）")
            }
            return text
        }
    }
}
