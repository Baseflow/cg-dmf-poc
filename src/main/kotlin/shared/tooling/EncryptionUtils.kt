// SPDX-License-Identifier: EUPL-1.2
// Copyright (C) 2026 Gemeente Utrecht
package com.baseflow.shared.tooling

import com.baseflow.shared.config.EncryptionConfig
import org.jetbrains.exposed.v1.crypt.Algorithms
import org.jetbrains.exposed.v1.crypt.Encryptor

private const val GCM_PREFIX = "gcm:"

/**
 * Returns an [Encryptor] that writes AES-256-PBE-GCM ciphertexts prefixed with "gcm:"
 * and can still decrypt legacy AES-256-PBE-CBC ciphertexts (those without the prefix).
 *
 * Both encryptors are initialised lazily so that [EncryptionConfig.secretKey] and
 * [EncryptionConfig.salt] are not read until the first actual encrypt/decrypt call.
 */
fun multiAlgorithmEncryptor(): Encryptor {
    val gcm = lazy { Algorithms.AES_256_PBE_GCM(EncryptionConfig.secretKey, EncryptionConfig.salt) }
    val cbc = lazy { Algorithms.AES_256_PBE_CBC(EncryptionConfig.secretKey, EncryptionConfig.salt) }
    return Encryptor(
        encryptFn = { GCM_PREFIX + gcm.value.encrypt(it) },
        decryptFn = { ciphertext ->
            if (ciphertext.startsWith(GCM_PREFIX)) {
                gcm.value.decrypt(ciphertext.removePrefix(GCM_PREFIX))
            } else {
                cbc.value.decrypt(ciphertext)
            }
        },
        maxColLengthFn = { gcm.value.maxColLength(it) + GCM_PREFIX.length },
    )
}
