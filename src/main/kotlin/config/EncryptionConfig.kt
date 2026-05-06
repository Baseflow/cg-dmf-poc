// SPDX-License-Identifier: EUPL-1.2
// Copyright (C) 2026 Gemeente Utrecht
package com.baseflow.config

/**
 * Encryption configuration for at-rest secrets (e.g. blob storage credentials).
 *
 * Required environment variables:
 * - `ENCRYPTION_SECRET_KEY` — passphrase used for AES-256-PBE-CBC encryption (required).
 * - `ENCRYPTION_SALT`       — hex or plain-text salt (required).
 */
object EncryptionConfig : Config() {
    /** Passphrase used for AES-256-PBE-CBC encryption via `exposed-crypt`. */
    val secretKey: String by lazy { envOrThrow("ENCRYPTION_SECRET_KEY") }

    /** Salt for key derivation. */
    val salt: String by lazy { envOrThrow("ENCRYPTION_SALT") }

    override fun printConfig() {
        // Intentionally not logging actual key/salt values.
    }
}
