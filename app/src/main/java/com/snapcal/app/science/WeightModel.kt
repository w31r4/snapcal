package com.snapcal.app.science

import kotlin.math.exp
import kotlin.math.ln

/**
 * Linearised NIH dynamic energy-balance model (Hall 2011 Lancet; Guo, Brager & Hall 2018 AJCN):
 *
 *     ρ · dBW/dt = ΔEI − ε · ΔBW     ⇒     ΔBW(t) = (ΔEI / ε) · (1 − e^(−t·ε/ρ))
 *
 * ΔEI is the sustained change in intake relative to the starting maintenance intake (negative for a deficit).
 * Unlike the static "7700 kcal = 1 kg" rule, weight loss slows as expenditure falls with body mass,
 * reaching half of the eventual change in about a year (time constant ρ/ε ≈ 340–410 days).
 */
object WeightModel {
    /** kcal per kg of weight change (Guo 2018 fit to the NIH model). */
    fun rho(sex: Sex): Double = if (sex == Sex.MALE) 9383.0 else 9916.0

    /** kcal/day by which expenditure falls per kg lost (≈ "24 kcal/day per kg", Hall 2011). */
    fun epsilon(sex: Sex): Double = if (sex == Sex.MALE) 28.0 else 24.0

    /** Predicted weight change (kg, negative = loss) after [days] at a sustained intake change of [deltaIntake] kcal/day. */
    fun deltaWeight(sex: Sex, deltaIntake: Double, days: Double): Double {
        val e = epsilon(sex)
        return (deltaIntake / e) * (1 - exp(-days * e / rho(sex)))
    }

    /** Eventual (steady-state) weight change for a sustained intake change. */
    fun plateauDelta(sex: Sex, deltaIntake: Double): Double = deltaIntake / epsilon(sex)

    /**
     * Days to lose [kgToLose] (> 0) at a sustained deficit [deficit] (> 0), or null if the steady-state loss
     * at this deficit never reaches the goal.
     */
    fun daysToLose(sex: Sex, deficit: Double, kgToLose: Double): Double? {
        if (kgToLose <= 0 || deficit <= 0) return null
        val e = epsilon(sex)
        val fraction = kgToLose * e / deficit
        if (fraction >= 1) return null
        return -(rho(sex) / e) * ln(1 - fraction)
    }

    /**
     * Energy content of weight change given current fat mass, from Forbes' law (C = 10.4 kg) and tissue energy
     * densities 9440 kcal/kg fat, 1816 kcal/kg lean (Hall 2008 IJO; Hall 2011 appendix). ~7500 at 30 kg fat.
     */
    fun energyPerKg(fatMassKg: Double): Double {
        val f = fatMassKg / (fatMassKg + 10.4)
        return f * 9440 + (1 - f) * 1816
    }
}
