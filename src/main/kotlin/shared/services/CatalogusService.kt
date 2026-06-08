// SPDX-License-Identifier: EUPL-1.2
// Copyright (C) 2026 Gemeente Utrecht
package com.baseflow.shared.services

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.baseflow.shared.entities.settings.ApiConnectionSettingEntity
import com.baseflow.shared.entities.settings.ApiConnectionType
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

@Serializable
data class InformatieObjectType(val url: String, val omschrijving: String, val vertrouwelijkheidaanduiding: String)

/**
 * Service for interacting with the Catalogus API and other configured API connections.
 *
 * Credentials are sourced from the api_connection_settings table rather than environment variables.
 * The correct entry is matched by URL prefix and api_type.
 */
open class CatalogusService(private val httpClient: HttpClient = HttpClient(CIO)) {
    private val logger = LoggerFactory.getLogger(CatalogusService::class.java)
    private val json = Json { ignoreUnknownKeys = true }

    @OptIn(ExperimentalTime::class)
    private data class CacheEntry<T>(val value: T, val expiresAt: Instant)

    @OptIn(ExperimentalTime::class)
    private val informatieobjecttypeCache = ConcurrentHashMap<String, CacheEntry<InformatieObjectType>>()

    @OptIn(ExperimentalTime::class)
    private val jsonCache = ConcurrentHashMap<String, CacheEntry<JsonObject>>()

    private val cacheTtl = 5.minutes
    private val connectionCacheTtl = 30.seconds

    private data class ConnectionSnapshot(
        val name: String,
        val baseUrl: String,
        val clientId: String,
        val clientSecret: String?,
        val apiType: String,
        val validationEnabled: Boolean,
        val enabled: Boolean,
    )

    @OptIn(ExperimentalTime::class)
    private val connectionListCache = ConcurrentHashMap<String, CacheEntry<List<ConnectionSnapshot>>>()

    @OptIn(ExperimentalTime::class)
    private fun allConnections(): List<ConnectionSnapshot> {
        val now = Clock.System.now()
        connectionListCache[""]?.let { cached ->
            if (cached.expiresAt > now) return cached.value
            connectionListCache.remove("")
        }
        val fresh = transaction {
            ApiConnectionSettingEntity.all().map { e ->
                ConnectionSnapshot(
                    name = e.name,
                    baseUrl = e.baseUrl,
                    clientId = e.clientId,
                    clientSecret = try {
                        e.clientSecret
                    } catch (_: Exception) {
                        null
                    },
                    apiType = e.apiType,
                    validationEnabled = e.validationEnabled,
                    enabled = e.enabled,
                )
            }
        }
        connectionListCache[""] = CacheEntry(fresh, now + connectionCacheTtl)
        return fresh
    }

    private fun findConnection(url: String, type: ApiConnectionType): ConnectionSnapshot? = allConnections()
        .filter { it.enabled && it.apiType == type.value && url.startsWith(normalizeBaseUrl(it.baseUrl)) }
        .maxByOrNull { it.baseUrl.length }

    private fun findAnyConnection(url: String): ConnectionSnapshot? = allConnections()
        .filter { it.enabled && url.startsWith(normalizeBaseUrl(it.baseUrl)) }
        .maxByOrNull { it.baseUrl.length }

    private fun normalizeBaseUrl(baseUrl: String) = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"

