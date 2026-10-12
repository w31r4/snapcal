package com.snapcal.app.science

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PhotoCalibrationTest {
    @Test fun inactiveWithFewSamples() {
        val r = PhotoCalibration.estimate(List(5) { 500 to 600 })
        assertFalse(r.active)
        assertEquals(5, r.samples)
    }

    @Test fun recoversSystematicUnderestimateWithShrinkage() {
        // AI consistently 20 % low → true/ai = 1.25; with n=20 and prior weight 5 the factor is pulled toward 1.
        val r = PhotoCalibration.estimate(List(20) { 400 to 500 })
        assertTrue(r.active)
        assertEquals(Math.exp(Math.log(1.25) * 20 / 25), r.factor, 1e-9)
        assertEquals(20.0, r.medianAbsErrorPct, 1e-9)
    }

    @Test fun robustToOutliers() {
        val pairs = List(15) { 500 to 550 } + listOf(100 to 2000, 2000 to 100)
        val r = PhotoCalibration.estimate(pairs)
        assertTrue(r.factor in 1.05..1.12)
    }

    @Test fun clampedToPlausibleRange() {
        val r = PhotoCalibration.estimate(List(100) { 100 to 1000 })
        assertEquals(1.6, r.factor, 1e-9)
    }
}
