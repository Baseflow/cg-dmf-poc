// SPDX-License-Identifier: EUPL-1.2
// Copyright (C) 2025-2026 Gemeente Utrecht
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

    /**
     * Map of client_id → HS256 secret for ZGW JWT signature verification.
     *
     * Sourced from ZGW_CLIENT_SECRETS: a comma-separated list of `client_id:secret` pairs.
     * Example: ZGW_CLIENT_SECRETS=gzac:supersecret,valtimo:anothersecret
     *
     * Clients not present in this map will have their signature skipped when
     * ZGW_REQUIRE_SIGNATURE=false (the default), or rejected when it is true.
     */
    val zgwClientSecrets: Map<String, String> = envOrSystem("ZGW_CLIENT_SECRETS", "")
        .split(",")
        .mapNotNull { entry ->
            val parts = entry.trim().split(":", limit = 2)
            if (parts.size == 2 && parts[0].isNotEmpty() && parts[1].isNotEmpty()) {
                parts[0] to parts[1]
            } else {
                null
            }
        }
        .toMap()

    /**
     * The role name (from `realm_access.roles` in Keycloak JWTs, or `roles` in ZGW JWTs)
     * that grants access to the admin API.  Defaults to `dmf-admin`.
     */
    val adminRole: String = envOrSystem("ADMIN_ROLE", "dmf-admin")

    override fun printConfig() {
        logger.info("AuthenticationConfig: issuer={}", issuer)
        logger.info("AuthenticationConfig: zgwAllowedClientIds={}", zgwAllowedClientIds)
        logger.info("AuthenticationConfig: zgwClientSecrets configured for clients={}", zgwClientSecrets.keys)
        logger.info("AuthenticationConfig: adminRole={}", adminRole)
    }
}
