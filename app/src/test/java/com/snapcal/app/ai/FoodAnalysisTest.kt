package com.snapcal.app.ai

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FoodAnalysisTest {
    private val sample = """
        {"title":"午餐：牛肉面","items":[
          {"name":"牛肉面","portion":"1 大碗","grams":500,"kcal":650,"proteinG":30,"carbsG":90,"fatG":18},
          {"name":"卤蛋","portion":"1 个","kcal":80,"proteinG":6,"carbsG":1,"fatG":5}
        ],"totalKcal":700,"confidence":"medium","notes":"汤里的油不确定"}
    """.trimIndent()

    @Test fun parsesAndRecomputesTotal() {
        val a = FoodAnalysis.parse(sample)
        assertEquals(2, a.items.size)
        assertEquals(730, a.totalKcal) // trusts item sum over model's 700
    }

    @Test fun toleratesFencesAndProse() {
        val a = FoodAnalysis.parse("好的，结果如下：\n```json\n$sample\n```")
        assertEquals("午餐：牛肉面", a.title)
    }

    @Test fun openAiRequestHasStrictSchemaAndImages() {
        val req = OpenAiCompatAnalyzer.buildRequest("gpt-6.1-sol", listOf("image/jpeg" to "AAA", "image/jpeg" to "BBB"), "半碗饭", strictSchema = true)
        val rf = req["response_format"]!!.jsonObject
        assertEquals("json_schema", rf["type"]!!.jsonPrimitive.content)
        val content = req["messages"]!!.jsonArray[1].jsonObject["content"]!!.jsonArray
        assertEquals(3, content.size)
        assertEquals("data:image/jpeg;base64,AAA", content[0].jsonObject["image_url"]!!.jsonObject["url"]!!.jsonPrimitive.content)
        assertTrue(content[2].jsonObject["text"]!!.jsonPrimitive.content.contains("半碗饭"))
    }

    @Test fun deepSeekUsesJsonObjectModeWithSchemaInPrompt() {
        val req = OpenAiCompatAnalyzer.buildRequest("deepseek-flash", listOf("image/jpeg" to "AAA"), "", strictSchema = false)
        assertEquals("json_object", req["response_format"]!!.jsonObject["type"]!!.jsonPrimitive.content)
        val sys = req["messages"]!!.jsonArray[0].jsonObject["content"]!!.jsonPrimitive.content
        assertTrue(sys.contains("\"totalKcal\""))
    }

    @Test fun openAiExtractsContent() {
        val resp = Json.parseToJsonElement("""{"choices":[{"message":{"role":"assistant","content":"{\"title\":\"x\"}"}}]}""").jsonObject
        assertEquals("{\"title\":\"x\"}", OpenAiCompatAnalyzer.extractText(resp))
    }

    @Test fun geminiRequestShape() {
        val req = GeminiAnalyzer.buildRequest(listOf("image/jpeg" to "AAA"), "")
        val parts = req["contents"]!!.jsonArray[0].jsonObject["parts"]!!.jsonArray
        assertEquals("AAA", parts[0].jsonObject["inlineData"]!!.jsonObject["data"]!!.jsonPrimitive.content)
        assertEquals("application/json", req["generationConfig"]!!.jsonObject["responseMimeType"]!!.jsonPrimitive.content)
    }

    @Test fun geminiExtractsTextSkippingThoughts() {
        val resp = Json.parseToJsonElement(
            """{"candidates":[{"content":{"parts":[{"text":"thinking...","thought":true},{"text":"{\"a\":1}"}]}}]}""",
        ).jsonObject
        assertEquals("{\"a\":1}", GeminiAnalyzer.extractText(resp))
    }
}
