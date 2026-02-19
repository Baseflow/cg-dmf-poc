// SPDX-License-Identifier: EUPL-1.2
// Copyright (C) 2026 Gemeente Utrecht

package com.baseflow.api.middleware

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.response.*
import io.ktor.server.routing.Route
import io.ktor.server.routing.RouteSelector
import io.ktor.server.routing.RouteSelectorEvaluation
import io.ktor.server.routing.RoutingResolveContext
import io.ktor.server.routing.intercept
import io.ktor.util.*
import org.slf4j.LoggerFactory

/**
 * Plugin to enforce scope-based authorization on routes.
 *
 * This plugin intercepts requests and checks if the authenticated user
 * has the required scopes specified via the @RequireScope annotation.
 */
class ScopeAuthorizationPlugin {
    companion object Plugin : BaseApplicationPlugin<Application, Configuration, ScopeAuthorizationPlugin> {
        override val key = AttributeKey<ScopeAuthorizationPlugin>("ScopeAuthorization")
        private val logger = LoggerFactory.getLogger(ScopeAuthorizationPlugin::class.java)

        override fun install(
            pipeline: Application,
            configure: Configuration.() -> Unit
        ): ScopeAuthorizationPlugin {
            val configuration = Configuration().apply(configure)
            val plugin = ScopeAuthorizationPlugin()

            // Store configuration in application attributes for access by route interceptors
            pipeline.attributes.put(ConfigKey, configuration)

            return plugin
        }

        internal val ConfigKey = AttributeKey<Configuration>("ScopeAuthorizationConfig")

        /**
         * Check scopes for a call. This is called from the route interceptor.
         */
        internal fun checkScopes(call: ApplicationCall, configuration: Configuration) {
            val principal = call.principal<JWTPrincipal>()

            // If no principal, skip (authentication should handle this)
            if (principal == null) {
                logger.debug("No JWT principal found, skipping scope check")
                return
            }

            // Extract scopes from JWT token
            val userScopes = extractScopes(principal, configuration)
            logger.debug("User scopes: {}", userScopes)

            // Get required scopes from route attributes
            val requiredScopes = call.attributes.getOrNull(ScopeKey)

            if (requiredScopes != null && requiredScopes.isNotEmpty()) {
                logger.debug("Route requires scopes: {}", requiredScopes)

                // Check if user has all required scopes
                val hasAllScopes = requiredScopes.all { required ->
                    userScopes.any { userScope ->
                        matchScope(userScope, required, configuration.wildcardEnabled)
                    }
                }

                if (!hasAllScopes) {
                    val missingScopes = requiredScopes.filter { required ->
                        !userScopes.any { userScope ->
                            matchScope(userScope, required, configuration.wildcardEnabled)
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
                }

                logger.debug("Scope check passed")
            }
        }

        /**
         * Extract scopes from JWT token.
         * Supports both "scope" (space-separated string) and "scopes" (array) claims.
         */
        private fun extractScopes(principal: JWTPrincipal, config: Configuration): Set<String> {
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

            // Wildcard matching: "documenten.*" matches "documenten.read"
            if (userScope.endsWith(":*")) {
                val prefix = userScope.removeSuffix(":*")
                return requiredScope.startsWith("$prefix:")
            }

            return false
        }
    }

    class Configuration {
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
}

/**
 * Attribute key for storing required scopes in route attributes.
 */
val ScopeKey = AttributeKey<Set<String>>("RequiredScopes")

/**
 * Exception thrown when a user lacks required scopes.
 */
class ScopeAuthorizationException(
    val requiredScopes: Set<String>,
    val userScopes: Set<String>,
    val missingScopes: List<String>
) : Exception("Access denied. Required scopes: ${requiredScopes.joinToString(", ")}")

/**
 * Extension function to add required scopes to a route.
 *
 * Usage:
 * ```
 * route("/documents") {
 *     withScopes("documenten:read") {
 *         get { ... }
 *     }
 * }
 * ```
 */
fun Route.withScopes(vararg scopes: String, build: Route.() -> Unit): Route {
    // Create a child route with a custom selector
    val scopedRoute = createChild(object : RouteSelector() {
        override suspend fun evaluate(context: RoutingResolveContext, segmentIndex: Int) = RouteSelectorEvaluation.Constant
        override fun toString(): String = "(scopes: ${scopes.joinToString(", ")})"
    })

    // Intercept at Call phase to set the scope attribute and check scopes
    scopedRoute.intercept(ApplicationCallPipeline.Call) {
        // Set the required scopes
        call.attributes.put(ScopeKey, scopes.toSet())

        // Get the configuration and check scopes
        val config = call.application.attributes.getOrNull(ScopeAuthorizationPlugin.ConfigKey)
            ?: ScopeAuthorizationPlugin.Configuration() // Use default if not configured

        try {
            ScopeAuthorizationPlugin.checkScopes(call, config)
        } catch (e: ScopeAuthorizationException) {
            call.respond(
                HttpStatusCode.Forbidden,
                mapOf(
                    "error" to "Insufficient permissions",
                    "detail" to "Required scopes: ${e.requiredScopes.joinToString(", ")}",
                    "code" to "insufficient_scope"
                )
            )
            finish()
        }
    }

    scopedRoute.build()
    return scopedRoute
}
