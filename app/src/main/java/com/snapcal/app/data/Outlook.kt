package com.snapcal.app.data

/**
 * "饮食效果" summary for the 秤一下 card: what the scale says vs. what the logged diet predicts.
 *
 * - actualKgPerWeek: robust (Theil–Sen) trend of daily weights over the last 28 days.
 * - expectedKgPerWeek: what the dynamic model predicts from the average logged deficit over the same window
 *   (initial rate, ρ kcal/kg). A persistent gap between the two means intake is under-logged or the
 *   maintenance estimate is off — which is exactly what the adaptive TDEE then corrects.
 * - projected12wKg: weight in 12 weeks if the current plan is followed (dynamic model, not 7700 kcal/kg).
 */
data class Outlook(
    val latestKg: Double?,
    val trendKg: Double?,
    val actualKgPerWeek: Double?,
    val expectedKgPerWeek: Double?,
    val avgLoggedDeficit: Int?,
    val loggedDays: Int,
    val weighIns: Int,
    val projected12wKg: Double?,
)
