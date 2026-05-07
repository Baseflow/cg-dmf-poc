// SPDX-License-Identifier: EUPL-1.2
// Copyright (C) 2026 Gemeente Utrecht

package com.baseflow.api.routes

import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
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
     * Whether to enable wildcard scope matching (e.g., "documenten.*" matches "documenten.lezen").
     * Default is true.
     */
    var wildcardEnabled: Boolean = true
}

fun ApplicationCall.checkScope(
    vararg requiredScopes: String,
) {
    checkScope(requiredScopes.toSet())
}

fun ApplicationCall.checkScope(
    requiredScopes: Set<String>,
    config: ScopeAuthorizationConfig = ScopeAuthorizationConfig()
) {
    if (requiredScopes.isEmpty()) {
        return
    }

    val principal = principal<JWTPrincipal>()

    // If no principal, treat as unauthorized when scopes are required
    if (principal == null) {
        logger.warn("No JWT principal found while checking required scopes {}, denying access", requiredScopes)
        throw ScopeAuthorizationException(
            requiredScopes = requiredScopes
        )
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
            requiredScopes = requiredScopes
        )
    } else {
        logger.debug("Scope check passed")
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
 * Supports wildcard matching if enabled (e.g., "documenten.*" matches "documenten.lezen").
 */
private fun matchScope(userScope: String, requiredScope: String, wildcardEnabled: Boolean): Boolean {
    if (userScope == requiredScope) return true

    if (!wildcardEnabled) return false

    // Wildcard matching: "documenten.*" matches "documenten.lezen"
    if (userScope.endsWith(".*")) {
        val prefix = userScope.removeSuffix(".*")
        return requiredScope.startsWith("$prefix.")
    }

    return false
}

/**
 * Exception thrown when a user lacks required scopes.
 */
class ScopeAuthorizationException(
    val requiredScopes: Set<String>
) : Exception("Access denied. Required scopes: ${requiredScopes.joinToString(", ")}")
