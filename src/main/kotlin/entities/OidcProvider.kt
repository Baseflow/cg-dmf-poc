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

object OidcProviderTable : UUIDTable("oidc_providers") {
    val name = varchar("name", 100).uniqueIndex()
    val issuer = text("issuer")
    val clientId = text("client_id")
    val clientSecretEncrypted = text("client_secret_encrypted").nullable()
    val updatedAt = datetime("updated_at").defaultExpression(CurrentDateTime)
}

class OidcProviderEntity(id: EntityID<UUID>) : UUIDEntity(id) {
    companion object : UUIDEntityClass<OidcProviderEntity>(OidcProviderTable)

    var name by OidcProviderTable.name
    var issuer by OidcProviderTable.issuer
    var clientId by OidcProviderTable.clientId
    var clientSecretEncrypted by OidcProviderTable.clientSecretEncrypted
    var updatedAt by OidcProviderTable.updatedAt
}
