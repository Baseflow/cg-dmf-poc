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

object OidcProviderSettingsTable : UUIDTable("oidc_provider_settings") {
    val name = varchar("name", 100).uniqueIndex()
    val issuer = text("issuer")
    val clientId = text("client_id")
    val clientSecretEncrypted = text("client_secret_encrypted").nullable()
    val createdAt = datetime("created_at").defaultExpression(CurrentDateTime)
    val updatedAt = datetime("updated_at").defaultExpression(CurrentDateTime)
}

class OidcProviderSettingEntity(id: EntityID<UUID>) : UUIDEntity(id) {
    companion object : UUIDEntityClass<OidcProviderSettingEntity>(OidcProviderSettingsTable)

    var name by OidcProviderSettingsTable.name
    var issuer by OidcProviderSettingsTable.issuer
    var clientId by OidcProviderSettingsTable.clientId
    var clientSecretEncrypted by OidcProviderSettingsTable.clientSecretEncrypted
    var createdAt by OidcProviderSettingsTable.createdAt
    var updatedAt by OidcProviderSettingsTable.updatedAt
}
