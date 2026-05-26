// SPDX-License-Identifier: EUPL-1.2
// Copyright (C) 2026 Gemeente Utrecht
package com.baseflow.entities.settings

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

private val blobStorageSettingEncryptor: Encryptor by lazy {
    Algorithms.AES_256_PBE_CBC(EncryptionConfig.secretKey, EncryptionConfig.salt)
}

private val lazyEncryptor = Encryptor(
    encryptFn = { blobStorageSettingEncryptor.encrypt(it) },
    decryptFn = { blobStorageSettingEncryptor.decrypt(it) },
    maxColLengthFn = { blobStorageSettingEncryptor.maxColLength(it) },
)

object BlobStorageRepositorySettingsTable : UUIDTable("blob_storage_repository_settings") {
    val repoName = varchar("name", 100).uniqueIndex()
    val storageType = varchar("storage_type", 50)
    val url = varchar("url", 500)
    val accessKey = encryptedVarchar("access_key_encrypted", 512, lazyEncryptor).nullable()
    val secretKey = encryptedVarchar("secret_key_encrypted", 512, lazyEncryptor).nullable()
    val bucket = varchar("bucket", 255)
    val region = varchar("region", 50).nullable()
    val extraProperties = text("extra_properties").default("{}")
    val isDefault = bool("is_default").default(false)
    val storageAccountName = varchar("storage_account_name", 255).nullable()
    val enabled = bool("enabled").default(true)
    val createdAt = datetime("created_at").defaultExpression(CurrentDateTime)
    val updatedAt = datetime("updated_at").defaultExpression(CurrentDateTime)
}

class BlobStorageRepositorySettingEntity(id: EntityID<UUID>) : UUIDEntity(id) {
    companion object : UUIDEntityClass<BlobStorageRepositorySettingEntity>(BlobStorageRepositorySettingsTable)

    var repoName by BlobStorageRepositorySettingsTable.repoName
    var storageType by BlobStorageRepositorySettingsTable.storageType
    var url by BlobStorageRepositorySettingsTable.url
    var accessKey by BlobStorageRepositorySettingsTable.accessKey
    var secretKey by BlobStorageRepositorySettingsTable.secretKey
    var bucket by BlobStorageRepositorySettingsTable.bucket
    var region by BlobStorageRepositorySettingsTable.region
    var extraProperties by BlobStorageRepositorySettingsTable.extraProperties
    var isDefault by BlobStorageRepositorySettingsTable.isDefault
    var storageAccountName by BlobStorageRepositorySettingsTable.storageAccountName
    var enabled by BlobStorageRepositorySettingsTable.enabled
    var createdAt by BlobStorageRepositorySettingsTable.createdAt
    var updatedAt by BlobStorageRepositorySettingsTable.updatedAt
}
