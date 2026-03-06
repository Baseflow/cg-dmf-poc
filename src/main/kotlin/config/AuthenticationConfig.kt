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

    /** Comma-separated list of client_id values allowed for ZGW-style JWT auth. */
    val zgwAllowedClientIds: List<String> = envOrSystem("ZGW_ALLOWED_CLIENT_IDS", "gzac")
        .split(",").map { it.trim() }.filter { it.isNotEmpty() }

    override fun printConfig() {
        logger.info("AuthenticationConfig: issuer={}", issuer)
        logger.info("AuthenticationConfig: zgwAllowedClientIds={}", zgwAllowedClientIds)
    }
}
