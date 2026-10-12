package com.snapcal.app.ai

import com.snapcal.app.data.FoodItem
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class FoodAnalysis(
    val title: String = "",
    val items: List<FoodItem> = emptyList(),
    val totalKcal: Int = 0,
    /** "high" | "medium" | "low" */
    val confidence: String = "",
    val notes: String = "",
    /** Optional clarifying question; answering it re-runs the estimate with the answer as a hint. */
    val question: String = "",
) {
    /** Trust the per-item numbers over the model's own sum. */
    fun normalized(): FoodAnalysis {
        val sum = items.sumOf { it.kcal }
        return copy(totalKcal = if (items.isNotEmpty()) sum else totalKcal)
    }

    companion object {
        private val json = Json { ignoreUnknownKeys = true; isLenient = true; coerceInputValues = true }

        /**
         * Models occasionally wrap JSON in prose or ``` fences even when asked not to;
         * take the outermost {...} span and decode that.
         */
        fun parse(raw: String): FoodAnalysis {
            val start = raw.indexOf('{')
            val end = raw.lastIndexOf('}')
            require(start >= 0 && end > start) { "模型没有返回 JSON：${raw.take(200)}" }
            return json.decodeFromString(serializer(), raw.substring(start, end + 1)).normalized()
        }
    }
}

class AnalysisException(message: String, cause: Throwable? = null) : Exception(message, cause)
