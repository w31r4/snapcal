package com.snapcal.app.science

import java.time.LocalDate
import kotlin.math.roundToInt

/**
 * Back-calculate the user's real maintenance intake from logged intake and the body-weight trend
 * (energy-balance method: Hall & Chow 2011 AJCN; validated vs DLW by Sanghvi 2015 AJCN, RMSD ≈ 215 kcal/d).
 *
 *     TDEE ≈ mean logged intake − ρ_eff · (dW/dt)
 *
 * Because intake is the user's own (AI-estimated, possibly biased) log, the result is TDEE "in the units of
 * their logging": if photos are systematically 20 % low, the target is too, and the plan still works.
 * It must therefore never be presented as a physiological measurement.
 */
object AdaptiveTdee {
    /** Hall & Chow 2011: ≥ 28 days of data for a 95 % CI under ~300 kcal/day. */
    const val WINDOW_DAYS = 28
    const val MIN_SPAN_DAYS = 14
    const val MIN_WEIGHINS = 10
    const val MIN_LOGGED_DAYS = 14
    /** The first ~2 weeks of a diet are dominated by glycogen and water loss (Heymsfield 2011 Obes Rev). */
    const val SKIP_INITIAL_DAYS = 14
    /** Keep the empirical value within a plausible band around the equation-based prior. */
    const val MAX_DEVIATION = 800

    data class Result(
        val tdee: Int,
        /** Prior-vs-empirical blend weight in [0,1]; 1 = fully empirical. */
        val confidence: Double,
        val trendKgPerWeek: Double,
        val loggedDays: Int,
        val weighIns: Int,
    )

    /**
     * @param intakeByDay total logged kcal per day; only days with at least one meal should be present.
     * @param weights weigh-ins (one per day; use the morning value if several).
     * @param trackingStart first day the user started logging; the initial water-loss phase after it is skipped.
     */
    fun estimate(
        today: LocalDate,
        trackingStart: LocalDate,
        intakeByDay: Map<LocalDate, Int>,
        weights: Map<LocalDate, Double>,
        priorTdee: Int,
        fatMassKg: Double,
    ): Result? {
        val windowStart = maxOf(today.minusDays(WINDOW_DAYS.toLong() - 1), trackingStart.plusDays(SKIP_INITIAL_DAYS.toLong()))
        // Today is usually not fully logged yet.
        val windowEnd = today.minusDays(1)
        if (windowStart.isAfter(windowEnd)) return null

        val w = weights.filterKeys { !it.isBefore(windowStart) && !it.isAfter(windowEnd) }
        val intake = intakeByDay.filterKeys { !it.isBefore(windowStart) && !it.isAfter(windowEnd) }.filterValues { it > 0 }
        if (w.size < MIN_WEIGHINS || intake.size < MIN_LOGGED_DAYS) return null
        val span = w.keys.max().toEpochDay() - w.keys.min().toEpochDay()
        if (span < MIN_SPAN_DAYS) return null

        val slopePerDay = Trend.theilSenSlope(w.map { (d, kg) -> d.toEpochDay().toDouble() to kg })
        val meanIntake = intake.values.average()
        val empirical = meanIntake - WeightModel.energyPerKg(fatMassKg) * slopePerDay
        val clamped = empirical.coerceIn((priorTdee - MAX_DEVIATION).toDouble(), (priorTdee + MAX_DEVIATION).toDouble())

        val confidence = (minOf(w.size, intake.size).toDouble() / WINDOW_DAYS).coerceAtMost(1.0)
        val blended = confidence * clamped + (1 - confidence) * priorTdee
        return Result(
            tdee = blended.roundToInt(),
            confidence = confidence,
            trendKgPerWeek = slopePerDay * 7,
            loggedDays = intake.size,
            weighIns = w.size,
        )
    }
}

object Trend {
    /** Theil–Sen estimator: median of pairwise slopes; robust to single-day water/salt spikes. */
    fun theilSenSlope(points: List<Pair<Double, Double>>): Double {
        val slopes = ArrayList<Double>()
        for (i in points.indices) for (j in i + 1 until points.size) {
            val dx = points[j].first - points[i].first
            if (dx != 0.0) slopes += (points[j].second - points[i].second) / dx
        }
        if (slopes.isEmpty()) return 0.0
        slopes.sort()
        val m = slopes.size / 2
        return if (slopes.size % 2 == 1) slopes[m] else (slopes[m - 1] + slopes[m]) / 2
    }

    /** Display-only smoothed weight line (exponential moving average, α = 0.1 as popularised by The Hacker's Diet). */
    fun ema(values: List<Double>, alpha: Double = 0.1): List<Double> {
        if (values.isEmpty()) return emptyList()
        val out = ArrayList<Double>(values.size)
        var s = values.first()
        for (v in values) {
            s += alpha * (v - s)
            out += s
        }
        return out
    }
}
