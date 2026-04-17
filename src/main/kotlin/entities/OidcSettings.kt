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

object OidcSettingsTable : UUIDTable("oidc_settings") {
    val issuer = text("issuer")
    val clientId = text("client_id")
    val clientSecretEncrypted = text("client_secret_encrypted").nullable()
    val updatedAt = datetime("updated_at").defaultExpression(CurrentDateTime)
}

class OidcSettingsEntity(id: EntityID<UUID>) : UUIDEntity(id) {
    companion object : UUIDEntityClass<OidcSettingsEntity>(OidcSettingsTable)

    var issuer by OidcSettingsTable.issuer
    var clientId by OidcSettingsTable.clientId
    var clientSecretEncrypted by OidcSettingsTable.clientSecretEncrypted
    var updatedAt by OidcSettingsTable.updatedAt
}
