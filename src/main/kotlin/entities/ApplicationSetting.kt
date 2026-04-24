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

object ApplicationSettingTable : UUIDTable("application_settings") {
    val name = varchar("name", 100).uniqueIndex()
    val clientId = text("client_id")
    val clientSecretEncrypted = text("client_secret_encrypted").nullable()
    val updatedAt = datetime("updated_at").defaultExpression(CurrentDateTime)
}

class ApplicationSettingEntity(id: EntityID<UUID>) : UUIDEntity(id) {
    companion object : UUIDEntityClass<ApplicationSettingEntity>(ApplicationSettingTable)

    var name by ApplicationSettingTable.name
    var clientId by ApplicationSettingTable.clientId
    var clientSecretEncrypted by ApplicationSettingTable.clientSecretEncrypted
    var updatedAt by ApplicationSettingTable.updatedAt
}
