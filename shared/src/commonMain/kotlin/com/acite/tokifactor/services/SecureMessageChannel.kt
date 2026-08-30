package com.acite.tokifactor.services

import org.bouncycastle.crypto.engines.AESEngine
import org.bouncycastle.crypto.digests.SHA256Digest
import org.bouncycastle.crypto.generators.HKDFBytesGenerator
import org.bouncycastle.crypto.modes.GCMSIVBlockCipher
import org.bouncycastle.crypto.params.AEADParameters
import org.bouncycastle.crypto.params.HKDFParameters
import org.bouncycastle.crypto.params.KeyParameter
import java.nio.charset.StandardCharsets

object SecureMessageChannel {

    // Hardcoded salt to ensure the same magic string yields the same key globally.
    private val HARDCODED_SALT = byteArrayOf(
        0x1A, 0x2B, 0x3C, 0x4D, 0x5E, 0x6F, 0x70, 0x71,
        0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08
    )

    // Context-specific info for HKDF.
    private val HKDF_INFO = "P2P_CHANNEL_V1".toByteArray(StandardCharsets.UTF_8)

    // 12-byte fixed nonce. ByteArray initializes with zeroes automatically.
    private val FIXED_NONCE = ByteArray(12)

    /**
     * Derives a 256-bit (32-byte) AES key from the user-provided magic string using HKDF-SHA256.
     */
    private fun deriveKey(magicString: String): KeyParameter {
        val ikm = magicString.toByteArray(StandardCharsets.UTF_8)

        val hkdf = HKDFBytesGenerator(SHA256Digest())
        hkdf.init(HKDFParameters(ikm, HARDCODED_SALT, HKDF_INFO))

        val keyBytes = ByteArray(32)
        hkdf.generateBytes(keyBytes, 0, keyBytes.size)

        return KeyParameter(keyBytes)
    }

    /**
     * Encrypts a message using AES-GCM-SIV.
     */
    fun encrypt(message: ByteArray, magicString: String): ByteArray {
        val key = deriveKey(magicString)

        // Use the factory method to avoid deprecation warnings.
        val cipher = GCMSIVBlockCipher(AESEngine.newInstance())

        val params = AEADParameters(key, 128, FIXED_NONCE)
        cipher.init(true, params)

        val output = ByteArray(cipher.getOutputSize(message.size))
        val len = cipher.processBytes(message, 0, message.size, output, 0)
        cipher.doFinal(output, len)

        return output
    }

    /**
     * Decrypts a ciphertext using AES-GCM-SIV.
     */
    fun decrypt(ciphertext: ByteArray, magicString: String): ByteArray {
        val key = deriveKey(magicString)

        // Use the factory method to avoid deprecation warnings.
        val cipher = GCMSIVBlockCipher(AESEngine.newInstance())
        val params = AEADParameters(key, 128, FIXED_NONCE)
        cipher.init(false, params)

        val output = ByteArray(cipher.getOutputSize(ciphertext.size))
        val len = cipher.processBytes(ciphertext, 0, ciphertext.size, output, 0)
        cipher.doFinal(output, len)

        return output
    }
}