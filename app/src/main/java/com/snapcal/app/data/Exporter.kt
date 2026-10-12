package com.snapcal.app.data

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import java.time.LocalDate
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Exports every stored record (including soft-deleted meals and all raw scale readings / AI outputs)
 * as CSV files in one zip, for offline analysis.
 */
class Exporter(private val context: Context, private val db: AppDatabase, private val settings: Settings) {

    suspend fun export(): File {
        val dir = File(context.cacheDir, "export").apply { mkdirs(); listFiles()?.forEach { it.delete() } }
        val out = File(dir, "snapcal-${LocalDate.now()}.zip")
        ZipOutputStream(FileOutputStream(out)).use { zip ->
            fun put(name: String, header: List<String>, rows: List<List<Any?>>) {
                zip.putNextEntry(ZipEntry(name))
                val sb = StringBuilder()
                sb.append('﻿') // BOM so Excel opens UTF-8 Chinese correctly
                sb.append(header.joinToString(",") { csv(it) }).append('\n')
                rows.forEach { r -> sb.append(r.joinToString(",") { csv(it) }).append('\n') }
                zip.write(sb.toString().toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }

            val meals = db.mealDao().allIncludingDeleted()
            put(
                "meals.csv",
                listOf("id", "day", "eaten_at", "title", "total_kcal", "ai_kcal", "verified", "deleted", "protein_g", "carbs_g", "fat_g",
                    "confidence", "provider", "model", "note", "ai_notes", "photo_count", "version"),
                meals.map { listOf(it.id, it.day, it.eatenAt, it.title, it.totalKcal, it.aiKcal, it.verified, it.deleted,
                    it.proteinG, it.carbsG, it.fatG, it.confidence, it.provider, it.model, it.note, it.aiNotes, it.photos.size, it.version) },
            )
            put(
                "meal_items.csv",
                listOf("meal_id", "name", "portion", "grams", "kcal", "protein_g", "carbs_g", "fat_g"),
                meals.flatMap { m -> m.items.map { listOf(m.id, it.name, it.portion, it.grams, it.kcal, it.proteinG, it.carbsG, it.fatG) } },
            )
            val raw = db.rawDao()
            put(
                "ai_calls.csv",
                listOf("id", "meal_id", "at", "provider", "model", "prompt_version", "note", "image_count", "parsed_kcal", "latency_ms", "error", "raw_text"),
                raw.allAiCalls().map { listOf(it.id, it.mealId, it.at, it.provider, it.model, it.promptVersion, it.note, it.imageCount,
                    it.parsedKcal, it.latencyMs, it.error, it.rawText) },
            )
            put(
                "weights.csv",
                listOf("day", "kg", "measured_at", "impedance_50k", "impedance_250k", "heart_rate", "source"),
                db.weightDao().allNow().map { listOf(it.day, it.kg, it.measuredAt, it.impedance50, it.impedance250, it.heartRate, it.source) },
            )
            put(
                "scale_readings.csv",
                listOf("id", "day", "at", "kg", "impedance_50k", "impedance_250k", "heart_rate", "profile_id", "disposition"),
                raw.allReadings().map { listOf(it.id, it.day, it.at, it.massKg, it.impedance50, it.impedance250, it.heartRate, it.profileId, it.disposition) },
            )
            put(
                "daily_snapshots.csv",
                listOf("day", "intake_kcal", "intake_calibrated_kcal", "meal_count", "protein_g", "weight_kg", "tdee_prior", "tdee_adaptive",
                    "activity_adjustment", "burned_kcal", "target_intake", "photo_calibration_factor", "updated_at"),
                raw.allSnapshots().map { listOf(it.day, it.intakeKcal, it.intakeCalibratedKcal, it.mealCount, it.proteinG, it.weightKg,
                    it.tdeePrior, it.tdeeAdaptive, it.activityAdjustment, it.burnedKcal, it.targetIntake, it.photoCalibrationFactor, it.updatedAt) },
            )
            val p = settings.profile
            put(
                "profile.csv",
                listOf("sex", "age", "height_cm", "weight_kg", "body_fat_pct", "bmr_override", "pal", "target_deficit", "goal_weight_kg", "tracking_start_epoch_day"),
                listOf(listOf(p.sex, p.age, p.heightCm, p.weightKg, p.bodyFatPct, p.bmrOverride, p.pal, p.targetDeficit, p.goalWeightKg, settings.trackingStartEpochDay)),
            )
        }
        return out
    }

    fun shareIntent(file: File): Intent {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        return Intent(Intent.ACTION_SEND)
            .setType("application/zip")
            .putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    private fun csv(v: Any?): String {
        val s = when (v) {
            null -> ""
            is Double -> if (v.isNaN()) "" else v.toString()
            else -> v.toString()
        }
        return if (s.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) "\"" + s.replace("\"", "\"\"") + "\"" else s
    }
}
