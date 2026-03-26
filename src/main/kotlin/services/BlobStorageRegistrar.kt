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
 */
object BlobStorageRegistrar {

    private val logger = LoggerFactory.getLogger(BlobStorageRegistrar::class.java)

    /** Provider instances keyed by repository name. */
    private val providers = mutableMapOf<String, BlobStorageProvider>()

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
        }

        for (cfg in configs) {
            providers[cfg.name] = createProvider(cfg)
        }

        logger.info("Registered {} blob storage provider(s): {}", providers.size, providers.keys)
    }

    /** Returns the provider for the given repository name, or `null` when not found. */
    fun providerByName(name: String): BlobStorageProvider? = providers[name]

    /** Returns the first (default) provider, or `null` when none configured. */
    fun defaultProvider(): BlobStorageProvider? = providers.values.firstOrNull()

    /** Returns all registered providers. */
    fun allProviders(): Collection<BlobStorageProvider> = providers.values

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

