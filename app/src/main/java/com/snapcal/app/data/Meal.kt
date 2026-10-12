package com.snapcal.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

@Serializable
data class FoodItem(
    val name: String,
    val portion: String = "",
    val grams: Double? = null,
    val kcal: Int,
    val proteinG: Double? = null,
    val carbsG: Double? = null,
    val fatG: Double? = null,
)

@Entity(tableName = "meals")
data class Meal(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** Epoch millis when the meal was eaten (defaults to capture time). */
    val eatenAt: Long,
    /** Local date `yyyy-MM-dd`, used for grouping by day. */
    val day: String,
    val title: String,
    val itemsJson: String,
    val totalKcal: Int,
    val note: String = "",
    /** Newline-separated absolute paths of the app-private photo copies. */
    val photoPaths: String = "",
    val provider: String = "",
    val model: String = "",
    val confidence: String = "",
    val aiNotes: String = "",
    /** Model's optional follow-up question; cleared once answered. */
    val aiQuestion: String = "",
    /** Bumped on each edit so Health Connect upserts replace the previous version. */
    val version: Long = 1,
    /** The model's latest total before any manual edit — the baseline for photo-estimate calibration. */
    val aiKcal: Int = 0,
    /** User confirmed [totalKcal] from a nutrition label or by weighing the food. */
    val verified: Boolean = false,
    /** Soft delete: hidden everywhere but kept for analysis. */
    val deleted: Boolean = false,
)

private val itemsJsonFormat = Json { ignoreUnknownKeys = true }
private val itemsSerializer = ListSerializer(FoodItem.serializer())

fun encodeItems(items: List<FoodItem>): String = itemsJsonFormat.encodeToString(itemsSerializer, items)
fun decodeItems(s: String): List<FoodItem> =
    runCatching { itemsJsonFormat.decodeFromString(itemsSerializer, s) }.getOrDefault(emptyList())

val Meal.items: List<FoodItem> get() = decodeItems(itemsJson)
val Meal.photos: List<String> get() = photoPaths.split('\n').filter { it.isNotBlank() }
val Meal.proteinG: Double get() = items.sumOf { it.proteinG ?: 0.0 }
val Meal.carbsG: Double get() = items.sumOf { it.carbsG ?: 0.0 }
val Meal.fatG: Double get() = items.sumOf { it.fatG ?: 0.0 }

data class DayTotal(val day: String, val totalKcal: Int, val mealCount: Int)
