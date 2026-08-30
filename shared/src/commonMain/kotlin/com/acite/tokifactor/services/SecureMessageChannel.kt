package com.acite.tokifactor.services

import org.bouncycastle.crypto.engines.AESEngine
import org.bouncycastle.crypto.digests.SHA256Digest
import org.bouncycastle.crypto.generators.HKDFBytesGenerator
import org.bouncycastle.crypto.modes.GCMSIVBlockCipher
import org.bouncycastle.crypto.params.AEADParameters
import org.bouncycastle.crypto.params.HKDFParameters
import org.bouncycastle.crypto.params.KeyParameter
import java.nio.charset.StandardCharsets
import java.security.SecureRandom

object SecureMessageChannel {

    private const val NONCE_BYTES = 12
    private const val TAG_BITS = 128

    // Hardcoded salt to ensure the same magic string yields the same key globally.
    private val HARDCODED_SALT = byteArrayOf(
        0x1A, 0x2B, 0x3C, 0x4D, 0x5E, 0x6F, 0x70, 0x71,
        0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08
    )

    // Context-specific info for HKDF.
    private val HKDF_INFO = "P2P_CHANNEL_V1".toByteArray(StandardCharsets.UTF_8)

    private val random = SecureRandom()

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
     * Encrypts [message] with AES-GCM-SIV. The returned blob is
     * `12-byte random nonce || ciphertext || tag` so the peer can decrypt
     * without a pre-shared nonce.
     */
    fun encrypt(message: ByteArray, magicString: String): ByteArray {
        val nonce = ByteArray(NONCE_BYTES)
        random.nextBytes(nonce)
        val body = crypt(encrypt = true, nonce = nonce, input = message, magicString = magicString)
        val packet = ByteArray(NONCE_BYTES + body.size)
        nonce.copyInto(packet, destinationOffset = 0)
        body.copyInto(packet, destinationOffset = NONCE_BYTES)
        return packet
    }

    /**
     * Decrypts a blob produced by [encrypt]: first 12 bytes are the nonce.
     */
    fun decrypt(packet: ByteArray, magicString: String): ByteArray {
        require(packet.size > NONCE_BYTES) { "Ciphertext too short" }
        val nonce = packet.copyOfRange(0, NONCE_BYTES)
        val body = packet.copyOfRange(NONCE_BYTES, packet.size)
        return crypt(encrypt = false, nonce = nonce, input = body, magicString = magicString)
    }

    private fun crypt(
        encrypt: Boolean,
        nonce: ByteArray,
        input: ByteArray,
        magicString: String,
    ): ByteArray {
        val cipher = GCMSIVBlockCipher(AESEngine.newInstance())
        cipher.init(encrypt, AEADParameters(deriveKey(magicString), TAG_BITS, nonce))
        val output = ByteArray(cipher.getOutputSize(input.size))
        val len = cipher.processBytes(input, 0, input.size, output, 0)
        cipher.doFinal(output, len)
        return output
    }
}
