package com.snapcal.app.science

import kotlin.math.roundToInt

/**
 * Wearable activity energy for one day, read from Health Connect.
 * [activeKcal] = total − basal over the elapsed part of the day (never add active to total).
 */
data class WearableDay(
    val activeKcal: Double,
    /** True when Health Connect had real records (not just its synthesized basal fill). */
    val hasRealData: Boolean,
    /** Samsung Health writes only workout sessions, so a low value is not evidence of a sedentary day. */
    val workoutsOnly: Boolean,
)

enum class BurnSource(val label: String) {
    PRIOR("公式估算（BMR × 活动系数）"),
    ADAPTIVE("根据你的记录和体重趋势校准"),
}

data class DailyBurn(
    val baselineKcal: Int,
    val baselineSource: BurnSource,
    /** Credited extra activity relative to the user's usual day (can be negative). */
    val activityAdjustmentKcal: Int,
    val totalKcal: Int,
)

/**
 * The deficit budget uses a calibrated baseline TDEE, adjusted by how much today's wearable activity
 * deviates from the user's own recent average. Raw wearable totals are never used as TDEE:
 *  - wearable EE errors are commonly ≥ 20 % and brand-dependent (Fuller 2020; O'Driscoll 2020; Shcherbina 2017);
 *  - ~28 % of extra activity energy is compensated by lower basal expenditure, more with higher adiposity
 *    (Careau 2021), and in diet + exercise interventions TDEE rose only ~30 % of the additive prediction
 *    (Pontzer & Trexler 2026).
 * Hence only half of above-usual activity is credited, capped; below-usual days are applied in full.
 */
object DailyBurnModel {
    const val CREDIT_FRACTION = 0.5
    const val MAX_POSITIVE_ADJUSTMENT = 500.0
    /** Floor for negative adjustments so a missing or partial sync can't collapse the budget. */
    const val MAX_NEGATIVE_ADJUSTMENT = -300.0
    /** Need this many complete days to know what "usual" looks like. */
    const val MIN_HISTORY_DAYS = 7

    fun compute(
        baselineTdee: Int,
        baselineSource: BurnSource,
        /** For an in-progress day, pass it only to credit extra activity; negatives apply to complete days. */
        today: WearableDay?,
        todayComplete: Boolean = true,
        /** Active kcal of previous complete days (same data source), most recent 14–28 days. */
        history: List<Double>,
    ): DailyBurn {
        val adj = when {
            today == null || !today.hasRealData -> 0.0
            today.workoutsOnly -> {
                // Workouts-only sources: usual = average workout kcal; credit half of anything above it.
                val usual = if (history.size >= MIN_HISTORY_DAYS) history.average() else 0.0
                ((today.activeKcal - usual).coerceAtLeast(0.0) * CREDIT_FRACTION).coerceAtMost(MAX_POSITIVE_ADJUSTMENT)
            }
            history.size < MIN_HISTORY_DAYS -> 0.0
            !todayComplete && today.activeKcal < history.average() -> 0.0
            else -> {
                val diff = today.activeKcal - history.average()
                if (diff > 0) (diff * CREDIT_FRACTION).coerceAtMost(MAX_POSITIVE_ADJUSTMENT)
                // Callers pass only complete days here for negative adjustments (see [compute] docs).
                else diff.coerceAtLeast(MAX_NEGATIVE_ADJUSTMENT)
            }
        }
        val a = adj.roundToInt()
        return DailyBurn(baselineTdee, baselineSource, a, baselineTdee + a)
    }
}
