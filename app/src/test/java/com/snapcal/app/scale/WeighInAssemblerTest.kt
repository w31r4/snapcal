package com.snapcal.app.scale

import com.snapcal.app.science.BodyComposition
import com.snapcal.app.science.Sex
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WeighInAssemblerTest {
    private var now = 0L
    private fun frame(id: Int, mass: Double?, z: Double?, hr: Int? = null, high: Boolean = false, reset: Boolean = false) =
        MiBeacon.BodyComposition(MiBeacon.Model.S400, id, 1, mass, hr, z, high, reset)

    @Test fun barefootSequenceProducesOneWeighInWithBothImpedances() {
        val a = WeighInAssembler { now }
        assertNull(a.accept(frame(10, 69.9, 543.2, 92)))
        assertNull(a.accept(frame(10, 69.9, 543.2, 92))) // repeated advertisement
        val w = a.accept(frame(11, null, 497.6, high = true))!!
        assertEquals(69.9, w.massKg, 1e-9)
        assertEquals(543.2, w.impedanceLow!!, 1e-9)
        assertEquals(497.6, w.impedanceHigh!!, 1e-9)
        assertEquals(92, w.heartRate)
        assertNull(a.flush())
    }

    @Test fun socksCompleteAfterQuietPeriod() {
        val a = WeighInAssembler { now }
        a.accept(frame(1, 74.7, null))
        now = 3000
        assertNull(a.flush())
        now = 7000
        val w = a.flush()!!
        assertEquals(74.7, w.massKg, 1e-9)
        assertNull(w.impedanceLow)
        assertNull(a.flush())
    }

    @Test fun resetDiscardsPartialMeasurement() {
        val a = WeighInAssembler { now }
        a.accept(frame(1, 70.0, 500.0))
        a.accept(frame(2, null, null, reset = true))
        now = 10_000
        assertNull(a.flush())
    }
}

class BodyCompositionTest {
    @Test fun wu2015MatchesHandCalculation() {
        // 13.055 + 0.204*80 + 0.394*175²/500 − 0.136*30 + 8.125 = 57.553
        val e = BodyComposition.wu2015(Sex.MALE, 80.0, 175.0, 30, 500.0)!!
        assertEquals(57.553, e.fatFreeMassKg, 1e-3)
        assertEquals(28.06, e.fatPercent, 0.01)
        assertEquals(4.0, e.fatPercentSe, 0.05) // 3.17 / 80
        assertTrue(!e.outsideValidatedRange)
    }

    @Test fun higherImpedanceMeansMoreFat() {
        val lo = BodyComposition.wu2015(Sex.FEMALE, 65.0, 160.0, 35, 450.0)!!
        val hi = BodyComposition.wu2015(Sex.FEMALE, 65.0, 160.0, 35, 650.0)!!
        assertTrue(hi.fatPercent > lo.fatPercent)
    }

    @Test fun flagsOutOfRangeInputs() {
        assertTrue(BodyComposition.wu2015(Sex.MALE, 80.0, 175.0, 30, 1200.0)!!.outsideValidatedRange)
        assertNotNull(BodyComposition.wu2015(Sex.MALE, 80.0, 175.0, 80, 500.0)?.takeIf { it.outsideValidatedRange })
    }
}
