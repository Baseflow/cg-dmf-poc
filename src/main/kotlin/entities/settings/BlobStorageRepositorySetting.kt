// SPDX-License-Identifier: EUPL-1.2
// Copyright (C) 2026 Gemeente Utrecht
package com.baseflow.entities.settings

import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.dao.id.java.UUIDTable
import org.jetbrains.exposed.v1.dao.java.UUIDEntity
import org.jetbrains.exposed.v1.dao.java.UUIDEntityClass
import org.jetbrains.exposed.v1.datetime.CurrentDateTime
import org.jetbrains.exposed.v1.datetime.datetime
import java.util.UUID

/**
 * Table storing blob storage repository configurations.
 * Secrets (access key, secret key) are stored as SHA-256 hashes for env-var-synced entries
 * and as encrypted values (via SecretCrypto) for admin-managed entries.
 */
object BlobStorageRepositorySettingsTable : UUIDTable("blob_storage_repository_settings") {
    val repoName = varchar("name", 100).uniqueIndex()
    val storageType = varchar("storage_type", 50)
    val url = varchar("url", 500)
    val accessKeyHash = varchar("access_key_hash", 64)
    val secretKeyHash = varchar("secret_key_hash", 64)
    val bucket = varchar("bucket", 255)
    val region = varchar("region", 50).nullable()
    val disableChecksums = bool("disable_checksums").default(false)
    val disableChunkedEncoding = bool("disable_chunked_encoding").default(false)
    val extraProperties = text("extra_properties").default("{}")
    val isDefault = bool("is_default").default(false)
    val createdAt = datetime("created_at").defaultExpression(CurrentDateTime)
    val updatedAt = datetime("updated_at").defaultExpression(CurrentDateTime)
    val accessKeyEncrypted = text("access_key_encrypted").nullable()
    val secretKeyEncrypted = text("secret_key_encrypted").nullable()
    val storageAccountName = varchar("storage_account_name", 255).nullable()
    val enabled = bool("enabled").default(true)
}

class BlobStorageRepositorySettingEntity(id: EntityID<UUID>) : UUIDEntity(id) {
    companion object : UUIDEntityClass<BlobStorageRepositorySettingEntity>(BlobStorageRepositorySettingsTable)

    var repoName by BlobStorageRepositorySettingsTable.repoName
    var storageType by BlobStorageRepositorySettingsTable.storageType
    var url by BlobStorageRepositorySettingsTable.url
    var accessKeyHash by BlobStorageRepositorySettingsTable.accessKeyHash
    var secretKeyHash by BlobStorageRepositorySettingsTable.secretKeyHash
    var bucket by BlobStorageRepositorySettingsTable.bucket
    var region by BlobStorageRepositorySettingsTable.region
    var disableChecksums by BlobStorageRepositorySettingsTable.disableChecksums
    var disableChunkedEncoding by BlobStorageRepositorySettingsTable.disableChunkedEncoding
    var extraProperties by BlobStorageRepositorySettingsTable.extraProperties
    var isDefault by BlobStorageRepositorySettingsTable.isDefault
    var createdAt by BlobStorageRepositorySettingsTable.createdAt
    var updatedAt by BlobStorageRepositorySettingsTable.updatedAt
    var accessKeyEncrypted by BlobStorageRepositorySettingsTable.accessKeyEncrypted
    var secretKeyEncrypted by BlobStorageRepositorySettingsTable.secretKeyEncrypted
    var storageAccountName by BlobStorageRepositorySettingsTable.storageAccountName
    var enabled by BlobStorageRepositorySettingsTable.enabled
}