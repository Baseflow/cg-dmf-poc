// SPDX-License-Identifier: EUPL-1.2
// Copyright (C) 2025-2026 Gemeente Utrecht
package com.baseflow.config

import com.auth0.jwk.JwkProviderBuilder
import com.auth0.jwt.JWT
import com.auth0.jwt.interfaces.JWTVerifier
import io.ktor.http.HttpStatusCode
import io.ktor.http.auth.parseAuthorizationHeader
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.response.respondText
import org.slf4j.LoggerFactory
import java.net.URI
import java.util.concurrent.TimeUnit

fun Application.authenticationModule() {
    val logger = LoggerFactory.getLogger("AuthenticationModule")
    val issuer = AuthenticationConfig.issuer
    val zgwAllowedClientIds = AuthenticationConfig.zgwAllowedClientIds

    // Configure JWK provider to fetch signing keys from Keycloak which are served at issuer's certs endpoint
    val jwkProvider = JwkProviderBuilder(URI("$issuer/protocol/openid-connect/certs").toURL())
        .cached(10, 24, TimeUnit.HOURS)
        .rateLimited(10, 1, TimeUnit.MINUTES)
        .build()

    install(Authentication) {
        jwt("auth-jwt") {
            authHeader { call ->
                val header = call.request.headers["Authorization"]
                logger.info("Raw Authorization header: {}", header)
                header?.let { parseAuthorizationHeader(it) }
            }

            verifier(jwkProvider, issuer) {
                acceptLeeway(3)
            }

            validate { credential ->
                val token = credential.payload
                logger.info(
                    "JWT token received - subject: {}, issuer: {}, claims: {}",
                    token.subject,
                    token.issuer,
                    token.claims.keys,
                )
                if (credential.payload.getClaim("username").asString() != "" ||
                    credential.payload.getClaim("user_id").asString() != ""
                ) {
                    JWTPrincipal(credential.payload)
                } else {
                    null
                }
            }

            challenge { _, _ ->
                call.respondText(
                    text = "Unauthorized",
                    status = HttpStatusCode.Unauthorized,
                )
            }
        }

        // ZGW-style JWT authentication (used by GZAC/Valtimo, Open Zaak, etc.)
        // These tokens are HS256-signed but we don't have access to the shared secret,
        // so we skip signature verification and only validate the client_id claim.
        jwt("auth-zgw") {
            authHeader { call ->
                val header = call.request.headers["Authorization"]
                logger.info("[ZGW] Raw Authorization header: {}", header)
                header?.let { parseAuthorizationHeader(it) }
            }

            verifier(
                object : JWTVerifier {
                    override fun verify(token: String): com.auth0.jwt.interfaces.DecodedJWT = JWT.decode(token)
                    override fun verify(jwt: com.auth0.jwt.interfaces.DecodedJWT): com.auth0.jwt.interfaces.DecodedJWT = jwt
                },
            )

            validate { credential ->
                val token = credential.payload
                val clientId = token.getClaim("client_id").asString()
                logger.info(
                    "[ZGW] JWT token received - issuer: {}, client_id: {}, claims: {}",
                    token.issuer,
                    clientId,
                    token.claims.keys,
                )
                if (clientId in zgwAllowedClientIds) {
                    JWTPrincipal(credential.payload)
                } else {
                    logger.warn("[ZGW] Rejected token with unknown client_id: {}", clientId)
                    null
                }
            }

            challenge { _, _ ->
                call.respondText(
                    text = "Unauthorized",
                    status = HttpStatusCode.Unauthorized,
                )
            }
        }
    }
}
