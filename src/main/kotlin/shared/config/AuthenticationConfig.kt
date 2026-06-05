// SPDX-License-Identifier: EUPL-1.2
// Copyright (C) 2025-2026 Gemeente Utrecht
package com.baseflow.shared.config

import org.slf4j.LoggerFactory

/**
 * AuthenticationConfig reads authentication configuration from environment variables
 * and provides it to services like AuthenticationService.
 */
internal object AuthenticationConfig : Config() {
    private val logger = LoggerFactory.getLogger(AuthenticationConfig::class.java)

    val issuer: String = envOrSystem("OIDC_ISSUER", "http://localhost:8081/realms/cg-dmf")

    /**
     * Optional Keycloak/OIDC client id used to read client roles from
     * `resource_access.<client_id>.roles`.
     *
     * If empty, role extraction falls back to token claims (`azp`, then `client_id`),
     * and only then to all `resource_access.*.roles` entries.
     */
    val oidcResourceClientId: String = envOrSystem("OIDC_RESOURCE_CLIENT_ID", "")

    /**
     * The role name (from `realm_access.roles`, `resource_access.<client_id>.roles`
     * in Keycloak/OIDC JWTs, or `roles` in ZGW JWTs)
     * that grants access to the admin API. Defaults to `dmf-admin`.
     */
    val adminRole: String = envOrSystem("ADMIN_ROLE", "dmf-admin")

    /**
     * Map of client_id → HS256 secret for JWT signature verification.
     *
     * Sourced from CLIENT_CREDENTIALS: a comma-separated list of `client_id:secret` pairs.
     * Example: CLIENT_CREDENTIALS=gzac:supersecret,valtimo:anothersecret
     *
     */
    val clientCredentials: Map<String, String> = envOrSystem("CLIENT_CREDENTIALS", "")
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

    override fun printConfig() {
        logger.info("AuthenticationConfig: OIDC issuer={}", issuer)
        logger.info("AuthenticationConfig: OIDC ResourceClientId={}", oidcResourceClientId.ifBlank { "<auto>" })
        logger.info("AuthenticationConfig: DMF adminRole={}", adminRole)
        logger.info("AuthenticationConfig: clientCredentials configured for clients={}", clientCredentials.keys)
    }
}
