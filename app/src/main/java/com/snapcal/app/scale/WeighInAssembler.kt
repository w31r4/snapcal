package com.snapcal.app.scale

/** One completed weigh-in from the scale. */
data class WeighIn(
    val massKg: Double,
    /** 50 kHz foot-to-foot impedance, Ω (null when measured with socks / dry feet). */
    val impedanceLow: Double?,
    /** 250 kHz foot-to-foot impedance, Ω. */
    val impedanceHigh: Double?,
    val heartRate: Int?,
    val profileId: Int,
    val atMillis: Long,
)

/**
 * The S400 repeats each advertisement many times and sends a weigh-in as a short sequence:
 * weight + 50 kHz impedance (+ heart rate) while measuring, then a final packet with only the 250 kHz impedance.
 * With socks there is no impedance and the weight packet is final. Feed frames in; complete weigh-ins come out.
 */
class WeighInAssembler(private val clock: () -> Long = System::currentTimeMillis) {
    private var lastPacketId = -1
    private var mass: Double? = null
    private var low: Double? = null
    private var hr: Int? = null
    private var profile = 0
    private var lastMassAt = 0L
    private var emitted = false

    /** Returns a completed weigh-in when [frame] finishes one. */
    fun accept(frame: MiBeacon.BodyComposition): WeighIn? {
        if (frame.packetId == lastPacketId) return null
        lastPacketId = frame.packetId
        if (frame.isReset) {
            reset()
            return null
        }
        profile = frame.profileId
        if (frame.isHighFrequency) {
            val m = mass ?: return null
            return emit(WeighIn(m, low, frame.impedanceOhm, hr, profile, clock()))
        }
        frame.massKg?.let { mass = it; lastMassAt = clock(); emitted = false }
        frame.impedanceOhm?.let { low = it }
        frame.heartRate?.let { hr = it }
        return null
    }

    /**
     * Call periodically: if weight packets stopped arriving without an impedance packet (socks, S200),
     * complete with the weight alone after [quietMs].
     */
    fun flush(quietMs: Long = 6000): WeighIn? {
        val m = mass ?: return null
        if (emitted || clock() - lastMassAt < quietMs) return null
        return emit(WeighIn(m, low, null, hr, profile, lastMassAt))
    }

    private fun emit(w: WeighIn): WeighIn? {
        if (emitted) return null
        emitted = true
        val out = w
        mass = null; low = null; hr = null
        return out
    }

    private fun reset() {
        mass = null; low = null; hr = null; emitted = false
    }
}
