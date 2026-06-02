// SPDX-License-Identifier: EUPL-1.2
// Copyright (C) 2026 Gemeente Utrecht
package com.baseflow.services

import com.baseflow.config.AuthenticationConfig
import com.baseflow.entities.settings.ApplicationSettingEntity
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap

/**
 * Manages ZGW client secrets cache for JWT signature verification.
 *
 * On startup, loads secrets from both [AuthenticationConfig.clientCredentials] (environment variables)
 * and the [ApplicationSettingEntity] database table, combining them into a single in-memory cache.
 *
 * Call [initialise] once during application startup (after Flyway migration).
 * The cache is updated whenever ApplicationSettings are created/changed/deleted.
 */
object ZgwClientSecretRegistrar {

    private val logger = LoggerFactory.getLogger(ZgwClientSecretRegistrar::class.java)

    /** In-memory cache of client_id → secret pairs. */
    private val secrets = ConcurrentHashMap<String, String>()

    /**
     * Call once during application startup (after Flyway migration).
     * Loads secrets from environment config and the database, merging them into the cache.
     * Database secrets take precedence if both sources define the same client_id.
     */
    fun initialise() {
        secrets.clear()

        transaction {
            // Load all application settings from database
            val dbSettings = ApplicationSettingEntity.all().toList()

            // First, load from environment config
            secrets.putAll(AuthenticationConfig.clientCredentials)

            // Then, load from database (which will override env config if present)
            for (entity in dbSettings) {
                if (entity.clientSecret != null) {
                    secrets[entity.clientId] = entity.clientSecret!!
                }
            }

            logger.info(
                "ZGW client secrets initialized: {} from env config, {} from database, {} total",
                AuthenticationConfig.clientCredentials.size,
                dbSettings.count { it.clientSecret != null },
                secrets.size,
            )
        }
    }

    /**
     * Returns the secret for the given [clientId], or null if not found.
     */
    fun getSecret(clientId: String): String? = secrets[clientId]

    /**
     * Returns all cached client IDs.
     */
    fun getAllClientIds(): Set<String> = secrets.keys

    /**
     * Registers or updates a secret for [clientId].
     * Called when a new ApplicationSetting is created or updated.
     */
    fun registerSecret(clientId: String, secret: String) {
        secrets[clientId] = secret
        logger.debug("ZGW client secret registered for client_id: {}", clientId)
    }

    /**
     * Removes the secret for [clientId].
     * Called when an ApplicationSetting is deleted.
     */
    fun unregisterSecret(clientId: String) {
        secrets.remove(clientId)
        logger.debug("ZGW client secret unregistered for client_id: {}", clientId)
    }

    /**
     * Clears all cached secrets. Used for testing.
     */
    internal fun resetForTesting() {
        secrets.clear()
    }
}
