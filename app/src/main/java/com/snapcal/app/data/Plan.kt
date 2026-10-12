package com.snapcal.app.data

import com.snapcal.app.science.Energy
import com.snapcal.app.science.WeightModel
import kotlin.math.roundToInt

/**
 * Deficit options. Guidelines recommend a 500–750 kcal/day deficit (AHA/ACC/TOS 2013) or 500–1000
 * (中国超重/肥胖医学营养治疗指南 2021), capped at 30 % of intake (肥胖症诊疗指南 2024).
 */
enum class Pace(val dailyDeficit: Int, val label: String) {
    GENTLE(300, "轻松"),
    STANDARD(500, "标准（推荐）"),
    AGGRESSIVE(750, "积极"),
    ;

    companion object {
        fun nearest(deficit: Int) = entries.minBy { kotlin.math.abs(it.dailyDeficit - deficit) }
    }
}

/** Everything the plan screen shows, derived from a [Profile]. */
data class Plan(
    val bmr: Int,
    /** Equation-based prior: BMR × PAL. */
    val tdeePrior: Int,
    /** ±1 SD of [tdeePrior]. */
    val tdeeSd: Int,
    /** Maintenance actually used: adaptive estimate when available, else the prior. */
    val tdee: Int,
    val tdeeIsAdaptive: Boolean,
    val deficit: Int,
    val dailyIntakeTarget: Int,
    val intakeFloor: Int,
    val cappedBySafety: Boolean,
    /** Initial rate of loss; it slows over time as expenditure falls (see [WeightModel]). */
    val initialKgPerWeek: Double,
    val kgToLose: Double,
    /** null when no goal, or the goal is beyond the steady state of this deficit. */
    val weeksToGoal: Int?,
    /** Weight change after 12 weeks at this deficit (dynamic model). */
    val kgIn12Weeks: Double,
    val proteinG: IntRange,
) {
    companion object {
        fun of(p: Profile, adaptiveTdee: Int? = null): Plan {
            val bmr = p.bmr
            val prior = (bmr * p.pal).roundToInt()
            val tdee = adaptiveTdee ?: prior
            val floor = Energy.intakeFloor(p.sex)
            val wanted = p.targetDeficit.coerceAtLeast(0)
            val maxDeficit = minOf((tdee - floor).coerceAtLeast(0), (tdee * Energy.MAX_DEFICIT_FRACTION).toInt())
            val deficit = minOf(wanted, maxDeficit)
            val toLose = if (p.goalWeightKg > 0) (p.weightKg - p.goalWeightKg).coerceAtLeast(0.0) else 0.0
            val days = WeightModel.daysToLose(p.sex, deficit.toDouble(), toLose)
            return Plan(
                bmr = bmr,
                tdeePrior = prior,
                tdeeSd = Energy.tdeeSd(p.sex),
                tdee = tdee,
                tdeeIsAdaptive = adaptiveTdee != null,
                deficit = deficit,
                dailyIntakeTarget = tdee - deficit,
                intakeFloor = floor,
                cappedBySafety = deficit < wanted,
                initialKgPerWeek = deficit * 7 / WeightModel.rho(p.sex),
                kgToLose = toLose,
                weeksToGoal = days?.let { kotlin.math.ceil(it / 7).toInt() },
                kgIn12Weeks = -WeightModel.deltaWeight(p.sex, -deficit.toDouble(), 84.0),
                proteinG = Energy.proteinTargetG(p.weightKg, p.heightCm.toDouble()),
            )
        }
    }
}
