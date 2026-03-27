// SPDX-License-Identifier: EUPL-1.2
// Copyright (C) 2025-2026 Gemeente Utrecht
package com.baseflow.config

import io.github.cdimascio.dotenv.dotenv
import org.slf4j.LoggerFactory

/**
 * Base interface for configuration providers.
 * All configuration objects must expose a way to print their effective configuration
 * (with sensitive values masked where applicable).
 */
abstract class Config {
    abstract fun printConfig()

    companion object {
        private val logger = LoggerFactory.getLogger(Config::class.java)

        private val env = dotenv {
            ignoreIfMalformed = true
            ignoreIfMissing = true
        }

        fun envOrSystem(key: String, default: String): String = env[key] ?: System.getenv(key) ?: default

        fun envOrThrow(key: String): String =
            env[key] ?: System.getenv(key) ?: throw IllegalStateException("Environment variable '$key' is required but not set.")

        /**
         * Reads [key], falling back to [legacyKey] if [key] is absent.
         * If the legacy key is used, a deprecation warning is logged.
         */
        fun envOrSystemWithLegacy(key: String, legacyKey: String, default: String): String {
            val primary = env[key] ?: System.getenv(key)
            if (primary != null) return primary
            val legacy = env[legacyKey] ?: System.getenv(legacyKey)
            if (legacy != null) {
                logger.warn(
                    "Deprecated env var '{}' is set but '{}' is not. " +
                        "Please rename it to '{}' in your configuration.",
                    legacyKey,
                    key,
                    key,
                )
                return legacy
            }
            return default
        }

        /**
         * Like [envOrThrow], but also accepts the [legacyKey] as a fallback.
         * Logs a deprecation warning when the legacy key is used.
         */
        fun envOrThrowWithLegacy(key: String, legacyKey: String): String {
            val primary = env[key] ?: System.getenv(key)
            if (primary != null) return primary
            val legacy = env[legacyKey] ?: System.getenv(legacyKey)
            if (legacy != null) {
                logger.warn(
                    "Deprecated env var '{}' is set but '{}' is not. " +
                        "Please rename it to '{}' in your configuration.",
                    legacyKey,
                    key,
                    key,
                )
                return legacy
            }
            throw IllegalStateException(
                "Environment variable '$key' is required but not set. " +
                    "Legacy fallback '$legacyKey' was also not found.",
            )
        }


        /**
         * Returns a merged map of all key→value pairs from both the `.env` file (dotenv)
         * and the process environment ([System.getenv]).
         * Dotenv entries take precedence over system env entries with the same key,
         * matching the resolution order used by [envOrSystem].
         */
        fun envEntries(): Map<String, String> = System.getenv() + env.entries().associate { it.key to it.value }
    }
}
