package com.snapcal.app.scale

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Test vectors from Home Assistant xiaomi-ble tests/test_parser.py (Apache-2.0): real S400 captures with the
 * bindkeys of those test devices, already public upstream. Not keys for any device of ours.
 */
class MiBeaconTest {
    private fun bytes(vararg v: Int) = ByteArray(v.size) { v[it].toByte() }

    // b'HY\xd5;\n\xbc\x07\x8f\xf24\x8c\x84A8\xe90"\x00\x00\x00\x9eS\x85\x99'
    private val s400First = bytes(0x48, 0x59, 0xd5, 0x3b, 0x0a, 0xbc, 0x07, 0x8f, 0xf2, 0x34, 0x8c, 0x84, 0x41, 0x38, 0xe9, 0x30, 0x22, 0x00, 0x00, 0x00, 0x9e, 0x53, 0x85, 0x99)
    // b"HY\xd5;\x0b\xd6\xef\x0b%\xdbrx^~/F\xd6\x00\x00\x00\xd8d-\xf6"
    private val s400Second = bytes(0x48, 0x59, 0xd5, 0x3b, 0x0b, 0xd6, 0xef, 0x0b, 0x25, 0xdb, 0x72, 0x78, 0x5e, 0x7e, 0x2f, 0x46, 0xd6, 0x00, 0x00, 0x00, 0xd8, 0x64, 0x2d, 0xf6)
    private val mac = "8C:D0:B2:F6:BE:EF"
    private val key = MiBeacon.parseKey("0728974d657a4b60964c1b1677f35f7c")

    private fun body(r: MiBeacon.Result): MiBeacon.BodyComposition =
        (r as MiBeacon.Result.Ok).frames.single() as MiBeacon.BodyComposition

    @Test fun decodesWeightLowImpedanceAndHeartRate() {
        val f = body(MiBeacon.parse(s400First, mac, key))
        assertEquals(MiBeacon.Model.S400, f.model)
        assertEquals(69.9, f.massKg!!, 1e-9)
        assertEquals(543.2, f.impedanceOhm!!, 1e-9)
        assertEquals(92, f.heartRate)
        assertEquals(1, f.profileId)
        assertFalse(f.isHighFrequency)
    }

    @Test fun decodesFinalHighFrequencyPacket() {
        val f = body(MiBeacon.parse(s400Second, mac, key))
        assertNull(f.massKg)
        assertEquals(497.6, f.impedanceOhm!!, 1e-9)
        assertTrue(f.isHighFrequency)
    }

    @Test fun socksMeasurementHasWeightButNoImpedance() {
        // b"HY\xd5;\x71\x53\x04\x38\xb5\x89\x4b" b"\x24\x2c\x20\x99\x08\xda\x00\x00\x00\x47\x9e\xcd\xa3"
        val d = bytes(0x48, 0x59, 0xd5, 0x3b, 0x71, 0x53, 0x04, 0x38, 0xb5, 0x89, 0x4b, 0x24, 0x2c, 0x20, 0x99, 0x08, 0xda, 0x00, 0x00, 0x00, 0x47, 0x9e, 0xcd, 0xa3)
        val f = body(MiBeacon.parse(d, "04:AE:47:67:C6:7C", MiBeacon.parseKey("02d2900363ef629c736a4549677acbee")))
        assertEquals(74.7, f.massKg!!, 1e-9)
        assertNull(f.impedanceOhm)
    }

    @Test fun decodesS200WeightObject() {
        // b"HY\x04L\x01\x9a\x80\xa2u\x93\x90\x10\xf0\xab\xc4\xfa\xdc\x06\x00\x00=)\xc0D"
        val d = bytes(0x48, 0x59, 0x04, 0x4c, 0x01, 0x9a, 0x80, 0xa2, 0x75, 0x93, 0x90, 0x10, 0xf0, 0xab, 0xc4, 0xfa, 0xdc, 0x06, 0x00, 0x00, 0x3d, 0x29, 0xc0, 0x44)
        val f = body(MiBeacon.parse(d, "D0:7B:6F:27:D7:29", MiBeacon.parseKey("653b1b10e1cb35e4ac5e60fa45f3bf29")))
        assertEquals(MiBeacon.Model.S200, f.model)
        assertEquals(62.25, f.massKg!!, 1e-9)
        assertEquals(1, f.profileId)
        assertNull(f.impedanceOhm)
    }

    @Test fun wrongKeyIsRejectedByAuthTag() {
        val wrong = MiBeacon.parseKey("00000000000000000000000000000000")
        assertEquals(MiBeacon.Result.BadKey, MiBeacon.parse(s400First, mac, wrong))
    }

    @Test fun wrongMacFailsAuthentication() {
        assertEquals(MiBeacon.Result.BadKey, MiBeacon.parse(s400First, "8C:D0:B2:F6:BE:EE", key))
    }

    @Test fun keyParsing() {
        assertNull(MiBeacon.parseKey("abc"))
        assertEquals(16, MiBeacon.parseKey(" 0728974D657A4B60964C1B1677F35F7C ")!!.size)
    }
}
