// SPDX-License-Identifier: EUPL-1.2
// Copyright (C) 2026 Gemeente Utrecht
package com.baseflow.config

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import java.util.Date

/**
 * Generates short-lived HS256 JWT tokens for authenticating against ZGW APIs
 * such as the Open Notificaties API.
 *
 * The token structure mirrors the ZGW JWT convention used by open-zaak / open-notificaties:
 * ```
 * {
 *   "iss":                  <clientId>,
 *   "iat":                  <unix timestamp>,
 *   "client_id":            <clientId>,
 *   "user_id":              "dmf-drc",
 *   "user_representation":  "dmf-drc"
 * }
 * ```
 * The token is signed with the client secret using the HS256 algorithm.
 */
object JwtTokenProvider {

    private const val USER_ID = "dmf-drc"

    /**
     * Generates a signed JWT bearer token for the given [clientId] and [clientSecret].
     *
     * @param clientId  The client identifier registered in the target ZGW service.
     * @param clientSecret  The shared secret used to sign the token (HS256).
     * @return A compact, signed JWT string suitable for use in an `Authorization: Bearer` header.
     */
    fun generate(clientId: String, clientSecret: String): String {
        val now = Date()
        val algorithm = Algorithm.HMAC256(clientSecret)

        return JWT.create()
            .withIssuer(clientId)
            .withIssuedAt(now)
            .withClaim("client_id", clientId)
            .withClaim("user_id", USER_ID)
            .withClaim("user_representation", USER_ID)
            .sign(algorithm)
    }
}
