// SPDX-License-Identifier: EUPL-1.2
// Copyright (C) 2025-2026 Gemeente Utrecht
package com.baseflow.config

import com.auth0.jwk.JwkProviderBuilder
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
                logger.info("JWT token received - subject: {}, issuer: {}, claims: {}", token.subject, token.issuer, token.claims.keys)
                if (credential.payload.getClaim("username").asString() != "") {
                    JWTPrincipal(credential.payload)
                } else {
                    null
                }
            }

            challenge { _, _ ->
                call.respondText(
                    text = "Unauthorized",
                    status = HttpStatusCode.Unauthorized
                )
            }
        }
    }
}