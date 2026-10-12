package com.snapcal.app.health

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.BasalMetabolicRateRecord
import androidx.health.connect.client.records.NutritionRecord
import androidx.health.connect.client.records.TotalCaloriesBurnedRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import com.snapcal.app.science.WearableDay
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.time.TimeRangeFilter
import androidx.health.connect.client.units.Energy
import androidx.health.connect.client.units.Mass
import com.snapcal.app.data.Meal
import com.snapcal.app.data.carbsG
import com.snapcal.app.data.fatG
import com.snapcal.app.data.proteinG
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Health Connect integration:
 *  - writes each meal as a NutritionRecord (so other apps see intake)
 *  - reads activity energy written by sport apps / wearables
 *  - reads and writes body weight
 *
 * Pitfalls handled (Android docs + AOSP HealthFitness source):
 *  - the TotalCaloriesBurned aggregate fills empty intervals with a *derived basal* value, so a day with no
 *    wearable at all still returns a plausible total — we use total − basal and check data origins;
 *  - raw records are not de-duplicated across apps; the aggregate API is (by data-origin priority);
 *  - Samsung Health only syncs workout sessions, not all-day activity.
 */
class HealthBridge(private val context: Context) {

    val permissions: Set<String> = setOf(
        HealthPermission.getReadPermission(TotalCaloriesBurnedRecord::class),
        HealthPermission.getReadPermission(BasalMetabolicRateRecord::class),
        HealthPermission.getReadPermission(NutritionRecord::class),
        HealthPermission.getWritePermission(NutritionRecord::class),
        HealthPermission.getReadPermission(WeightRecord::class),
        HealthPermission.getWritePermission(WeightRecord::class),
    )

    fun sdkStatus(): Int = HealthConnectClient.getSdkStatus(context)

    val isAvailable: Boolean get() = sdkStatus() == HealthConnectClient.SDK_AVAILABLE

    private val client: HealthConnectClient? by lazy {
        if (isAvailable) HealthConnectClient.getOrCreate(context) else null
    }

    fun permissionContract() = PermissionController.createRequestPermissionResultContract()

    suspend fun grantedPermissions(): Set<String> =
        client?.permissionController?.getGrantedPermissions() ?: emptySet()

    suspend fun hasAllPermissions(): Boolean = grantedPermissions().containsAll(permissions)

    /**
     * Activity energy for each day in [from, to] (inclusive). For today only the elapsed part is queried,
     * never the future, because the derived basal fill would otherwise credit hours that haven't happened.
     */
    suspend fun readActivity(from: LocalDate, to: LocalDate, zone: ZoneId = ZoneId.systemDefault()): Map<LocalDate, WearableDay> {
        val c = client ?: return emptyMap()
        val granted = grantedPermissions()
        if (HealthPermission.getReadPermission(TotalCaloriesBurnedRecord::class) !in granted) return emptyMap()
        val out = HashMap<LocalDate, WearableDay>()
        var d = from
        val now = Instant.now()
        while (!d.isAfter(to)) {
            val start = d.atStartOfDay(zone).toInstant()
            val end = minOf(d.plusDays(1).atStartOfDay(zone).toInstant(), now)
            if (end.isAfter(start)) {
                val r = c.aggregate(
                    AggregateRequest(
                        setOf(TotalCaloriesBurnedRecord.ENERGY_TOTAL, BasalMetabolicRateRecord.BASAL_CALORIES_TOTAL),
                        TimeRangeFilter.between(start, end),
                    ),
                )
                val total = r[TotalCaloriesBurnedRecord.ENERGY_TOTAL]?.inKilocalories
                val basal = r[BasalMetabolicRateRecord.BASAL_CALORIES_TOTAL]?.inKilocalories ?: 0.0
                val origins = r.dataOrigins.map { it.packageName }.filter { it != context.packageName }
                if (total != null) {
                    val active = (total - basal).coerceIn(0.0, MAX_PLAUSIBLE_ACTIVE)
                    out[d] = WearableDay(
                        activeKcal = active,
                        hasRealData = origins.isNotEmpty() && active > 0,
                        workoutsOnly = origins.isNotEmpty() && origins.all { it in WORKOUT_ONLY_SOURCES },
                    )
                }
            }
            d = d.plusDays(1)
        }
        return out
    }

    suspend fun writeWeight(kg: Double, at: Instant) {
        val c = client ?: return
        if (HealthPermission.getWritePermission(WeightRecord::class) !in grantedPermissions()) return
        val record = WeightRecord(
            time = at,
            zoneOffset = ZoneId.systemDefault().rules.getOffset(at),
            weight = Mass.kilograms(kg),
            metadata = Metadata.manualEntry(),
        )
        c.insertRecords(listOf(record))
    }

    /** Most recent weight per day from other apps (smart scales), for importing. */
    suspend fun readWeights(from: Instant, to: Instant): Map<LocalDate, Pair<Double, Instant>> {
        val c = client ?: return emptyMap()
        if (HealthPermission.getReadPermission(WeightRecord::class) !in grantedPermissions()) return emptyMap()
        val records = c.readRecords(ReadRecordsRequest(WeightRecord::class, TimeRangeFilter.between(from, to))).records
        return records
            .filter { it.metadata.dataOrigin.packageName != context.packageName }
            .groupBy { it.time.atZone(ZoneId.systemDefault()).toLocalDate() }
            // Prefer the earliest weigh-in of the day (closest to a fasted morning weight).
            .mapValues { (_, rs) -> rs.minBy { it.time }.let { it.weight.inKilograms to it.time } }
    }

    /** Upsert by clientRecordId, so editing a meal replaces its record instead of duplicating it. */
    suspend fun writeMeal(meal: Meal) {
        val c = client ?: return
        if (HealthPermission.getWritePermission(NutritionRecord::class) !in grantedPermissions()) return
        val start = Instant.ofEpochMilli(meal.eatenAt)
        val offset = ZoneId.systemDefault().rules.getOffset(start)
        val record = NutritionRecord(
            startTime = start,
            startZoneOffset = offset,
            endTime = start.plusSeconds(60),
            endZoneOffset = offset,
            name = meal.title.take(100),
            energy = Energy.kilocalories(meal.totalKcal.toDouble()),
            protein = meal.proteinG.takeIf { it > 0 }?.let { Mass.grams(it) },
            totalCarbohydrate = meal.carbsG.takeIf { it > 0 }?.let { Mass.grams(it) },
            totalFat = meal.fatG.takeIf { it > 0 }?.let { Mass.grams(it) },
            metadata = Metadata.manualEntry(
                clientRecordId = clientId(meal.id),
                clientRecordVersion = meal.version,
            ),
        )
        c.insertRecords(listOf(record))
    }

    suspend fun deleteMeal(mealId: Long) {
        val c = client ?: return
        if (HealthPermission.getWritePermission(NutritionRecord::class) !in grantedPermissions()) return
        c.deleteRecords(NutritionRecord::class, recordIdsList = emptyList(), clientRecordIdsList = listOf(clientId(mealId)))
    }

    private fun clientId(mealId: Long) = "snapcal-meal-$mealId"

    companion object {
        private const val MAX_PLAUSIBLE_ACTIVE = 2500.0
        private val WORKOUT_ONLY_SOURCES = setOf("com.sec.android.app.shealth")
    }

}
