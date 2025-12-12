// SPDX-License-Identifier: EUPL-1.2
// Copyright (C) 2025 Gemeente Utrecht
package com.baseflow.config

import com.auth0.jwk.JwkProviderBuilder
import com.baseflow.api.models.forbiddenJwtExpired
import com.baseflow.api.models.unauthorizedJwtInvalid
import com.baseflow.api.models.unauthorizedJwtInvalidAudience
import com.baseflow.api.models.unauthorizedJwtInvalidIssuer
import com.baseflow.api.models.unauthorizedJwtInvalidSignature
import com.baseflow.api.models.unauthorizedJwtMalformed
import com.baseflow.api.models.unauthorizedJwtMissing
import com.baseflow.api.models.respondProblem
import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.auth0.jwt.exceptions.*
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import java.net.URI
import java.security.interfaces.RSAPublicKey
import java.util.UUID
import java.util.concurrent.TimeUnit

fun Application.authenticationModule() {

    val issuer = System.getenv("OIDC_ISSUER") ?: "http://localhost:8081/realms/cg-dmf"

    // Configure JWK provider to fetch signing keys from Keycloak which are served at issuer's certs endpoint
    val jwkProvider = JwkProviderBuilder(URI("$issuer/protocol/openid-connect/certs").toURL())
        .cached(10, 24, TimeUnit.HOURS)
        .rateLimited(10, 1, TimeUnit.MINUTES)
        .build()

    install(Authentication) {
        jwt("auth-jwt") {

            verifier(jwkProvider, issuer) {
                acceptLeeway(3)
            }

            validate { credential ->
                if (credential.payload.getClaim("username").asString() != "") {
                    JWTPrincipal(credential.payload)
                } else {
                    null
                }
            }

            challenge { _, _ ->
                val instance = "urn:uuid:${'$'}{UUID.randomUUID()}"

                val authHeader = call.request.headers["Authorization"]?.trim()
                if (authHeader.isNullOrBlank()) {
                    call.respondProblem(HttpStatusCode.Unauthorized, unauthorizedJwtMissing(instance))
                    return@challenge
                }

                val bearerPrefix = "Bearer "
                if (!authHeader.startsWith(bearerPrefix, ignoreCase = true)) {
                    call.respondProblem(HttpStatusCode.Unauthorized, unauthorizedJwtMalformed(instance))
                    return@challenge
                }

                val token = authHeader.substringAfter(bearerPrefix).trim()
                val decoded = try {
                    JWT.decode(token)
                } catch (e: JWTDecodeException) {
                    call.respondProblem(HttpStatusCode.Unauthorized, unauthorizedJwtMalformed(instance))
                    return@challenge
                }

                // Try to verify using the same issuer and JWK provider to classify failures
                try {
                    val kid = decoded.keyId
                    val jwk = jwkProvider.get(kid)
                    val publicKey = jwk.publicKey as? RSAPublicKey
                        ?: throw AlgorithmMismatchException("Unsupported key type")
                    val algorithm = Algorithm.RSA256(publicKey, null)
                    val verifier = JWT.require(algorithm)
                        .withIssuer(issuer)
                        .acceptLeeway(3)
                        .build()
                    verifier.verify(token)

                    // If verification succeeds but Ktor still challenged, fallback to generic invalid
                    call.respondProblem(HttpStatusCode.Unauthorized, unauthorizedJwtInvalid(instance))
                } catch (e: TokenExpiredException) {
                    call.respondProblem(HttpStatusCode.Forbidden, forbiddenJwtExpired(instance))
                } catch (e: InvalidClaimException) {
                    // Attempt to distinguish common invalid claims via message heuristics
                    val msg = (e.message ?: "").lowercase()
                    when {
                        "iss" in msg || "issuer" in msg -> call.respondProblem(HttpStatusCode.Unauthorized, unauthorizedJwtInvalidIssuer(instance))
                        "aud" in msg || "audience" in msg -> call.respondProblem(HttpStatusCode.Unauthorized, unauthorizedJwtInvalidAudience(instance))
                        "nbf" in msg || "not before" in msg -> call.respondProblem(HttpStatusCode.Unauthorized, unauthorizedJwtMalformed(instance))
                        else -> call.respondProblem(HttpStatusCode.Unauthorized, unauthorizedJwtInvalid(instance))
                    }
                } catch (e: AlgorithmMismatchException) {
                    call.respondProblem(HttpStatusCode.Unauthorized, unauthorizedJwtInvalidSignature(instance))
                } catch (e: SignatureVerificationException) {
                    call.respondProblem(HttpStatusCode.Unauthorized, unauthorizedJwtInvalidSignature(instance))
                } catch (e: JWTVerificationException) {
                    call.respondProblem(HttpStatusCode.Unauthorized, unauthorizedJwtInvalid(instance))
                } catch (e: Exception) {
                    // Fallback (do not leak internals)
                    call.respondProblem(HttpStatusCode.Unauthorized, unauthorizedJwtInvalid(instance))
                }
            }
        }
    }
}
