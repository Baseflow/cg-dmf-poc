// SPDX-License-Identifier: EUPL-1.2
// Copyright (C) 2026 Gemeente Utrecht
package com.baseflow.config

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * AES-256-GCM encryption for the OIDC client secret stored at rest.
 *
 * Storage format: Base64( IV[12 bytes] || ciphertext+tag )
 * Key: SHA-256 of OIDC_CLIENT_SECRET_KEY env var → 32 bytes → AES-256 key
 *
 * Requires OIDC_CLIENT_SECRET_KEY to be set — fails fast at startup if missing,
 * preventing silent use of a weak fallback key in production.
 */
internal object OidcCrypto {
    private val secretKey: SecretKeySpec by lazy {
        val raw = Config.envOrThrow("OIDC_CLIENT_SECRET_KEY")
        val keyBytes = MessageDigest.getInstance("SHA-256").digest(raw.toByteArray(Charsets.UTF_8))
        SecretKeySpec(keyBytes, "AES")
    }

    private val random = SecureRandom()

    fun encrypt(plaintext: String): String {
        val iv = ByteArray(12).also { random.nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, GCMParameterSpec(128, iv))
        val ciphertext = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        return Base64.getEncoder().encodeToString(iv + ciphertext)
    }

    fun decrypt(encoded: String): String {
        val combined = Base64.getDecoder().decode(encoded)
        require(combined.size > 12) { "Encrypted value is too short to contain IV + ciphertext" }
        val iv = combined.sliceArray(0 until 12)
        val ciphertext = combined.sliceArray(12 until combined.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(128, iv))
        return String(cipher.doFinal(ciphertext), Charsets.UTF_8)
    }
}
