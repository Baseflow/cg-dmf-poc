// SPDX-License-Identifier: EUPL-1.2
// Copyright (C) 2025 Gemeente Utrecht
package com.baseflow.config

import io.github.cdimascio.dotenv.dotenv

/**
 * Base interface for configuration providers.
 * All configuration objects must expose a way to print their effective configuration
 * (with sensitive values masked where applicable).
 */
abstract class Config {
    abstract fun printConfig()

    companion object {
        private val env = dotenv {
            ignoreIfMalformed = true
            ignoreIfMissing = true
        }

        fun envOrSystem(key: String, default: String): String = env[key] ?: System.getenv(key) ?: default
    }
}
