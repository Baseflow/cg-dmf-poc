// SPDX-License-Identifier: EUPL-1.2
// Copyright (C) 2026 Gemeente Utrecht
package com.baseflow.services

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.baseflow.config.OpenZaakConfig
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.statement.bodyAsText
import org.slf4j.LoggerFactory
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/**
 * Service for interacting with OpenZaak
 *
 * TODO: We should split this into a service per API type (Catalogus, Zaken, etc.) if more functionality is added.
 */
class OpenZaakService(private val config: OpenZaakConfig, private val httpClient: HttpClient = HttpClient(CIO)) {
    private val logger = LoggerFactory.getLogger(OpenZaakService::class.java)

    /**
     * Validates if the given informatieobjecttype URL exists in OpenZaak
     *
     * @param url The full URL to the informatieobjecttype in OpenZaak
     * @throws Exception if validation fails and validation is enabled in config
     */
    suspend fun validateInformatieobjecttype(url: String) {
        if (!config.validationEnabled) {
            logger.debug("Informatieobjecttype validation is disabled, skipping validation for: {}", url)
            return
        }

        val jwtToken = generateJwtToken()
        logger.debug("Validating informatieobjecttype at endpoint: {}", url)

        try {
            val response = httpClient.get(url) {
                headers {
                    append("Authorization", "Bearer $jwtToken")
                }
            }

            if (response.status.value != 200) {
                val errorMessage = """
                    Error fetching information object type from OpenZaak.
                    Status: ${response.status.value}
                    Endpoint: $url
                    Response: ${response.bodyAsText()}
                """.trimIndent()
                logger.error("OpenZaak validation failed: {}", errorMessage)
                throw Exception(errorMessage)
            }

            logger.debug("Successfully validated informatieobjecttype: {}", url)
        } catch (e: Exception) {
            if (e.message?.contains("Error fetching information object type") == true) {
                throw e
            }
            logger.error("Failed to connect to OpenZaak for validation: {}", e.message)
            throw Exception("Failed to connect to OpenZaak for validation: ${e.message}", e)
        }
    }

    /**
     * Closes the underlying HTTP client
     */
    fun close() {
        httpClient.close()
    }

    /**
     * Generates a JWT token for OpenZaak authentication
     */
    @OptIn(ExperimentalTime::class)
    fun generateJwtToken(): String {
        val now = Clock.System.now().epochSeconds
        return JWT.create()
            .withIssuer(config.clientId) // iss
            .withClaim("client_id", config.clientId)
            .withClaim("user_id", config.clientId)
            .withClaim("user_representation", config.clientId)
            .withClaim("iat", now) // seconds
            .sign(Algorithm.HMAC256(config.clientSecret))
    }
}
