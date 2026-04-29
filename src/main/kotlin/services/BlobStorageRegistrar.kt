// SPDX-License-Identifier: EUPL-1.2
// Copyright (C) 2026 Gemeente Utrecht
package com.baseflow.services

import com.baseflow.config.BlobStorageConfig
import com.baseflow.config.BlobStorageRepoConfig
import com.baseflow.config.BlobStorageType
import com.baseflow.entities.BlobStorageRepositories
import com.baseflow.entities.BlobStorageRepositoryEntity
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.slf4j.LoggerFactory

/**
 * Reads [BlobStorageConfig] on startup, hashes secrets, and
 * upserts every configured repository into [BlobStorageRepositories].
 *
 * Also exposes the live [BlobStorageProvider] instances for the rest of the
 * application to use.
 *
 * The **default** repository is the one that [StorageService] uses when no
 * explicit repository name is given.  The default is determined by:
 * 1. The `is_default = true` row in [BlobStorageRepositories] (survives restarts).
 * 2. If none is marked, the first configured repository is used.
 *
 * Call [setDefaultProvider] to change the default at runtime – the change is
 * persisted immediately to the database.
 */
object BlobStorageRegistrar {

    private val logger = LoggerFactory.getLogger(BlobStorageRegistrar::class.java)

    /** Provider instances keyed by repository name. */
    private val providers = mutableMapOf<String, BlobStorageProvider>()

    /** Name of the currently designated default provider (may be `null` before [initialise]). */
    @Volatile
    private var defaultProviderName: String? = null

    /**
     * Call once during application startup (after Flyway migration).
     * Syncs every configured repository to the database and instantiates
     * the matching [BlobStorageProvider].
     */
    fun initialise() {
        val envConfigs = BlobStorageConfig.repositories

        if (envConfigs.isEmpty()) {
            // No env-based configuration – fall back to repositories stored in the database.
            logger.info("No blob storage repositories configured via env vars – loading from database.")
            val dbConfigs = transaction { loadConfigsFromDatabase() }
            if (dbConfigs.isEmpty()) {
                logger.warn("No blob storage repositories found in database either – file uploads will not work.")
                return
            }
            for (cfg in dbConfigs) {
                providers[cfg.name] = createProvider(cfg)
            }
            // Honour the is_default flag that is already persisted in the DB.
            defaultProviderName = defaultProviderName
                ?: dbConfigs.firstOrNull { it.index == -1 }?.name
                ?: dbConfigs.first().name
            logger.info(
                "Registered {} blob storage provider(s) from database: {} — default: {}",
                providers.size,
                providers.keys,
                defaultProviderName,
            )
            return
        }

        transaction {
            for (cfg in envConfigs) {
                upsertRepository(cfg)
            }

            // Determine the default: prefer the row already marked is_default=true in the DB,
            // fall back to the first configured repository.
            val markedDefault = BlobStorageRepositoryEntity
                .find { BlobStorageRepositories.isDefault eq true }
                .firstOrNull()

            if (markedDefault != null) {
                defaultProviderName = markedDefault.repoName
            } else {
                // Mark the first config as default
                val firstName = envConfigs.first().name
                defaultProviderName = firstName
                BlobStorageRepositoryEntity
                    .find { BlobStorageRepositories.repoName eq firstName }
                    .firstOrNull()
                    ?.let { it.isDefault = true }
            }
        }

        for (cfg in envConfigs) {
            providers[cfg.name] = createProvider(cfg)
        }

        logger.info(
            "Registered {} blob storage provider(s): {} — default: {}",
            providers.size,
            providers.keys,
            defaultProviderName,
        )
    }

    /**
     * Reads all rows from [BlobStorageRepositories] and converts them to [BlobStorageRepoConfig].
     * The [BlobStorageRepoConfig.index] is set to `-1` for rows that are marked as default
     * (used as a sentinel to pick the default provider name), and `0` for others.
     *
     * Must be called inside a [transaction].
     */
    private fun loadConfigsFromDatabase(): List<BlobStorageRepoConfig> {
        val entities = BlobStorageRepositoryEntity.all().toList()
        if (entities.isEmpty()) return emptyList()

        val default = entities.firstOrNull { it.isDefault } ?: entities.first()
        defaultProviderName = default.repoName

        return entities.map { entity ->
            val extraMap = runCatching {
                Json.parseToJsonElement(entity.extraProperties)
                    .let { it as? JsonObject }
                    ?.mapValues { (_, v) -> v.jsonPrimitive.content }
                    ?: emptyMap()
            }.getOrDefault(emptyMap())

            BlobStorageRepoConfig(
                index = 0,
                name = entity.repoName,
                type = BlobStorageType.fromLabel(entity.storageType),
                url = entity.url,
                accessKey = entity.accessKey,
                secretKey = entity.secretKey,
                bucket = entity.bucket,
                region = entity.region,
                disableChecksums = entity.disableChecksums,
                disableChunkedEncoding = entity.disableChunkedEncoding,
                extraProperties = extraMap,
            )
        }
    }

    /** Returns the provider for the given repository name, or `null` when not found. */
    fun providerByName(name: String): BlobStorageProvider? = providers[name]

