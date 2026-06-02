// SPDX-License-Identifier: EUPL-1.2
// Copyright (C) 2025-2026 Gemeente Utrecht
package com.baseflow.config

import com.auth0.jwk.JwkProviderBuilder
import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.auth0.jwt.exceptions.JWTVerificationException
import com.auth0.jwt.interfaces.JWTVerifier
import com.baseflow.services.ApplicationCredentialRegistrar
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

    install(Authentication) {
        jwt("auth-jwt") {
            authHeader { call ->
                val header = call.request.headers["Authorization"]
                header?.let { parseAuthorizationHeader(it) }
            }

            // Configure JWK provider to fetch signing keys from Keycloak.
            val jwkProvider = JwkProviderBuilder(URI("$issuer/protocol/openid-connect/certs").toURL())
                .cached(10, 24, TimeUnit.HOURS)
                .rateLimited(10, 1, TimeUnit.MINUTES)
                .build()

            // RS256 verification via Keycloak's JWK endpoint (production default).
            logger.info("[JWT] Using JWK/RS256 verification for auth-jwt (issuer={})", issuer)
            verifier(jwkProvider, issuer) {
                acceptLeeway(3)
            }

            validate { credential ->
                val token = credential.payload
                if (!token.getClaim("preferred_username").asString().isNullOrBlank() ||
                    !token.getClaim("user_id").asString().isNullOrBlank()
                ) {
                    JWTPrincipal(token)
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

        // Application credential JWT authentication (used by GZAC/Valtimo, Open Zaak, etc.)
        // Tokens are HS256-signed with a per-client secret.  Configure via
        // CLIENT_CREDENTIALS=client_id:secret,...  (see AuthenticationConfig).
        // They can also be added/changed from the admin portal.
        jwt("auth-api-key") {
            authHeader { call ->
                val header = call.request.headers["Authorization"]
                header?.let { parseAuthorizationHeader(it) }
            }

            verifier(
                object : JWTVerifier {
                    override fun verify(token: String): com.auth0.jwt.interfaces.DecodedJWT {
                        val decoded = JWT.decode(token)
                        val clientId = decoded.getClaim("client_id").asString()

                        // Reject tokens that are not ZGW-style (no client_id claim).
                        // This prevents Keycloak tokens with a bad signature from falling
                        // through FirstSuccessful and being accepted by this provider.
                        if (clientId.isNullOrBlank()) {
                            throw JWTVerificationException("Not a ZGW token: missing or blank client_id claim")
                        }

                        val secret = ApplicationCredentialRegistrar.getSecret(clientId)
                        return if (secret == null) {
                            logger.debug(
                                "[ZGW] Unknown client '{}' — rejecting token because signature verification cannot be performed",
                                clientId,
                            )
                            throw JWTVerificationException("No secret configured for client_id '$clientId'")
                        } else {
                            JWT.require(Algorithm.HMAC256(secret)).build().verify(token)
                        }
                    }

                    override fun verify(jwt: com.auth0.jwt.interfaces.DecodedJWT): com.auth0.jwt.interfaces.DecodedJWT =
                        throw JWTVerificationException("Decoded JWT verification without raw token is not supported")
                },
            )

            validate { credential ->
                val token = credential.payload
                JWTPrincipal(token)
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
    // auth-api-key → HTTP Bearer (paste-in): Swagger UI shows a plain text box for a ZGW/GZAC token.
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
    registerSecurityScheme(
        providerName = "auth-api-key",
        securityScheme = HttpSecurityScheme(
            scheme = "bearer",
            bearerFormat = "JWT",
            description = "ZGW-stijl HS256 JWT (GZAC/OpenZaak/Valtimo). " +
                "Plak een token gegenereerd via de ZGW token-tool. " +
                "Het token wordt geverifieerd met de HS256-handtekening indien een secret geconfigureerd is voor de client_id.",
        ),
    )
}
