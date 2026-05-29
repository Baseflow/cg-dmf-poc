// SPDX-License-Identifier: EUPL-1.2
// Copyright (C) 2026 Gemeente Utrecht
package com.baseflow.api.settings

import com.baseflow.api.middleware.ForbiddenException
import com.baseflow.api.settings.routes.applicationSettingsRoutes
import com.baseflow.api.settings.routes.blobStorageRepositorySettingsRoutes
import com.baseflow.api.settings.routes.dmfSettingsRoutes
import com.baseflow.api.settings.routes.oidcProviderSettingsRoutes
import com.baseflow.api.settings.routes.zgwApiSettingsRoutes
import com.baseflow.config.AuthenticationConfig
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.createRouteScopedPlugin
import io.ktor.server.auth.AuthenticationChecked
import io.ktor.server.auth.AuthenticationStrategy
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.authentication
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.principal
import io.ktor.server.routing.Route
import io.ktor.server.routing.route
import io.ktor.server.routing.routing

/**
 * Role claims are interpreted based on the auth provider that authenticated the request:
 * - auth-jwt (OIDC): realm/resource roles
 * - auth-zgw (ZGW): top-level roles
 */
private enum class AuthTokenType {
    OIDC,
    ZGW,
    UNKNOWN,
}

private fun ApplicationCall.authenticatedTokenType(): AuthTokenType {
    if (authentication.principal<JWTPrincipal>("auth-jwt") != null) return AuthTokenType.OIDC
    if (authentication.principal<JWTPrincipal>("auth-zgw") != null) return AuthTokenType.ZGW
    return AuthTokenType.UNKNOWN
}

/**
 * Extracts roles from a Keycloak or ZGW JWT principal.
 *
 * - Keycloak (auth-jwt): roles can be in `realm_access.roles` and/or
 *   `resource_access.<client_id>.roles`.
 * - ZGW (auth-zgw): roles may be in a top-level `roles` claim (string array).
 */
private fun ApplicationCall.jwtRoles(): Set<String> {
    val principal = principal<JWTPrincipal>() ?: return emptySet()
    val roles = mutableSetOf<String>()
    val tokenType = authenticatedTokenType()

    if (tokenType == AuthTokenType.OIDC) {
        // Keycloak: realm_access.roles
        runCatching {
            principal.payload
                .getClaim("realm_access")
                .asMap()["roles"]
                ?.let { it as? List<*> }
                ?.filterIsInstance<String>()
                ?.let { roles.addAll(it) }
        }

        // Keycloak: resource_access.<client_id>.roles
        runCatching {
            val resourceAccess = principal.payload
                .getClaim("resource_access")
                .asMap()

            val preferredClientId = AuthenticationConfig.oidcResourceClientId
                .ifBlank { principal.payload.getClaim("azp").asString().orEmpty() }
                .ifBlank { principal.payload.getClaim("client_id").asString().orEmpty() }

            if (preferredClientId.isNotBlank()) {
                (resourceAccess[preferredClientId] as? Map<*, *>)
                    ?.get("roles")
                    ?.let { it as? List<*> }
                    ?.filterIsInstance<String>()
                    ?.let { roles.addAll(it) }
            } else {
                // Fallback for tokens without azp/client_id when no explicit client id is configured.
                resourceAccess.values
                    .asSequence()
                    .mapNotNull { it as? Map<*, *> }
                    .mapNotNull { it["roles"] as? List<*> }
                    .flatMap { it.asSequence() }
                    .filterIsInstance<String>()
                    .forEach { roles.add(it) }
            }
        }
    }

    if (tokenType == AuthTokenType.ZGW) {
        // ZGW: top-level roles claim
        runCatching {
            principal.payload
                .getClaim("roles")
                .asList(String::class.java)
                ?.let { roles.addAll(it) }
        }
    }

    return roles
}

/**
 * Settings API Module
 *
 * Provides internal management endpoints (not part of the public Documenten API).
 * All routes require the caller to have the role configured in [AuthenticationConfig.adminRole]
 * (default: `dmf-admin`).
 *
 * Endpoints:
 * - /settings/application-settings — manage application credential configurations
 * - /settings/storage-repositories — manage blob storage repositories
 * - /settings/oidc-providers — manage OIDC provider configurations
 * - /settings/dmf-settings — manage DMF settings
 * - /settings/zgw-api-settings — manage ZGW API settings
 */
fun Route.settingsRoutes(requireRoleCheck: Boolean = true) {
    val requiredRole = AuthenticationConfig.adminRole

    route("/settings") {
        // Role check: every request to /settings/** must carry the admin role.
        if (requireRoleCheck) {
            val roleCheckPlugin = createRouteScopedPlugin("SettingsRoleCheck") {
                on(AuthenticationChecked) { call ->
                    // If there is no authenticated principal, the auth challenge will
                    // handle the 401 response — do not override it with a 403 here.
                    if (call.principal<JWTPrincipal>() == null) return@on

                    val roles = call.jwtRoles()
                    if (requiredRole !in roles) {
                        throw ForbiddenException(
                            "Access denied: role '$requiredRole' is required for settings endpoints.",
                        )
                    }
                }
            }
            install(roleCheckPlugin)
        }

        applicationSettingsRoutes()
        dmfSettingsRoutes()
        zgwApiSettingsRoutes()
        blobStorageRepositorySettingsRoutes()
        oidcProviderSettingsRoutes()
    }
}

fun Application.settingsModule(useAuthentication: Boolean = true) {
    routing {
        if (useAuthentication) {
            authenticate("auth-jwt", "auth-zgw", strategy = AuthenticationStrategy.FirstSuccessful) {
                settingsRoutes(requireRoleCheck = true)
            }
        } else {
            settingsRoutes(requireRoleCheck = false)
        }
    }
}
