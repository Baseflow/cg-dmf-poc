// SPDX-License-Identifier: EUPL-1.2
// Copyright (C) 2025 Gemeente Utrecht
package com.baseflow.config

import org.slf4j.LoggerFactory

/**
 * AuthenticationConfig reads authentication configuration from environment variables
 * and provides it to services like AuthenticationService.
 */
internal object AuthenticationConfig : Config() {
    private val logger = LoggerFactory.getLogger(AuthenticationConfig::class.java)

    val issuer: String = envOrSystem("OIDC_ISSUER", "http://localhost:8081/realms/cg-dmf")

    override fun printConfig() {
        logger.info("AuthenticationConfig: issuer={}", issuer)
    }
}
