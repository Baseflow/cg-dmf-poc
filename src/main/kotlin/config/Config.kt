// SPDX-License-Identifier: EUPL-1.2
// Copyright (C) 2025-2026 Gemeente Utrecht
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

        fun envOrThrow(key: String): String =
            env[key] ?: System.getenv(key) ?: throw IllegalStateException("Environment variable '$key' is required but not set.")

        /**
         * Returns a merged map of all key→value pairs from both the `.env` file (dotenv)
         * and the process environment ([System.getenv]).
         * Dotenv entries take precedence over system env entries with the same key,
         * matching the resolution order used by [envOrSystem].
         */
        fun envEntries(): Map<String, String> = System.getenv() + env.entries().associate { it.key to it.value }
    }
}
