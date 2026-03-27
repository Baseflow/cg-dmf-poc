// SPDX-License-Identifier: EUPL-1.2
// Copyright (C) 2026 Gemeente Utrecht
package com.baseflow.entities

import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.dao.id.java.UUIDTable
import org.jetbrains.exposed.v1.dao.java.UUIDEntity
import org.jetbrains.exposed.v1.dao.java.UUIDEntityClass
import org.jetbrains.exposed.v1.datetime.CurrentDateTime
import org.jetbrains.exposed.v1.datetime.datetime
import java.util.UUID

/**
 * Table storing blob storage repository configurations.
 * Secrets (access key, secret key) are stored as SHA-256 hashes.
 */
object BlobStorageRepositories : UUIDTable("blob_storage_repositories") {
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
}

class BlobStorageRepositoryEntity(id: EntityID<UUID>) : UUIDEntity(id) {
    companion object : UUIDEntityClass<BlobStorageRepositoryEntity>(BlobStorageRepositories)

    var repoName by BlobStorageRepositories.repoName
    var storageType by BlobStorageRepositories.storageType
    var url by BlobStorageRepositories.url
    var accessKeyHash by BlobStorageRepositories.accessKeyHash
    var secretKeyHash by BlobStorageRepositories.secretKeyHash
    var bucket by BlobStorageRepositories.bucket
    var region by BlobStorageRepositories.region
    var disableChecksums by BlobStorageRepositories.disableChecksums
    var disableChunkedEncoding by BlobStorageRepositories.disableChunkedEncoding
    var extraProperties by BlobStorageRepositories.extraProperties
    var isDefault by BlobStorageRepositories.isDefault
    var createdAt by BlobStorageRepositories.createdAt
    var updatedAt by BlobStorageRepositories.updatedAt
}

