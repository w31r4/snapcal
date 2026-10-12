package com.snapcal.app.science

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class WeightModelTest {
    @Test fun steadyStateIs24kcalPerKgForWomen() {
        assertEquals(-500.0 / 24, WeightModel.plateauDelta(Sex.FEMALE, -500.0), 1e-9)
    }

    @Test fun halfOfEventualChangeTakesAboutAYear() {
        val half = WeightModel.daysToLose(Sex.MALE, 500.0, 500.0 / 28 / 2)!!
        assertTrue(half in 200.0..300.0)
    }

    @Test fun deltaWeightAndDaysToLoseAreInverse() {
        val d = WeightModel.deltaWeight(Sex.MALE, -600.0, 100.0)
        assertEquals(100.0, WeightModel.daysToLose(Sex.MALE, 600.0, -d)!!, 1e-6)
    }

    @Test fun energyPerKgDependsOnFatMass() {
        val lean = WeightModel.energyPerKg(15.0)
        val obese = WeightModel.energyPerKg(30.0)
        assertTrue(lean in 6000.0..6600.0)
        assertTrue(obese in 7200.0..7800.0)
    }
}

class EnergyTest {
    @Test fun deurenbergBodyFat() {
        // man, BMI 26, age 30: 1.2*26 + 0.23*30 - 10.8 - 5.4 = 21.9
        assertEquals(21.9, Energy.bodyFatPercentFromBmi(Sex.MALE, 26.0, 30), 1e-9)
    }
}

class AdaptiveTdeeTest {
    private val start = LocalDate.of(2026, 1, 1)
    private val today = start.plusDays(60)

    /** Simulate a user whose true maintenance is [trueTdee] but who logs only [logRatio] of what they eat. */
    private fun simulate(trueTdee: Int, eaten: Int, logRatio: Double, noise: Double = 0.0): AdaptiveTdee.Result? {
        val rho = WeightModel.energyPerKg(25.0)
        var w = 85.0
        val weights = HashMap<LocalDate, Double>()
        val intake = HashMap<LocalDate, Int>()
        var d = start
        var i = 0
        while (d.isBefore(today)) {
            w += (eaten - trueTdee) / rho
            val wobble = if (noise > 0) noise * kotlin.math.sin(i * 1.7) else 0.0
            weights[d] = w + wobble
            intake[d] = (eaten * logRatio).toInt()
            d = d.plusDays(1); i++
        }
        return AdaptiveTdee.estimate(today, start, intake, weights, priorTdee = 2400, fatMassKg = 25.0)
    }

    @Test fun recoversTrueTdeeFromAccurateLogs() {
        val r = simulate(trueTdee = 2600, eaten = 2000, logRatio = 1.0)!!
        assertEquals(2600.0, r.tdee.toDouble(), 30.0)
        assertEquals(1.0, r.confidence, 0.05)
    }

    @Test fun absorbsSystematicUnderLogging() {
        // Logs 80 % of intake → calibrated TDEE is in "logged units" (~0.8*2000 + 600 = 2200).
        val r = simulate(trueTdee = 2600, eaten = 2000, logRatio = 0.8)!!
        assertEquals(2200.0, r.tdee.toDouble(), 30.0)
    }

    @Test fun robustToDailyWaterNoise() {
        val r = simulate(trueTdee = 2600, eaten = 2000, logRatio = 1.0, noise = 1.0)!!
        assertEquals(2600.0, r.tdee.toDouble(), 150.0)
    }

    @Test fun needsEnoughData() {
        val few = AdaptiveTdee.estimate(
            today, start,
            intakeByDay = mapOf(today.minusDays(1) to 2000),
            weights = mapOf(today.minusDays(1) to 80.0),
            priorTdee = 2400, fatMassKg = 25.0,
        )
        assertNull(few)
    }

    @Test fun skipsInitialWaterLossWeeks() {
        // Only 20 days since start: after skipping 14, too little data remains.
        val r = AdaptiveTdee.estimate(
            start.plusDays(20), start,
            intakeByDay = (0L..19L).associate { start.plusDays(it) to 2000 },
            weights = (0L..19L).associate { start.plusDays(it) to 85.0 - it * 0.1 },
            priorTdee = 2400, fatMassKg = 25.0,
        )
        assertNull(r)
    }
}

class DailyBurnTest {
    private val history = List(14) { 400.0 }

    @Test fun creditsHalfOfExtraActivity() {
        val b = DailyBurnModel.compute(2400, BurnSource.PRIOR, WearableDay(800.0, true, false), true, history)
        assertEquals(200, b.activityAdjustmentKcal)
        assertEquals(2600, b.totalKcal)
    }

    @Test fun capsPositiveAdjustment() {
        val b = DailyBurnModel.compute(2400, BurnSource.PRIOR, WearableDay(3000.0, true, false), true, history)
        assertEquals(500, b.activityAdjustmentKcal)
    }

    @Test fun lowerActivityOnlyAfterDayIsComplete() {
        val partial = DailyBurnModel.compute(2400, BurnSource.PRIOR, WearableDay(100.0, true, false), false, history)
        assertEquals(0, partial.activityAdjustmentKcal)
        val complete = DailyBurnModel.compute(2400, BurnSource.PRIOR, WearableDay(200.0, true, false), true, history)
        assertEquals(-200, complete.activityAdjustmentKcal)
    }

    @Test fun ignoresSynthesizedBasalOnlyDays() {
        val b = DailyBurnModel.compute(2400, BurnSource.PRIOR, WearableDay(0.0, false, false), true, history)
        assertEquals(0, b.activityAdjustmentKcal)
    }

    @Test fun noAdjustmentWithoutHistory() {
        val b = DailyBurnModel.compute(2400, BurnSource.PRIOR, WearableDay(900.0, true, false), true, emptyList())
        assertEquals(0, b.activityAdjustmentKcal)
    }

    @Test fun trendSlopeIsRobustToOutlier() {
        val pts = (0 until 20).map { it.toDouble() to 80.0 - 0.1 * it }.toMutableList()
        pts[10] = 10.0 to 85.0
        assertEquals(-0.1, Trend.theilSenSlope(pts), 1e-9)
        assertNotNull(Trend.ema(pts.map { it.second }).last())
    }
}
