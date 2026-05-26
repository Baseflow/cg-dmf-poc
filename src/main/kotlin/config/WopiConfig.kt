// SPDX-License-Identifier: EUPL-1.2
// Copyright (C) 2025-2026 Gemeente Utrecht
package com.baseflow.config

import org.slf4j.LoggerFactory

class WopiConfig(
    /**
     * Indicates whether WOPI protocol is enabled for this instance of the DMF.
     */
    val wopiEnabled: Boolean,
    /**
     * The Short-lived Access Token (SLAT) secret.
     *
     * The SLAT is used as a salt to encode or decode the token and must be set when WOPI is enabled. Minimum
     * recommended length: 32 characters.
     */
    val slatSecret: String,
    /**
     * The SLAT token lifetime in seconds. Defaults to 3600 (1 hour).
     *
     * The SLAT token is valid for this amount of time. Requests that use an expired SLAT should be rejected.
     */
    val slatTtlSeconds: Long = 3600,
) : Config() {
    private val logger = LoggerFactory.getLogger(WopiConfig::class.java)

    override fun printConfig() {
        logger.info("WOPI_ENABLED={}", wopiEnabled)
        logger.info("WOPI_SLAT_TTL_SECONDS={}", slatTtlSeconds)
    }

    fun isEnabled(): Boolean = wopiEnabled

    companion object {
        fun fromEnv(): WopiConfig = WopiConfig(
            wopiEnabled = envOrSystem("WOPI_ENABLED", "false").toBoolean(),
            slatSecret = envOrThrow("WOPI_SLAT_SECRET"),
            slatTtlSeconds = envOrSystem("WOPI_SLAT_TTL_SECONDS", "3600").toLong(),
        )
    }
}
