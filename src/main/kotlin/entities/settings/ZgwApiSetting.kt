// SPDX-License-Identifier: EUPL-1.2
// Copyright (C) 2026 Gemeente Utrecht
package com.baseflow.entities.settings

import com.baseflow.tooling.multiAlgorithmEncryptor
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.dao.id.java.UUIDTable
import org.jetbrains.exposed.v1.crypt.encryptedVarchar
import org.jetbrains.exposed.v1.dao.java.UUIDEntity
import org.jetbrains.exposed.v1.dao.java.UUIDEntityClass
import org.jetbrains.exposed.v1.datetime.CurrentDateTime
import org.jetbrains.exposed.v1.datetime.datetime
import java.util.UUID

private val lazyEncryptor = multiAlgorithmEncryptor()

object ZgwApiSettingsTable : UUIDTable("zgw_api_settings") {
    val name = varchar("name", 100).uniqueIndex()
    val baseUrl = text("base_url")
    val clientId = text("client_id")
    val clientSecret = encryptedVarchar("client_secret_encrypted", 512, lazyEncryptor).nullable()
    val createdAt = datetime("created_at").defaultExpression(CurrentDateTime)
    val updatedAt = datetime("updated_at").defaultExpression(CurrentDateTime)
}

class ZgwApiSettingEntity(id: EntityID<UUID>) : UUIDEntity(id) {
    companion object : UUIDEntityClass<ZgwApiSettingEntity>(ZgwApiSettingsTable)

    var name by ZgwApiSettingsTable.name
    var baseUrl by ZgwApiSettingsTable.baseUrl
    var clientId by ZgwApiSettingsTable.clientId
    var clientSecret by ZgwApiSettingsTable.clientSecret
    var createdAt by ZgwApiSettingsTable.createdAt
    var updatedAt by ZgwApiSettingsTable.updatedAt
}
