// SPDX-License-Identifier: EUPL-1.2
// Copyright (C) 2026 Gemeente Utrecht

package com.baseflow.api.middleware

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import kotlin.test.*

class ScopeAuthorizationPluginTest {

    private val jwtSecret = "test-secret-key-for-testing-only"
    private val jwtIssuer = "test-issuer"

    private fun generateToken(scopes: String): String {
        return JWT.create()
            .withIssuer(jwtIssuer)
            .withSubject("testuser")
            .withClaim("scope", scopes)
            .sign(Algorithm.HMAC256(jwtSecret))
    }

    private fun ApplicationTestBuilder.setupTestApp() {
        application {
            install(ContentNegotiation) {
                json()
            }

            install(io.ktor.server.plugins.statuspages.StatusPages) {
                exception<ScopeAuthorizationException> { call, cause ->
                    call.respond(
                        HttpStatusCode.Forbidden,
                        mapOf(
                            "error" to "Insufficient permissions",
                            "detail" to "Required scopes: ${cause.requiredScopes.joinToString(", ")}",
                            "code" to "insufficient_scope"
                        )
                    )
                }
            }

            install(Authentication) {
                jwt("auth-jwt") {
                    verifier(
                        JWT.require(Algorithm.HMAC256(jwtSecret))
                            .withIssuer(jwtIssuer)
                            .build()
                    )
                    validate { credential ->
                        if (credential.payload.subject != null) {
                            JWTPrincipal(credential.payload)
                        } else {
                            null
                        }
                    }
                }
            }

            install(ScopeAuthorizationPlugin) {
                scopeClaimName = "scope"
                wildcardEnabled = true
            }

            routing {
                authenticate("auth-jwt") {
                    // Route requiring single scope
                    route("/documents") {
                        requiredScope("documenten:read")
                        get {
                            call.respond(HttpStatusCode.OK, mapOf("message" to "success"))
                        }
                    }

                    // Route requiring multiple scopes
                    route("/documents/{id}") {
                        requiredScope("documenten:write", "documenten:admin")
                        delete {
                            call.respond(HttpStatusCode.NoContent)
                        }
                    }

                    // Route without scope requirement
                    get("/public") {
                        call.respond(HttpStatusCode.OK, mapOf("message" to "public"))
                    }
                }
            }
        }
    }

    @Test
    fun testAccessWithCorrectScope() = testApplication {
        setupTestApp()

        val token = generateToken("documenten:read")

        val response = client.get("/documents") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }

        assertEquals(HttpStatusCode.OK, response.status)
    }

    @Test
    fun testAccessWithoutRequiredScope() = testApplication {
        setupTestApp()

        val token = generateToken("other:scope")

        val response = client.get("/documents") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }

        assertEquals(HttpStatusCode.Forbidden, response.status)
        assertTrue(response.bodyAsText().contains("Insufficient permissions"))
    }

    @Test
    fun testAccessWithMultipleScopes() = testApplication {
        setupTestApp()

        val token = generateToken("documenten:write documenten:admin")

        val response = client.delete("/documents/123") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }

        assertEquals(HttpStatusCode.NoContent, response.status)
    }

    @Test
    fun testAccessWithMissingOneOfMultipleScopes() = testApplication {
        setupTestApp()

        // Has documenten:write but missing documenten:admin
        val token = generateToken("documenten:write")

        val response = client.delete("/documents/123") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }

        assertEquals(HttpStatusCode.Forbidden, response.status)
    }

    @Test
    fun testAccessPublicRouteWithAnyScope() = testApplication {
        setupTestApp()

        val token = generateToken("any:scope")

        val response = client.get("/public") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }

        assertEquals(HttpStatusCode.OK, response.status)
    }

    @Test
    fun testWildcardScopeMatching() = testApplication {
        setupTestApp()

        // User has wildcard scope
        val token = generateToken("documenten:*")

        val response = client.get("/documents") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }

        assertEquals(HttpStatusCode.OK, response.status)
    }

    @Test
    fun testScopeAsArray() = testApplication {
        application {
            install(ContentNegotiation) {
                json()
            }

            install(Authentication) {
                jwt("auth-jwt") {
                    verifier(
                        JWT.require(Algorithm.HMAC256(jwtSecret))
                            .withIssuer(jwtIssuer)
                            .build()
                    )
                    validate { credential ->
                        if (credential.payload.subject != null) {
                            JWTPrincipal(credential.payload)
                        } else {
                            null
                        }
                    }
                }
            }

            install(ScopeAuthorizationPlugin) {
                scopeClaimName = "scope"
            }

            routing {
                authenticate("auth-jwt") {
                    route("/documents") {
                        requiredScope("documenten:read")
                        get {
                            call.respond(HttpStatusCode.OK, mapOf("message" to "success"))
                        }
                    }
                }
            }
        }

        // Create token with array-style scopes
        val token = JWT.create()
            .withIssuer(jwtIssuer)
            .withSubject("testuser")
            .withArrayClaim("scope", arrayOf("documenten:read", "documenten:write"))
            .sign(Algorithm.HMAC256(jwtSecret))

        val response = client.get("/documents") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }

        assertEquals(HttpStatusCode.OK, response.status)
    }

    @Test
    fun testAccessWithoutToken() = testApplication {
        setupTestApp()

        val response = client.get("/documents")

        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }
}
