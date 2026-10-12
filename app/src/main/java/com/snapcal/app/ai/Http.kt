package com.snapcal.app.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

internal object Http {
    val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        // Reasoning models can take a while on multi-photo meals.
        .readTimeout(5, TimeUnit.MINUTES)
        .callTimeout(6, TimeUnit.MINUTES)
        .build()

    val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    suspend fun postJson(url: String, headers: Map<String, String>, body: JsonObject): JsonObject =
        withContext(Dispatchers.IO) {
            val req = Request.Builder()
                .url(url)
                .apply { headers.forEach { (k, v) -> header(k, v) } }
                .post(body.toString().toRequestBody("application/json".toMediaType()))
                .build()
            val call = runCatching { client.newCall(req).execute() }.getOrElse { e ->
                throw AnalysisException(networkHint(url, e), e)
            }
            call.use { resp ->
                val text = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    throw AnalysisException("HTTP ${resp.code}: ${errorMessage(text)}")
                }
                json.parseToJsonElement(text).jsonObject
            }
        }

    private fun errorMessage(text: String): String = runCatching {
        val root = json.parseToJsonElement(text).jsonObject
        val err: JsonElement? = root["error"]
        when {
            err is JsonObject -> err["message"]?.jsonPrimitive?.content
            err != null -> err.jsonPrimitive.content
            // Mistral / FastAPI style
            root["detail"] != null -> root["detail"].toString().trim('"')
            root["message"] != null -> root["message"]!!.jsonPrimitive.content
            else -> null
        }
    }.getOrNull() ?: text.take(300)

    /** Turn low-level network failures into an actionable message (common in mainland China without a proxy). */
    fun networkHint(url: String, e: Throwable): String {
        val host = runCatching { java.net.URI(url).host }.getOrNull() ?: url
        val reachableInChina = host.endsWith("deepseek.com")
        return when (e) {
            is javax.net.ssl.SSLPeerUnverifiedException, is javax.net.ssl.SSLHandshakeException ->
                "连不上 $host（证书不匹配，通常是网络被劫持或 DNS 污染）。" +
                    if (reachableInChina) "" else "国内网络访问海外 AI 需要代理，或在设置里填可用的 Base URL，或改用 DeepSeek。"
            is java.net.UnknownHostException, is java.net.ConnectException, is java.net.SocketTimeoutException ->
                "连不上 $host。" + if (reachableInChina) "请检查网络。" else "国内网络访问海外 AI 需要代理，或改用 DeepSeek。"
            else -> "网络错误：${e.message ?: e}"
        }
    }
}
