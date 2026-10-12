package com.snapcal.app.science

import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.sqrt

/**
 * Calibration of AI photo estimates against meals whose true energy the user confirmed (nutrition label or
 * weighed food).
 *
 * Why not calibrate from body weight: the weight trend only identifies (true intake − true expenditure).
 * A photo bias and an expenditure error are indistinguishable there, and the adaptive TDEE already absorbs
 * both (docs/SCIENCE.md §2.4). Scaling meals from the weight trend as well would correct the same error
 * twice. Only meals with known ground truth can separate photo bias from metabolism.
 *
 * Method: log-ratio median (robust to a few wild estimates), shrunk toward 1 with few samples,
 * clamped to [0.7, 1.6] — published LLM biases are mostly under-estimation of 10–30 % (Fridolfsson 2025).
 */
object PhotoCalibration {
    const val MIN_SAMPLES = 10
    /** Pseudo-count pulling the factor toward 1.0 while data are scarce. */
    private const val PRIOR_WEIGHT = 5.0
    val RANGE = 0.7..1.6

    data class Result(
        /** Multiply AI estimates by this. 1.0 = no correction. */
        val factor: Double,
        val samples: Int,
        /** Median absolute percentage error of the raw AI estimates on verified meals. */
        val medianAbsErrorPct: Double,
        /** Approximate 95 % range of the factor. */
        val lower: Double,
        val upper: Double,
        val active: Boolean,
    )

    /** @param pairs (aiKcal, trueKcal) for verified meals */
    fun estimate(pairs: List<Pair<Int, Int>>): Result {
        val logs = pairs.filter { it.first > 0 && it.second > 0 }.map { ln(it.second.toDouble() / it.first) }
        val n = logs.size
        if (n == 0) return Result(1.0, 0, 0.0, 1.0, 1.0, false)
        val sorted = logs.sorted()
        val median = if (n % 2 == 1) sorted[n / 2] else (sorted[n / 2 - 1] + sorted[n / 2]) / 2
        val shrunk = median * n / (n + PRIOR_WEIGHT)
        val mean = logs.average()
        val sd = if (n > 1) sqrt(logs.sumOf { (it - mean) * (it - mean) } / (n - 1)) else 0.0
        // Standard error of the median ≈ 1.253 · SD/√n.
        val se = 1.253 * sd / sqrt(n.toDouble())
        val absErr = pairs.filter { it.first > 0 && it.second > 0 }
            .map { kotlin.math.abs(it.first - it.second).toDouble() / it.second * 100 }
            .sorted()
        return Result(
            factor = exp(shrunk).coerceIn(RANGE.start, RANGE.endInclusive),
            samples = n,
            medianAbsErrorPct = absErr[absErr.size / 2],
            lower = exp(shrunk - 1.96 * se).coerceIn(RANGE.start, RANGE.endInclusive),
            upper = exp(shrunk + 1.96 * se).coerceIn(RANGE.start, RANGE.endInclusive),
            active = n >= MIN_SAMPLES,
        )
    }
}
