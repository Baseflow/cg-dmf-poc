// SPDX-License-Identifier: EUPL-1.2
// Copyright (C) 2025-2026 Gemeente Utrecht
package com.baseflow.config

import org.slf4j.LoggerFactory

object WopiConfig : Config() {
    private val logger = LoggerFactory.getLogger(WopiConfig::class.java)

    val wopiEnabled: Boolean = envOrSystem("WOPI_ENABLED", "false").toBoolean()

    /**
     * Secret used to sign WOPI Short-Lived Access Tokens (SLATs).
     * Must be set when WOPI is enabled. Minimum recommended length: 32 characters.
     */
    val slatSecret: String by lazy { envOrThrow("WOPI_SLAT_SECRET") }

    /**
     * Lifetime of issued SLATs in seconds. Defaults to 3600 (1 hour).
     */
    val slatTtlSeconds: Long = envOrSystem("WOPI_SLAT_TTL_SECONDS", "3600").toLong()

    override fun printConfig() {
        logger.info("WOPI_ENABLED={}", wopiEnabled)
        logger.info("WOPI_SLAT_TTL_SECONDS={}", slatTtlSeconds)
    }

    fun isEnabled(): Boolean = wopiEnabled
}
