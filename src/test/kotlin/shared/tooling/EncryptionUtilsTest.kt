// SPDX-License-Identifier: EUPL-1.2
// Copyright (C) 2026 Gemeente Utrecht
package com.baseflow.shared.tooling

import com.baseflow.shared.config.EncryptionConfig
import org.jetbrains.exposed.v1.crypt.Algorithms
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class EncryptionUtilsTest {

    private val encryptor = multiAlgorithmEncryptor()

    @Test
    fun `encrypt produces gcm-prefixed ciphertext`() {
        val ciphertext = encryptor.encrypt("my-secret")
        assertTrue(ciphertext.startsWith("gcm:"), "Expected 'gcm:' prefix, got: $ciphertext")
    }

    @Test
    fun `GCM round-trip returns original plaintext`() {
        val plaintext = "my-secret-value"
        assertEquals(plaintext, encryptor.decrypt(encryptor.encrypt(plaintext)))
    }

    @Test
    fun `legacy CBC ciphertext without gcm prefix decrypts correctly`() {
        val cbc = Algorithms.AES_256_PBE_CBC(EncryptionConfig.secretKey, EncryptionConfig.salt)
        val legacyCiphertext = cbc.encrypt("legacy-secret")
        assertEquals("legacy-secret", encryptor.decrypt(legacyCiphertext))
    }

    @Test
    fun `corrupted gcm-prefixed ciphertext throws and does not fall back to CBC`() {
        assertFailsWith<Exception> {
            encryptor.decrypt("gcm:notvalidciphertext!!!")
        }
    }

    @Test
    fun `maxColLength adds gcm prefix overhead to raw GCM length`() {
        val rawGcm = Algorithms.AES_256_PBE_GCM(EncryptionConfig.secretKey, EncryptionConfig.salt)
        assertEquals(rawGcm.maxColLength(100) + "gcm:".length, encryptor.maxColLength(100))
    }
}
