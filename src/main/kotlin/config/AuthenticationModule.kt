// SPDX-License-Identifier: EUPL-1.2
// Copyright (C) 2025 Gemeente Utrecht
package com.baseflow.config

import com.auth0.jwk.JwkProviderBuilder
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.response.respondText
import java.net.URL
import java.util.concurrent.TimeUnit

fun Application.authenticationModule() {

    val issuer = System.getenv("OIDC_ISSUER") ?: "http://localhost:8080/auth/realms/cg-dmf"

    // Configure JWK provider to fetch signing keys from Keycloak which are served at issuer's certs endpoint
    val jwkProvider = JwkProviderBuilder(URL("$issuer/protocol/openid-connect/certs"))
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
                call.respondText(
                    text = "Unauthorized",
                    status = HttpStatusCode.Unauthorized
                )
            }
        }
    }
}
