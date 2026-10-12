package com.snapcal.app.scale

/**
 * Decoder for Xiaomi MiBeacon v4/v5 advertisements (service data UUID 0xFE95) from Mijia scales.
 *
 * Format and object layout follow the Home Assistant `xiaomi-ble` library (Apache-2.0,
 * github.com/Bluetooth-Devices/xiaomi-ble), whose S400 test vectors are reproduced in our unit tests.
 * Payloads are AES-CCM encrypted with the per-device 16-byte bindkey, which the owner extracts once from
 * their own Xiaomi account.
 */
object MiBeacon {
    const val SERVICE_UUID_16 = 0xFE95

    enum class Model(val label: String, val hasImpedance: Boolean) {
        S400("体脂秤 S400", true),
        S400_PRO("体脂秤 S400 Pro", true),
        S200("体重秤 S200", false),
    }

    /** Product IDs from xiaomi-ble devices.py. */
    val PRODUCTS: Map<Int, Model> = mapOf(
        0x30D9 to Model.S400, 0x3BD5 to Model.S400, 0x48CF to Model.S400,
        0x4B05 to Model.S400_PRO,
        0x45C9 to Model.S200, 0x4C04 to Model.S200, 0x4DCB to Model.S200,
    )

    sealed interface Frame {
        val model: Model
        val packetId: Int
    }

    /** S400 object 0x6E16 (one advertisement of a weigh-in sequence). */
    data class BodyComposition(
        override val model: Model,
        override val packetId: Int,
        val profileId: Int,
        /** kg, or null on the final packet (which carries only the high-frequency impedance). */
        val massKg: Double?,
        val heartRate: Int?,
        val impedanceOhm: Double?,
        /** Final packet of a measurement: impedance is the 250 kHz value. */
        val isHighFrequency: Boolean,
        /** All-zero packet: the person stepped off. */
        val isReset: Boolean,
    ) : Frame

    data class Unsupported(override val model: Model, override val packetId: Int, val objectId: Int) : Frame

    sealed interface Result {
        data class Ok(val frames: List<Frame>) : Result
        data object NotAScale : Result
        /** No measurement object, or encrypted and we don't hold this device's key. */
        data object NoPayload : Result
        data object BadKey : Result
    }

    /** Identify a supported scale from the unencrypted header, without a key. */
    fun modelOf(serviceData: ByteArray): Model? {
        if (serviceData.size < 5) return null
        return PRODUCTS[(serviceData[2].toInt() and 0xFF) or ((serviceData[3].toInt() and 0xFF) shl 8)]
    }

    /**
     * @param serviceData the FE95 service data bytes
     * @param macAddress the advertiser address "AA:BB:CC:DD:EE:FF"
     */
    fun parse(serviceData: ByteArray, macAddress: String, bindkey: ByteArray?): Result {
        val d = serviceData
        if (d.size < 5) return Result.NotAScale
        val frctrl = u8(d, 0) or (u8(d, 1) shl 8)
        val version = frctrl ushr 12
        val mesh = (frctrl shr 7) and 1
        val hasObject = (frctrl shr 6) and 1
        val hasCapability = (frctrl shr 5) and 1
        val hasMac = (frctrl shr 4) and 1
        val encrypted = (frctrl shr 3) and 1
        if (mesh != 0 || version < 4) return Result.NotAScale

        val productId = u8(d, 2) or (u8(d, 3) shl 8)
        val model = PRODUCTS[productId] ?: return Result.NotAScale
        val packetId = u8(d, 4)

        val sourceMac = macAddress.split(":").map { it.toInt(16).toByte() }.toByteArray()
        var i = 5
        if (hasMac != 0) {
            i += 6
            if (d.size < i) return Result.NotAScale
            val macInFrame = d.copyOfRange(5, 11).reversedArray()
            if (!macInFrame.contentEquals(sourceMac)) return Result.NotAScale
        }
        if (hasCapability != 0) {
            i += 1
            if (d.size < i) return Result.NotAScale
            if ((u8(d, i - 1) and 0x20) != 0) i += 1
        }
        if (hasObject == 0) return Result.NoPayload

        val payload = if (encrypted != 0) {
            val key = bindkey ?: return Result.NoPayload
            if (d.size < i + 9) return Result.NotAScale
            // nonce = reversed MAC (6) + product id & frame counter (3) + extended counter (3)
            val nonce = sourceMac.reversedArray() + d.copyOfRange(2, 5) + d.copyOfRange(d.size - 7, d.size - 4)
            AesCcm.decrypt(
                key = key,
                nonce = nonce,
                ciphertext = d.copyOfRange(i, d.size - 7),
                tag = d.copyOfRange(d.size - 4, d.size),
                aad = byteArrayOf(0x11),
            ) ?: return Result.BadKey
        } else {
            d.copyOfRange(i, d.size)
        }

        val frames = ArrayList<Frame>()
        var p = 0
        while (payload.size >= p + 3) {
            val objId = u8(payload, p) or (u8(payload, p + 1) shl 8)
            val len = u8(payload, p + 2)
            val end = p + 3 + len
            if (payload.size < end) break
            val obj = payload.copyOfRange(p + 3, end)
            frames += when (objId) {
                0x6E16 -> decodeBodyComposition(obj, model, packetId)
                else -> Unsupported(model, packetId, objId)
            }
            p = end
        }
        return Result.Ok(frames)
    }

    private fun decodeBodyComposition(obj: ByteArray, model: Model, packetId: Int): Frame {
        if (obj.size < 5) return Unsupported(model, packetId, 0x6E16)
        val profileId = u8(obj, 0)
        val data = (u8(obj, 1).toLong()) or (u8(obj, 2).toLong() shl 8) or (u8(obj, 3).toLong() shl 16) or (u8(obj, 4).toLong() shl 24)
        val mass = (data and 0x7FF).toInt()
        val hr = ((data shr 11) and 0x7F).toInt()
        val imp = (data ushr 18).toInt()
        return BodyComposition(
            model = model,
            packetId = packetId,
            profileId = profileId,
            massKg = if (mass != 0) mass / 10.0 else null,
            heartRate = if (hr in 1..126) hr + 50 else null,
            impedanceOhm = if (imp != 0) imp / 10.0 else null,
            isHighFrequency = mass == 0 && hr == 0 && imp != 0,
            isReset = mass == 0 && hr == 0 && imp == 0,
        )
    }

    private fun u8(b: ByteArray, i: Int) = b[i].toInt() and 0xFF

    fun parseKey(hex: String): ByteArray? {
        val h = hex.trim().lowercase().replace(" ", "")
        if (h.length != 32 || !h.all { it in "0123456789abcdef" }) return null
        return h.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    }
}
