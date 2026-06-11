// SPDX-License-Identifier: EUPL-1.2
// Copyright (C) 2026 Gemeente Utrecht
package com.baseflow.shared.services

import com.baseflow.shared.config.AuthenticationConfig
import com.baseflow.shared.entities.settings.ApplicationSettingEntity
import com.baseflow.shared.entities.settings.ApplicationSettingsTable
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Clock

/**
 * Manages client secrets cache for JWT signature verification.
 *
 * On startup, loads secrets from both [AuthenticationConfig.clientCredentials] (environment variables)
 * and the [ApplicationSettingEntity] database table, combining them into a single in-memory cache.
 * Database entries take precedence over environment variables when both define the same client_id.
 *
 * Call [initialise] once during application startup (after Flyway migration).
 * The cache is updated whenever ApplicationSettings are created/changed/deleted.
 *
 * **Single-instance only.** This is a plain in-memory cache — each application instance maintains
 * its own independent copy. In a horizontally-scaled deployment (multiple pods, Kubernetes, etc.)
 * a credential change applied through one instance will not be visible to the others until they
 * restart. If multi-instance support is required, replace this cache with a distributed store
 * (e.g. Redis / Memcached) or add a short TTL so stale entries self-expire.
 */
object ApplicationCredentialRegistrar {

    private val logger = LoggerFactory.getLogger(ApplicationCredentialRegistrar::class.java)

    /** In-memory cache of client_id → secret pairs. */
    private val secrets = ConcurrentHashMap<String, String>()

    /**
     * Call once during application startup (after Flyway migration).
     * Loads secrets from environment config and the database, merging them into the cache.
     * Database secrets take precedence over env config when both define the same client_id.
     *
     * WARNING: Do not call this while the HTTP server is already handling requests. The
     * [secrets].clear() → repopulate sequence is not atomic, so concurrent requests would
     * briefly see an empty cache. Currently safe because [initialise] is only called before
     * the server starts listening.
     */
    fun initialise() {
        secrets.clear()

        // Load env config first, outside the transaction — it has no DB dependency, and loading
        // it inside would leave the cache empty if the transaction fails (e.g. DB unreachable).
        secrets.putAll(AuthenticationConfig.clientCredentials)

        var dbSecretCount = 0
        var importedCount = 0
        transaction {
            // Load existing DB entries and update the in-memory cache.
            for (entity in ApplicationSettingEntity.all()) {
                val secret = runCatching { entity.clientSecret }
                    .onFailure {
                        logger.error(
                            "Failed to decrypt clientSecret for application '{}' ({}); skipping secret for client_id='{}'",
                            entity.name,
                            entity.id.value,
                            entity.clientId,
                            it,
                        )
                    }
                    .getOrNull()
                    ?.takeIf { it.isNotBlank() }

                if (secret != null) {
                    secrets[entity.clientId] = secret
                    dbSecretCount++
                }
            }

            // Import env credentials that are not yet present in the database (matched by clientId).
            for ((clientId, secret) in AuthenticationConfig.clientCredentials) {
                val alreadyExists = ApplicationSettingEntity
                    .find { ApplicationSettingsTable.clientId eq clientId }
                    .firstOrNull() != null

                if (!alreadyExists) {
                    ApplicationSettingEntity.new {
                        name = clientId
                        this.clientId = clientId
                        clientSecret = secret
                        readonly = true
                        updatedAt = Clock.System.now().toLocalDateTime(TimeZone.UTC)
                    }
                    importedCount++
                    logger.info("Imported env credential into database for client_id='{}'", clientId)
                }
            }
        }

        logger.info(
            "Client secrets initialized: {} from env config, {} from database ({} newly imported), {} total",
            AuthenticationConfig.clientCredentials.size,
            dbSecretCount,
            importedCount,
            secrets.size,
        )
    }

    /**
     * Returns the secret for the given [clientId], or null if not found.
     */
    fun getSecret(clientId: String): String? = secrets[clientId]

    /**
     * Registers or updates a secret for [clientId].
     * Called when a new ApplicationSetting is created or updated.
     */
    fun registerSecret(clientId: String, secret: String) {
        secrets[clientId] = secret
        logger.debug("Client secret registered for client_id: {}", clientId)
    }

    /**
     * Removes the secret for [clientId].
     * Called when an ApplicationSetting is deleted.
     *
     * NOTE: If [clientId] was also present in [AuthenticationConfig.clientCredentials] (env config),
     * it will be removed from the cache permanently until the next restart. Env-sourced credentials
     * are not re-added automatically on delete — only [initialise] restores them.
     */
    fun unregisterSecret(clientId: String) {
        secrets.remove(clientId)
        logger.debug("Client secret unregistered for client_id: {}", clientId)
    }

    /**
     * Clears all cached secrets. Used for testing.
     */
    internal fun resetForTesting() {
        secrets.clear()
    }
}
