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

private val oidcProviderEncryptor: Encryptor by lazy {
    Algorithms.AES_256_PBE_CBC(EncryptionConfig.secretKey, EncryptionConfig.salt)
}

private val lazyEncryptor = Encryptor(
    encryptFn = { oidcProviderEncryptor.encrypt(it) },
    decryptFn = { oidcProviderEncryptor.decrypt(it) },
    maxColLengthFn = { oidcProviderEncryptor.maxColLength(it) },
)

object OidcProviderSettingsTable : UUIDTable("oidc_provider_settings") {
    val name = varchar("name", 100).uniqueIndex()
    val issuer = text("issuer")
    val clientId = text("client_id")
    val clientSecret = encryptedVarchar("client_secret_encrypted", 512, lazyEncryptor).nullable()
    val createdAt = datetime("created_at").defaultExpression(CurrentDateTime)
    val updatedAt = datetime("updated_at").defaultExpression(CurrentDateTime)
}

class OidcProviderSettingEntity(id: EntityID<UUID>) : UUIDEntity(id) {
    companion object : UUIDEntityClass<OidcProviderSettingEntity>(OidcProviderSettingsTable)

    var name by OidcProviderSettingsTable.name
    var issuer by OidcProviderSettingsTable.issuer
    var clientId by OidcProviderSettingsTable.clientId
    var clientSecret by OidcProviderSettingsTable.clientSecret
    var createdAt by OidcProviderSettingsTable.createdAt
    var updatedAt by OidcProviderSettingsTable.updatedAt
}
