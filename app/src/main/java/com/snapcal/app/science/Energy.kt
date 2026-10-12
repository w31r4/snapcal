package com.snapcal.app.science

import kotlin.math.roundToInt

/**
 * Energy-expenditure estimation. Every constant here is sourced; see docs/SCIENCE.md for references.
 */
enum class Sex { MALE, FEMALE }

/**
 * Physical activity level (PAL = TDEE / BMR). Bands follow FAO/WHO/UNU 2004 and the Chinese DRIs (1.40 / 1.70 / 2.00).
 * Descriptions are concrete behaviours rather than adjectives because people over-report activity by ~50%
 * (Lichtman 1992 NEJM), and DLW-measured PAL in young Chinese adults averaged only 1.29 (Nie 2026).
 */
enum class ActivityLevel(val pal: Double, val label: String, val hint: String) {
    SEDENTARY(1.40, "久坐（推荐默认）", "坐着工作，通勤以外很少走动，日常 < 6000 步"),
    LIGHT(1.55, "轻度活跃", "日常 7000–10000 步，或每周 2–3 次 30 分钟以上运动"),
    MODERATE(1.70, "中度活跃", "每天 > 10000 步，或每周 4–5 次正经训练"),
    HIGH(2.00, "高度活跃", "体力劳动，或几乎每天高强度训练"),
    ;

    companion object {
        fun nearest(pal: Double) = entries.minBy { kotlin.math.abs(it.pal - pal) }
    }
}

object Energy {
    /** Mifflin-St Jeor 1990 — best-validated RMR equation in obesity (Frankenfield 2005; Madden 2016) and in Chinese samples (Camps 2016; Wu 2025). */
    fun mifflinStJeor(sex: Sex, weightKg: Double, heightCm: Double, age: Int): Double =
        10 * weightKg + 6.25 * heightCm - 5 * age + if (sex == Sex.MALE) 5 else -161

    /** Cunningham 1991 (FFM-based), offered when the user knows their body-fat %. */
    fun cunningham(fatFreeMassKg: Double): Double = 370 + 21.6 * fatFreeMassKg

    /**
     * Deurenberg 1991 (Br J Nutr) body-fat % from BMI, ±~4 percentage points.
     * Used only to estimate the fat/lean split of weight change, never shown as a measurement.
     */
    fun bodyFatPercentFromBmi(sex: Sex, bmi: Double, age: Int): Double =
        (1.2 * bmi + 0.23 * age - 10.8 * (if (sex == Sex.MALE) 1 else 0) - 5.4).coerceIn(5.0, 60.0)

    /** ~1 SD of individual TDEE prediction error (NASEM 2023 DLW-based equations: SEE ≈ 342 kcal men / 241 kcal women). */
    fun tdeeSd(sex: Sex): Int = if (sex == Sex.MALE) 350 else 250

    /** Minimum intake without clinical supervision (NHLBI 1998; AHA/ACC/TOS 2013; 中国超重/肥胖医学营养治疗指南 2021). */
    fun intakeFloor(sex: Sex): Int = if (sex == Sex.MALE) 1500 else 1200

    /** Restricted-energy diet: at most −30% of usual intake (国家卫健委《肥胖症诊疗指南》2024). */
    const val MAX_DEFICIT_FRACTION = 0.30

    /**
     * Protein during a deficit: 1.2–1.6 g/kg helps preserve lean mass (Wycherley 2012 meta-analysis; ISSN 2017).
     * For BMI above 24 (Chinese overweight cut-off) use the weight at BMI 24 as the reference to avoid inflated targets.
     */
    fun proteinTargetG(weightKg: Double, heightCm: Double): IntRange {
        val h = heightCm / 100
        val ref = minOf(weightKg, 24 * h * h)
        return (1.2 * ref).roundToInt()..(1.6 * ref).roundToInt()
    }

    fun bmi(weightKg: Double, heightCm: Double): Double = weightKg / ((heightCm / 100) * (heightCm / 100))
}
