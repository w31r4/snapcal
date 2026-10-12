package com.snapcal.app.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query

/**
 * Append-only raw records kept for later analysis. Nothing here is ever updated or deleted by the app,
 * so derived values (meal totals, daily weights, adaptive TDEE) can always be recomputed.
 */

/** Every model call: what was sent (minus image bytes) and exactly what came back. */
@Entity(tableName = "ai_calls", indices = [Index("mealId")])
data class AiCall(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val mealId: Long,
    val at: Long,
    val provider: String,
    val model: String,
    val promptVersion: Int,
    val note: String,
    val imageCount: Int,
    /** Model's raw text output (JSON), or null on failure. */
    val rawText: String?,
    val parsedKcal: Int?,
    val error: String?,
    val latencyMs: Long,
)

/** Every completed scale weigh-in, including ones not used as the day's weight. */
@Entity(tableName = "scale_readings", indices = [Index("day")])
data class ScaleReading(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val day: String,
    val at: Long,
    val massKg: Double,
    val impedance50: Double?,
    val impedance250: Double?,
    val heartRate: Int?,
    val profileId: Int,
    /** "used" (became the day's weight) | "later_same_day" | "implausible" | "other_profile" */
    val disposition: String,
)

/** End-of-day snapshot of everything the app computed, so model changes don't rewrite history. */
@Entity(tableName = "daily_snapshots")
data class DailySnapshot(
    @PrimaryKey val day: String,
    val intakeKcal: Int,
    /** Intake after applying the photo calibration factor (equal to intake when uncalibrated). */
    val intakeCalibratedKcal: Int,
    val mealCount: Int,
    val proteinG: Double,
    val weightKg: Double?,
    val tdeePrior: Int,
    val tdeeAdaptive: Int?,
    val activityAdjustment: Int,
    val burnedKcal: Int,
    val targetIntake: Int,
    val photoCalibrationFactor: Double,
    val updatedAt: Long,
)

@Dao
interface RawDao {
    @Insert suspend fun insertAiCall(c: AiCall): Long
    @Insert suspend fun insertReading(r: ScaleReading): Long
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertSnapshot(s: DailySnapshot)

    @Query("SELECT * FROM ai_calls ORDER BY at") suspend fun allAiCalls(): List<AiCall>
    @Query("SELECT * FROM scale_readings ORDER BY at") suspend fun allReadings(): List<ScaleReading>
    @Query("SELECT * FROM daily_snapshots ORDER BY day") suspend fun allSnapshots(): List<DailySnapshot>
}
