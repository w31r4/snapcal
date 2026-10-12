package com.snapcal.app.data

import com.snapcal.app.science.ActivityLevel
import com.snapcal.app.science.Sex
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlanTest {
    private val man = Profile(sex = Sex.MALE, age = 30, heightCm = 175, weightKg = 80.0, pal = ActivityLevel.SEDENTARY.pal,
        targetDeficit = Pace.STANDARD.dailyDeficit, goalWeightKg = 72.0)

    @Test fun mifflinStJeorAndSedentaryPal() {
        val plan = Plan.of(man)
        assertEquals(1749, plan.bmr) // 10*80 + 6.25*175 - 5*30 + 5 = 1748.75
        assertEquals(2449, plan.tdeePrior) // × 1.40
        assertEquals(1949, plan.dailyIntakeTarget)
        assertFalse(plan.cappedBySafety)
    }

    @Test fun dynamicModelIsSlowerThan7700Rule() {
        val plan = Plan.of(man)
        // 7700 rule would say 500*84/7700 = 5.45 kg in 12 weeks; dynamic model must predict less.
        assertTrue(plan.kgIn12Weeks < 5.45)
        assertTrue(plan.kgIn12Weeks > 3.5)
        // 8 kg needs more than the static 16 weeks.
        assertTrue(plan.weeksToGoal!! > 16)
    }

    @Test fun adaptiveTdeeReplacesPrior() {
        val plan = Plan.of(man, adaptiveTdee = 2200)
        assertTrue(plan.tdeeIsAdaptive)
        assertEquals(1700, plan.dailyIntakeTarget)
    }

    @Test fun deficitCappedByFloorAndThirtyPercent() {
        val small = Profile(sex = Sex.FEMALE, age = 40, heightCm = 155, weightKg = 50.0, targetDeficit = Pace.AGGRESSIVE.dailyDeficit)
        val plan = Plan.of(small)
        assertTrue(plan.cappedBySafety)
        assertTrue(plan.dailyIntakeTarget >= 1200)
        assertTrue(plan.deficit <= plan.tdee * 0.30)
    }

    @Test fun unreachableGoalHasNoEta() {
        // Steady-state loss at 300 kcal/day for a man is 300/28 ≈ 10.7 kg; asking for 20 kg is unreachable.
        val plan = Plan.of(man.copy(targetDeficit = 300, goalWeightKg = 60.0))
        assertNull(plan.weeksToGoal)
    }

    @Test fun proteinUsesReferenceWeightWhenOverweight() {
        val plan = Plan.of(man) // BMI 26.1 → ref weight at BMI 24 = 73.5 kg
        assertEquals(88..118, plan.proteinG)
    }
}
