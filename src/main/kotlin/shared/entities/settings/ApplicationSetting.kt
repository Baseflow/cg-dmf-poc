// SPDX-License-Identifier: EUPL-1.2
// Copyright (C) 2026 Gemeente Utrecht
package com.baseflow.shared.entities.settings

import com.baseflow.shared.tooling.multiAlgorithmEncryptor
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.dao.id.java.UUIDTable
import org.jetbrains.exposed.v1.crypt.encryptedVarchar
import org.jetbrains.exposed.v1.dao.java.UUIDEntity
import org.jetbrains.exposed.v1.dao.java.UUIDEntityClass
import org.jetbrains.exposed.v1.datetime.CurrentDateTime
import org.jetbrains.exposed.v1.datetime.datetime
import java.util.UUID

private val lazyEncryptor = multiAlgorithmEncryptor()

object ApplicationSettingsTable : UUIDTable("application_settings") {
    val name = varchar("name", 100).uniqueIndex()
    val clientId = text("client_id")
    val clientSecret = encryptedVarchar("client_secret_encrypted", 512, lazyEncryptor).nullable()
    val createdAt = datetime("created_at").defaultExpression(CurrentDateTime)
    val updatedAt = datetime("updated_at").defaultExpression(CurrentDateTime)
}

class ApplicationSettingEntity(id: EntityID<UUID>) : UUIDEntity(id) {
    companion object : UUIDEntityClass<ApplicationSettingEntity>(ApplicationSettingsTable)

    var name by ApplicationSettingsTable.name
    var clientId by ApplicationSettingsTable.clientId
    var clientSecret by ApplicationSettingsTable.clientSecret
    var createdAt by ApplicationSettingsTable.createdAt
    var updatedAt by ApplicationSettingsTable.updatedAt
}
