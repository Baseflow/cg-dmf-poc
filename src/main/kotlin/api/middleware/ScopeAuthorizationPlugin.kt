// SPDX-License-Identifier: EUPL-1.2
// Copyright (C) 2026 Gemeente Utrecht

package com.baseflow.api.middleware

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.util.*
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("ScopeAuthorizationPlugin")

/**
 * Configuration for scope authorization.
 */
class ScopeAuthorizationConfig {
    /**
     * The name of the JWT claim containing scopes. Default is "scope" (OAuth2 standard).
     */
    var scopeClaimName: String = "scope"

    /**
     * Whether to enable wildcard scope matching (e.g., "documenten:*" matches "documenten:read").
     * Default is true.
     */
    var wildcardEnabled: Boolean = true
}

/**
 * Attribute key for storing the plugin configuration.
 */
val ScopeAuthorizationConfigKey = AttributeKey<ScopeAuthorizationConfig>("ScopeAuthorizationConfig")

/**
 * Attribute key for storing required scopes on route attributes.
 */
val RouteScopeKey = AttributeKey<Set<String>>("RouteRequiredScopes")

/**
 * Application plugin to configure scope authorization settings.
 * Install this plugin to configure global settings like scope claim name and wildcard matching.
 */
val ScopeAuthorizationPlugin = createApplicationPlugin(
    name = "ScopeAuthorizationPlugin",
    createConfiguration = ::ScopeAuthorizationConfig
) {
    application.attributes.put(ScopeAuthorizationConfigKey, pluginConfig)
}

/**
 * Route-scoped plugin that checks JWT scopes against required scopes.
 * This plugin is automatically installed when you call requiredScope().
 */
private class ScopeCheckPluginConfig {
    var scopes: Set<String> = emptySet()
}

private val ScopeCheckPlugin = createRouteScopedPlugin(
    name = "ScopeCheckPlugin",
    createConfiguration = ::ScopeCheckPluginConfig
) {
    val requiredScopes = pluginConfig.scopes
    val config = application.attributes.getOrNull(ScopeAuthorizationConfigKey)
        ?: ScopeAuthorizationConfig()

    on(AuthenticationChecked) { call ->
        if (requiredScopes.isEmpty()) {
            return@on
        }

        val principal = call.principal<JWTPrincipal>()

        // If no principal, skip (authentication should handle this)
        if (principal == null) {
            logger.debug("No JWT principal found, skipping scope check")
            return@on
        }

        // Extract scopes from JWT token
        val userScopes = extractScopes(principal, config)
        logger.debug("User scopes: {}", userScopes)
        logger.debug("Route requires scopes: {}", requiredScopes)

        // Check if user has all required scopes
        val hasAllScopes = requiredScopes.all { required ->
            userScopes.any { userScope ->
                matchScope(userScope, required, config.wildcardEnabled)
            }
        }

        if (!hasAllScopes) {
            val missingScopes = requiredScopes.filter { required ->
                !userScopes.any { userScope ->
                    matchScope(userScope, required, config.wildcardEnabled)
                }
            }
            logger.warn(
                "Access denied. User missing scopes: {}. User has: {}",
                missingScopes,
                userScopes
            )
            throw ScopeAuthorizationException(
                requiredScopes = requiredScopes,
                userScopes = userScopes,
                missingScopes = missingScopes
            )
        } else {
            logger.debug("Scope check passed")
        }
    }
}

/**
 * Extract scopes from JWT token.
 * Supports both "scope" (space-separated string) and "scopes" (array) claims.
 */
private fun extractScopes(principal: JWTPrincipal, config: ScopeAuthorizationConfig): Set<String> {
    val scopes = mutableSetOf<String>()

    // Try "scope" claim (space-separated string, OAuth2 standard)
    principal.payload.getClaim(config.scopeClaimName)?.let { claim ->
        if (!claim.isNull) {
            when {
                claim.asString() != null -> {
                    scopes.addAll(claim.asString().split(" ").filter { it.isNotBlank() })
                }
                claim.asList(String::class.java) != null -> {
                    scopes.addAll(claim.asList(String::class.java))
                }
            }
        }
    }

    // Try alternative "scopes" claim (array)
    if (config.scopeClaimName != "scopes") {
        principal.payload.getClaim("scopes")?.let { claim ->
            if (!claim.isNull) {
                claim.asList(String::class.java)?.let { scopes.addAll(it) }
            }
        }
    }

    return scopes
}

/**
 * Match a user scope against a required scope.
 * Supports wildcard matching if enabled (e.g., "documenten:*" matches "documenten:read").
 */
private fun matchScope(userScope: String, requiredScope: String, wildcardEnabled: Boolean): Boolean {
    if (userScope == requiredScope) return true

    if (!wildcardEnabled) return false

    // Wildcard matching: "documenten:*" matches "documenten:read"
    if (userScope.endsWith(":*")) {
        val prefix = userScope.removeSuffix(":*")
        return requiredScope.startsWith("$prefix:")
    }

    return false
}

/**
 * Exception thrown when a user lacks required scopes.
 */
class ScopeAuthorizationException(
    val requiredScopes: Set<String>,
    val userScopes: Set<String>,
    val missingScopes: List<String>
) : Exception("Access denied. Required scopes: ${requiredScopes.joinToString(", ")}")

/**
 * Extension function to set required scopes on a route.
 * Use this inside a route block to specify what scopes are needed.
 *
 * Usage:
 * ```
 * route("/documents") {
 *     requiredScope("documenten:read")
 *     get { ... }
 * }
 * ```
 *
 * @param scopes One or more required scopes (AND logic - user must have ALL)
 */
fun Route.requiredScope(vararg scopes: String) {
    if (scopes.isNotEmpty()) {
        // Store scopes on the route for reference
        attributes.put(RouteScopeKey, scopes.toSet())

        // Install the scope check plugin on this route with the required scopes
        install(ScopeCheckPlugin) {
            this.scopes = scopes.toSet()
        }
    }
}
