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

object ZgwApiSettingsTable : UUIDTable("zgw_api_settings") {
    val name = varchar("name", 100).uniqueIndex()
    val baseUrl = text("base_url")
    val clientId = text("client_id")
    val clientSecretEncrypted = text("client_secret_encrypted").nullable()
    val updatedAt = datetime("updated_at").defaultExpression(CurrentDateTime)
}

class ZgwApiSettingsEntity(id: EntityID<UUID>) : UUIDEntity(id) {
    companion object : UUIDEntityClass<ZgwApiSettingsEntity>(ZgwApiSettingsTable)

    var name by ZgwApiSettingsTable.name
    var baseUrl by ZgwApiSettingsTable.baseUrl
    var clientId by ZgwApiSettingsTable.clientId
    var clientSecretEncrypted by ZgwApiSettingsTable.clientSecretEncrypted
    var updatedAt by ZgwApiSettingsTable.updatedAt
}
