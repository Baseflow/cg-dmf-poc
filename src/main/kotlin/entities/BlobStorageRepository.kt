// SPDX-License-Identifier: EUPL-1.2
// Copyright (C) 2026 Gemeente Utrecht
package com.baseflow.entities

import com.baseflow.config.EncryptionConfig
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.dao.id.java.UUIDTable
import org.jetbrains.exposed.v1.crypt.Algorithms
import org.jetbrains.exposed.v1.crypt.Encryptor
import org.jetbrains.exposed.v1.crypt.encryptedVarchar
import org.jetbrains.exposed.v1.dao.java.UUIDEntity
import org.jetbrains.exposed.v1.dao.java.UUIDEntityClass
import org.jetbrains.exposed.v1.datetime.CurrentDateTime
import org.jetbrains.exposed.v1.datetime.datetime
import java.util.UUID

/**
 * Lazily-delegating [Encryptor] that creates the real AES-256-PBE-CBC encryptor the
 * first time it is actually used (i.e. on the first read/write of an encrypted column).
 *
 * This avoids requiring [EncryptionConfig.secretKey] and [EncryptionConfig.salt] at
 * class-loading time, which would break unit tests that create the schema without having
 * the encryption environment variables set.
 */
private val blobStorageEncryptor: Encryptor by lazy {
    Algorithms.AES_256_PBE_CBC(EncryptionConfig.secretKey, EncryptionConfig.salt)
}

private val lazyEncryptor = Encryptor(
    encryptFn = { blobStorageEncryptor.encrypt(it) },
    decryptFn = { blobStorageEncryptor.decrypt(it) },
    maxColLengthFn = { blobStorageEncryptor.maxColLength(it) },
)

/**
 * Table storing blob storage repository configurations.
 * Secrets (access key, secret key) are stored AES-256-CBC encrypted using the
 * application-level encryption key ([EncryptionConfig.secretKey]).
 */
object BlobStorageRepositories : UUIDTable("blob_storage_repositories") {
    val repoName = varchar("name", 100).uniqueIndex()
    val storageType = varchar("storage_type", 50)
    val url = varchar("url", 500)
    val accessKeyEncrypted = encryptedVarchar("access_key_encrypted", 512, lazyEncryptor)
    val secretKeyEncrypted = encryptedVarchar("secret_key_encrypted", 512, lazyEncryptor)
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
    var accessKeyEncrypted by BlobStorageRepositories.accessKeyEncrypted
    var secretKeyEncrypted by BlobStorageRepositories.secretKeyEncrypted
    var bucket by BlobStorageRepositories.bucket
    var region by BlobStorageRepositories.region
    var disableChecksums by BlobStorageRepositories.disableChecksums
    var disableChunkedEncoding by BlobStorageRepositories.disableChunkedEncoding
    var extraProperties by BlobStorageRepositories.extraProperties
    var isDefault by BlobStorageRepositories.isDefault
    var createdAt by BlobStorageRepositories.createdAt
    var updatedAt by BlobStorageRepositories.updatedAt
}
