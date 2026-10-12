package com.snapcal.app.data

import com.snapcal.app.science.DailyBurn

/** What the home card shows for one day. */
data class EnergyBalance(
    /** Calibrated intake (raw × photo factor for unverified meals); what the deficit is computed from. */
    val intakeKcal: Int,
    val burn: DailyBurn,
    val plan: Plan,
    /** Sum of meal totals as logged, before photo calibration. */
    val rawIntakeKcal: Int = intakeKcal,
    val photoFactor: Double = 1.0,
) {
    val burnedKcal: Int get() = burn.totalKcal
    /** Positive = deficit (burned more than eaten). */
    val deficit: Int get() = burnedKcal - intakeKcal
    /** Today's budget = plan target + credited extra activity. */
    val budgetKcal: Int get() = plan.dailyIntakeTarget + burn.activityAdjustmentKcal
    val remaining: Int get() = budgetKcal - intakeKcal
}
