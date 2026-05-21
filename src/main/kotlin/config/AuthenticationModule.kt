// SPDX-License-Identifier: EUPL-1.2
// Copyright (C) 2025-2026 Gemeente Utrecht
package com.baseflow.config

import com.auth0.jwk.JwkProviderBuilder
import com.auth0.jwt.JWT
import com.auth0.jwt.interfaces.JWTVerifier
import io.ktor.http.HttpStatusCode
import io.ktor.http.auth.parseAuthorizationHeader
import io.ktor.openapi.HttpSecurityScheme
import io.ktor.openapi.OAuth2SecurityScheme
import io.ktor.openapi.OAuthFlow
import io.ktor.openapi.OAuthFlows
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.response.respondText
import io.ktor.server.routing.openapi.registerSecurityScheme
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

    // Register OpenAPI security schemes under the same names as the Ktor auth providers above.
    // Ktor's routing-openapi `+` operator infers per-operation `security` requirements from the
    // `authenticate(...)` route selectors by matching provider names to registered scheme names.
    // This means Swagger UI knows which lock icon to use and sends the Authorization header.
    //
    // auth-jwt → OAuth2 Authorization Code + PKCE: Swagger UI shows an interactive Keycloak login button.
    // auth-zgw → HTTP Bearer (paste-in): Swagger UI shows a plain text box for a ZGW/GZAC token.
    registerSecurityScheme(
        providerName = "auth-jwt",
        securityScheme = OAuth2SecurityScheme(
            description = "OIDC login via Keycloak (Authorization Code + PKCE). " +
                "Klik 'Authorize', log in met uw Keycloak-account en het token wordt automatisch gebruikt.",
            flows = OAuthFlows(
                authorizationCode = OAuthFlow(
                    authorizationUrl = "$issuer/protocol/openid-connect/auth",
                    tokenUrl = "$issuer/protocol/openid-connect/token",
                    refreshUrl = "$issuer/protocol/openid-connect/token",
                    scopes = mapOf(
                        "openid" to "OpenID Connect scope",
                        "profile" to "Profiel informatie",
                        "email" to "E-mailadres",
                    ),
                ),
            ),
        ),
    )
    // <!-- FIXME unsafe -->
    // The bypass token is accepted by this same provider: type the literal value `bypass`
    // in the Swagger UI bearer box — it will be sent as `Authorization: Bearer bypass` and
    // the auth-zgw handler will accept it without any JWT validation.
    // FOR DEVELOPMENT / TESTING ONLY — never use this in production.
    registerSecurityScheme(
        providerName = "auth-zgw",
        securityScheme = HttpSecurityScheme(
            scheme = "bearer",
            bearerFormat = "JWT",
            description = "ZGW-stijl HS256 JWT (GZAC/OpenZaak/Valtimo). " +
                "Plak een token gegenereerd via de ZGW token-tool. " +
                "Het token wordt niet op handtekening gecontroleerd; alleen client_id wordt gevalideerd.\n\n" +
                "⚠️ UNSAFE BYPASS: typ de letterlijke waarde `bypass` om alle JWT-validatie over te slaan. " +
                "Uitsluitend bedoeld voor lokale ontwikkeling en testen. NOOIT gebruiken in productie.",
        ),
    )
}
