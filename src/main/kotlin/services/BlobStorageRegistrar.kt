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
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.slf4j.LoggerFactory
import java.security.MessageDigest

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
        val configs = BlobStorageConfig.repositories
        if (configs.isEmpty()) {
            logger.warn("No blob storage repositories configured – file uploads will not work.")
            return
        }

        transaction {
            for (cfg in configs) {
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
                val firstName = configs.first().name
                defaultProviderName = firstName
                BlobStorageRepositoryEntity
                    .find { BlobStorageRepositories.repoName eq firstName }
                    .firstOrNull()
                    ?.let { it.isDefault = true }
            }
        }

        for (cfg in configs) {
            providers[cfg.name] = createProvider(cfg)
        }

        logger.info(
            "Registered {} blob storage provider(s): {} — default: {}",
            providers.size,
            providers.keys,
            defaultProviderName,
        )
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

        val accessHash = sha256(cfg.accessKey)
        val secretHash = sha256(cfg.secretKey)
        val extraJson = Json.encodeToString(
            JsonObject.serializer(),
            JsonObject(cfg.extraProperties.mapValues { JsonPrimitive(it.value) }),
        )

        if (existing != null) {
            existing.storageType = cfg.type.label
            existing.url = cfg.url
            existing.accessKeyHash = accessHash
            existing.secretKeyHash = secretHash
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
                accessKeyHash = accessHash
                secretKeyHash = secretHash
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

    /** SHA-256 hex digest of [input]. */
    internal fun sha256(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(input.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }
}
