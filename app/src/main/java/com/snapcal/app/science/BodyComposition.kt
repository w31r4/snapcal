package com.snapcal.app.science

import kotlin.math.roundToInt

/**
 * Body composition from foot-to-foot bioimpedance (Xiaomi S400: 4 foot electrodes, |Z| at 50 kHz).
 *
 * FFM uses Wu et al. 2015 (Nutr J 14:52), the only verified foot-to-foot equation derived in Chinese adults
 * (n = 554 Taiwanese, DXA reference, R² = 0.92, SEE = 3.17 kg):
 *
 *     FFM(kg) = 13.055 + 0.204·W + 0.394·H²/Z − 0.136·age + 8.125·(male = 1)
 *
 * Deliberately NOT computed: visceral fat, bone mass, protein %, body age, segmental values, ECW/ICW.
 * Foot-to-foot current doesn't traverse the upper trunk or arms, and vendor values for these are
 * anthropometric regressions, not measurements. See docs/SCIENCE.md §6.
 */
object BodyComposition {
    /** Wu 2015 standard error of estimate for FFM. */
    const val FFM_SEE_KG = 3.17
    /** Valid input range of the derivation sample (Z range for women was ~326–733 Ω). */
    val VALID_IMPEDANCE = 250.0..900.0
    val VALID_BMI = 16.0..43.0
    val VALID_AGE = 16..75

    data class Estimate(
        val fatFreeMassKg: Double,
        val fatMassKg: Double,
        val fatPercent: Double,
        /** ± fat-% points corresponding to 1 SEE. */
        val fatPercentSe: Double,
        /** Derived as 0.73 × FFM (normal hydration assumed); carries no independent information. */
        val bodyWaterKg: Double,
        val outsideValidatedRange: Boolean,
    )

    fun wu2015(sex: Sex, weightKg: Double, heightCm: Double, age: Int, impedance50kHz: Double): Estimate? {
        if (impedance50kHz <= 0 || weightKg <= 0) return null
        val ffm = 13.055 + 0.204 * weightKg + 0.394 * heightCm * heightCm / impedance50kHz -
            0.136 * age + 8.125 * (if (sex == Sex.MALE) 1 else 0)
        val clamped = ffm.coerceIn(weightKg * 0.4, weightKg * 0.97)
        val fat = weightKg - clamped
        val bmi = Energy.bmi(weightKg, heightCm)
        return Estimate(
            fatFreeMassKg = clamped,
            fatMassKg = fat,
            fatPercent = fat / weightKg * 100,
            fatPercentSe = FFM_SEE_KG / weightKg * 100,
            bodyWaterKg = 0.73 * clamped,
            outsideValidatedRange = impedance50kHz !in VALID_IMPEDANCE || bmi !in VALID_BMI || age !in VALID_AGE,
        )
    }

    fun fmt(e: Estimate): String = "${e.fatPercent.roundToInt()}% ±${e.fatPercentSe.roundToInt()}"
}