    /**
     * Validates if the given informatieobjecttype URL exists in the Catalogus API.
     *
     * Looks up a ZTC entry in api_connection_settings whose base_url is a prefix of the URL.
     * If no matching ZTC entry exists, or validation_enabled is false on that entry, validation is skipped.
     *
     * @return The InformatieObjectType if found, null if validation is skipped
     * @throws Exception if validation is enabled and the request fails
     */
    @OptIn(ExperimentalTime::class)
    suspend fun validateInformatieobjecttype(url: String): InformatieObjectType? {
        val connection = findConnection(url, ApiConnectionType.ZTC)
        if (connection == null) {
            logger.debug("No ZTC connection found for URL '{}', skipping informatieobjecttype validation", url)
            return null
        }
        if (!connection.validationEnabled) {
            logger.debug("Validation disabled for ZTC connection '{}', skipping validation for: {}", connection.name, url)
            return null
        }

        val now = Clock.System.now()
        informatieobjecttypeCache[url]?.let { cached ->
            if (cached.expiresAt > now) {
                logger.debug("Returning cached informatieobjecttype for: {}", url)
                return cached.value
            } else {
                informatieobjecttypeCache.remove(url)
            }
        }

        val clientSecret = connection.clientSecret ?: run {
            logger.warn("ZTC connection '{}' has no client secret configured, skipping validation", connection.name)
            return null
        }
        val jwtToken = generateJwtToken(connection.clientId, clientSecret)
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
            val result = json.decodeFromString<InformatieObjectType>(body)
            informatieobjecttypeCache[url] = CacheEntry(result, now + cacheTtl)
            return result
        } catch (e: Exception) {
            if (e.message?.contains("Error fetching information object type") == true) {
                throw e
            }
            logger.error("Failed to connect to Catalogus for validation: {}", e.message)
            throw Exception("Failed to connect to Catalogus for validation: ${e.message}", e)
        }
    }

    /**
     * Fetches a URL from a configured API connection and returns the raw JSON response.
     * Matches the connection by URL prefix across all api_type values.
     *
     * @param url The full URL to fetch
     * @return The raw JSON response as a JsonObject
     * @throws IllegalArgumentException if no connection matches the URL prefix
     * @throws Exception if the request fails
     */
    @OptIn(ExperimentalTime::class)
    suspend fun fetchJsonFromUrl(url: String): JsonObject {
        val connection = findAnyConnection(url)
            ?: throw IllegalArgumentException(
                "No API connection found whose base_url is a prefix of '$url'. Check api_connection_settings.",
            )

        val now = Clock.System.now()
        jsonCache[url]?.let { cached ->
            if (cached.expiresAt > now) {
                logger.debug("Returning cached JSON for URL: {}", url)
                return cached.value
            } else {
                jsonCache.remove(url)
            }
        }

        val clientSecret = connection.clientSecret
            ?: throw IllegalStateException(
                "API connection '${connection.name}' has no client secret configured.",
            )
        val jwtToken = generateJwtToken(connection.clientId, clientSecret)
        logger.debug("Fetching JSON from URL: {}", url)

        try {
            val response = httpClient.get(url) {
                headers {
                    append("Authorization", "Bearer $jwtToken")
                }
            }

            if (response.status.value != 200) {
                val errorMessage = """
                    Error fetching resource from API connection '${connection.name}'.
                    Status: ${response.status.value}
                    Endpoint: $url
                    Response: ${response.bodyAsText()}
                """.trimIndent()
                logger.error("Fetch failed: {}", errorMessage)
                throw Exception(errorMessage)
            }

            val body = response.bodyAsText()
            val result = json.decodeFromString<JsonObject>(body)
            jsonCache[url] = CacheEntry(result, now + cacheTtl)
            return result
        } catch (e: Exception) {
            if (e.message?.contains("Error fetching resource") == true) {
                throw e
            }
            logger.error("Failed to fetch URL {}: {}", url, e.message)
            throw Exception("Failed to fetch URL $url: ${e.message}", e)
        }
    }

    fun close() {
        httpClient.close()
    }

    @OptIn(ExperimentalTime::class)
    fun generateJwtToken(clientId: String, clientSecret: String): String {
        val now = Clock.System.now().epochSeconds
        return JWT.create()
            .withIssuer(clientId)
            .withClaim("client_id", clientId)
            .withClaim("user_id", clientId)
            .withClaim("user_representation", clientId)
            .withClaim("iat", now)
            .sign(Algorithm.HMAC256(clientSecret))
    }
}