    /** Returns the currently designated default provider, or `null` when none configured. */
    fun defaultProvider(): BlobStorageProvider? = defaultProviderName?.let { providers[it] }

    /**
     * Designates [name] as the new default provider.
     * Persists the change to [BlobStorageRepositories] immediately.
     *
     * @throws IllegalArgumentException when [name] does not match a registered provider.
     */
    fun setDefaultProvider(name: String) {
        require(providers.containsKey(name)) {
            "Cannot set default: no provider registered with name '$name'."
        }
        transaction {
            // Clear old default(s)
            BlobStorageRepositoryEntity.all()
                .filter { it.isDefault }
                .forEach { it.isDefault = false }

            // Set new default
            BlobStorageRepositoryEntity
                .find { BlobStorageRepositories.repoName eq name }
                .firstOrNull()
                ?.let { it.isDefault = true }
                ?: error("Repository '$name' not found in database.")
        }
        defaultProviderName = name
        logger.info("Default blob storage repository changed to '{}'", name)
    }

    /**
     * Registers a new provider from a freshly persisted [BlobStorageRepositoryEntity].
     * The entity must already be saved in the database before calling this.
     */
    fun registerProvider(cfg: BlobStorageRepoConfig, makeDefault: Boolean = false) {
        providers[cfg.name] = createProvider(cfg)
        if (makeDefault || defaultProviderName == null) {
            defaultProviderName = cfg.name
        }
        logger.info("Registered new blob storage provider '{}'", cfg.name)
    }

    /**
     * Replaces the provider for an existing repository, e.g. after updating its config.
     * [oldName] is required when the repository is being renamed.
     */
    fun updateProvider(cfg: BlobStorageRepoConfig, oldName: String? = null) {
        val nameToRemove = oldName ?: cfg.name
        providers.remove(nameToRemove)
        providers[cfg.name] = createProvider(cfg)

        if (defaultProviderName == nameToRemove) {
            defaultProviderName = cfg.name
        }
        logger.info("Updated blob storage provider '{}' (was '{}')", cfg.name, nameToRemove)
    }

    /**
     * Removes the provider for [name] from the in-memory registry.
     * If it was the default, the default is cleared (or reassigned to the first remaining provider).
     */
    fun unregisterProvider(name: String) {
        providers.remove(name)
        if (defaultProviderName == name) {
            defaultProviderName = providers.keys.firstOrNull()
            if (defaultProviderName != null) {
                transaction {
                    BlobStorageRepositoryEntity
                        .find { BlobStorageRepositories.repoName eq defaultProviderName!! }
                        .firstOrNull()
                        ?.let { it.isDefault = true }
                }
            }
        }
        logger.info("Unregistered blob storage provider '{}'", name)
    }

    // ---- test helpers (internal visibility keeps them out of prod call-sites) ----

    /**
     * Directly registers [provider] under its [BlobStorageProvider.name].
     * Intended for unit tests only – bypasses env config and database.
     */
    internal fun registerForTesting(provider: BlobStorageProvider, isDefault: Boolean = false) {
        providers[provider.name] = provider
        if (isDefault || defaultProviderName == null) {
            defaultProviderName = provider.name
        }
    }

    /** Clears all registered providers and resets the default pointer. For unit tests only. */
    internal fun resetForTesting() {
        providers.clear()
        defaultProviderName = null
    }

    // ---- internal helpers ---------------------------------------------------

    private fun upsertRepository(cfg: BlobStorageRepoConfig) {
        val existing = BlobStorageRepositoryEntity
            .find { BlobStorageRepositories.repoName eq cfg.name }
            .firstOrNull()

        val extraJson = Json.encodeToString(
            JsonObject.serializer(),
            JsonObject(cfg.extraProperties.mapValues { JsonPrimitive(it.value) }),
        )

        if (existing != null) {
            existing.storageType = cfg.type.label
            existing.url = cfg.url
            existing.accessKey = cfg.accessKey
            existing.secretKey = cfg.secretKey
            existing.bucket = cfg.bucket
            existing.region = cfg.region
            existing.disableChecksums = cfg.disableChecksums
            existing.disableChunkedEncoding = cfg.disableChunkedEncoding
            existing.extraProperties = extraJson
            logger.info("Updated blob storage repository '{}' in database", cfg.name)
        } else {
            BlobStorageRepositoryEntity.new {
                repoName = cfg.name
                storageType = cfg.type.label
                url = cfg.url
                accessKey = cfg.accessKey
                secretKey = cfg.secretKey
                bucket = cfg.bucket
                region = cfg.region
                disableChecksums = cfg.disableChecksums
                disableChunkedEncoding = cfg.disableChunkedEncoding
                extraProperties = extraJson
            }
            logger.info("Inserted blob storage repository '{}' into database", cfg.name)
        }
    }

    private fun createProvider(cfg: BlobStorageRepoConfig): BlobStorageProvider = when (cfg.type) {
        BlobStorageType.S3 -> S3BlobStorageProvider(cfg)
        BlobStorageType.AZURE_BLOB_STORAGE -> AzureBlobStorageProvider(cfg)
    }
}
