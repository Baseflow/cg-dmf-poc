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
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.koin.core.annotation.Singleton
import org.slf4j.LoggerFactory
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

@Serializable
data class InformatieObjectType(val url: String, val omschrijving: String, val vertrouwelijkheidaanduiding: String)

/**
 * Service for interacting with the Catalogus API
 *
 * TODO: We should split this into a service per API type (Catalogus, Zaken, etc.) if more functionality is added.
 */
@Singleton
open class CatalogusService(private val config: OpenZaakConfig, private val httpClient: HttpClient = HttpClient(CIO)) {
    private val logger = LoggerFactory.getLogger(CatalogusService::class.java)
    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Validates if the given informatieobjecttype URL exists in the Catalogus API
     *
     * @param url The full URL to the informatieobjecttype in the Catalogus
     * @return The InformatieObjectType if found, null otherwise
     * @throws Exception if validation fails and validation is enabled in config
     */
    suspend fun validateInformatieobjecttype(url: String): InformatieObjectType? {
        if (!config.validationEnabled) {
            logger.debug("Informatieobjecttype validation is disabled, skipping validation for: {}", url)
            return null
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
                    Error fetching information object type from Catalogus.
                    Status: ${response.status.value}
                    Endpoint: $url
                    Response: ${response.bodyAsText()}
                """.trimIndent()
                logger.error("Catalogus validation failed: {}", errorMessage)
                throw Exception(errorMessage)
            }

            val body = response.bodyAsText()
            logger.debug("Successfully validated informatieobjecttype: {}", url)
            return json.decodeFromString<InformatieObjectType>(body)
        } catch (e: Exception) {
            if (e.message?.contains("Error fetching information object type") == true) {
                throw e
            }
            logger.error("Failed to connect to Catalogus for validation: {}", e.message)
            throw Exception("Failed to connect to Catalogus for validation: ${e.message}", e)
        }
    }

    /**
     * Fetches a URL that starts with the configured OpenZaak endpoint and returns the raw JSON response.
     * Uses JWT authentication with the configured OPENZAAK_CLIENT_ID and OPENZAAK_CLIENT_SECRET.
     *
     * @param url The full URL to fetch, must start with the configured OpenZaak endpoint
     * @return The raw JSON response as a JsonObject
     * @throws IllegalArgumentException if the URL does not start with the configured endpoint
     * @throws Exception if the request fails
     */
    suspend fun fetchJsonFromUrl(url: String): JsonObject {
        val endpoint = if (config.endpoint.endsWith("/")) config.endpoint else "${config.endpoint}/"
        require(url.startsWith(endpoint)) {
            "URL must start with the configured OpenZaak endpoint: ${config.endpoint}"
        }

        val jwtToken = generateJwtToken()
        logger.debug("Fetching JSON from URL: {}", url)

        try {
            val response = httpClient.get(url) {
                headers {
                    append("Authorization", "Bearer $jwtToken")
                }
            }

            if (response.status.value != 200) {
                val errorMessage = """
                    Error fetching resource from OpenZaak.
                    Status: ${response.status.value}
                    Endpoint: $url
                    Response: ${response.bodyAsText()}
                """.trimIndent()
                logger.error("Fetch failed: {}", errorMessage)
                throw Exception(errorMessage)
            }

            val body = response.bodyAsText()
            return json.decodeFromString<JsonObject>(body)
        } catch (e: Exception) {
            if (e.message?.contains("Error fetching resource") == true) {
                throw e
            }
            logger.error("Failed to fetch URL {}: {}", url, e.message)
            throw Exception("Failed to fetch URL $url: ${e.message}", e)
        }
    }

    /**
     * Closes the underlying HTTP client
     */
    fun close() {
        httpClient.close()
    }

    /**
     * Generates a JWT token for Catalogus authentication
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
