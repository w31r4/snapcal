package com.snapcal.app.scale

import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

/**
 * Minimal AES-CCM decryption (RFC 3610 / NIST SP 800-38C) built on AES-ECB, so it runs identically on
 * Android and in JVM unit tests without depending on a provider's CCM support.
 */
object AesCcm {
    /** Returns the plaintext, or null when the authentication tag doesn't match (wrong key or corrupt packet). */
    fun decrypt(key: ByteArray, nonce: ByteArray, ciphertext: ByteArray, tag: ByteArray, aad: ByteArray): ByteArray? {
        require(key.size == 16) { "key must be 16 bytes" }
        require(nonce.size in 7..13) { "nonce must be 7..13 bytes" }
        val aes = Cipher.getInstance("AES/ECB/NoPadding").apply { init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES")) }
        val m = tag.size
        val l = 15 - nonce.size

        fun counterBlock(i: Int) = ByteArray(16).also { b ->
            b[0] = (l - 1).toByte()
            nonce.copyInto(b, 1)
            for (k in 0 until l) b[15 - k] = ((i ushr (8 * k)) and 0xFF).toByte()
        }

        // CTR decryption; counter 0 encrypts the tag.
        val plain = ByteArray(ciphertext.size)
        var offset = 0
        var ctr = 1
        while (offset < ciphertext.size) {
            val ks = aes.doFinal(counterBlock(ctr++))
            for (k in 0 until minOf(16, ciphertext.size - offset)) plain[offset + k] = (ciphertext[offset + k].toInt() xor ks[k].toInt()).toByte()
            offset += 16
        }
        val s0 = aes.doFinal(counterBlock(0))
        val expectedTag = ByteArray(m) { (tag[it].toInt() xor s0[it].toInt()).toByte() }

        // CBC-MAC over B0 || encoded AAD || plaintext.
        val b0 = ByteArray(16)
        b0[0] = ((if (aad.isNotEmpty()) 0x40 else 0) or (((m - 2) / 2) shl 3) or (l - 1)).toByte()
        nonce.copyInto(b0, 1)
        for (k in 0 until l) b0[15 - k] = ((plain.size ushr (8 * k)) and 0xFF).toByte()
        val macInput = ArrayList<Byte>()
        macInput.addAll(b0.toList())
        if (aad.isNotEmpty()) {
            macInput.add(((aad.size ushr 8) and 0xFF).toByte())
            macInput.add((aad.size and 0xFF).toByte())
            macInput.addAll(aad.toList())
            while (macInput.size % 16 != 0) macInput.add(0)
        }
        macInput.addAll(plain.toList())
        while (macInput.size % 16 != 0) macInput.add(0)
        var x = ByteArray(16)
        val bytes = macInput.toByteArray()
        for (blk in bytes.indices step 16) {
            val y = ByteArray(16) { (x[it].toInt() xor bytes[blk + it].toInt()).toByte() }
            x = aes.doFinal(y)
        }
        var diff = 0
        for (k in 0 until m) diff = diff or (x[k].toInt() xor expectedTag[k].toInt())
        return if (diff == 0) plain else null
    }
}
