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

object BlobStorageRepositorySettingsTable : UUIDTable("blob_storage_repository_settings") {
    val repoName             = varchar("name", 100).uniqueIndex()
    val storageType          = varchar("storage_type", 50)
    val url                  = varchar("url", 500)
    val accessKeyEncrypted   = text("access_key_encrypted").nullable()
    val secretKeyEncrypted   = text("secret_key_encrypted").nullable()
    val bucket               = varchar("bucket", 255)
    val isDefault            = bool("is_default").default(false)
    val storageAccountName   = varchar("storage_account_name", 255).nullable()
    val enabled              = bool("enabled").default(true)
    val updatedAt            = datetime("updated_at").defaultExpression(CurrentDateTime)
}

class BlobStorageRepositorySettingEntity(id: EntityID<UUID>) : UUIDEntity(id) {
    companion object : UUIDEntityClass<BlobStorageRepositorySettingEntity>(BlobStorageRepositorySettingsTable)

    var repoName           by BlobStorageRepositorySettingsTable.repoName
    var storageType        by BlobStorageRepositorySettingsTable.storageType
    var url                by BlobStorageRepositorySettingsTable.url
    var accessKeyEncrypted by BlobStorageRepositorySettingsTable.accessKeyEncrypted
    var secretKeyEncrypted by BlobStorageRepositorySettingsTable.secretKeyEncrypted
    var bucket             by BlobStorageRepositorySettingsTable.bucket
    var isDefault          by BlobStorageRepositorySettingsTable.isDefault
    var storageAccountName by BlobStorageRepositorySettingsTable.storageAccountName
    var enabled            by BlobStorageRepositorySettingsTable.enabled
    var updatedAt          by BlobStorageRepositorySettingsTable.updatedAt
}
