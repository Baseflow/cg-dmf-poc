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

object ApiConnectionSettingsTable : UUIDTable("api_connection_settings") {
    val name = varchar("name", 100).uniqueIndex()
    val baseUrl = text("base_url")
    val clientId = text("client_id")
    val clientSecret = encryptedVarchar("client_secret_encrypted", 512, lazyEncryptor).nullable()
    val apiType = varchar("api_type", 10).default(ApiConnectionType.ORC.value)
    val authType = varchar("auth_type", 20).default(ApiAuthType.ZGW_AUTH.value)
    val validationEnabled = bool("validation_enabled").default(true)
    val enabled = bool("enabled").default(true)
    val readonly = bool("readonly").default(false)
    val createdAt = datetime("created_at").defaultExpression(CurrentDateTime)
    val updatedAt = datetime("updated_at").defaultExpression(CurrentDateTime)
}

class ApiConnectionSettingEntity(id: EntityID<UUID>) : UUIDEntity(id) {
    companion object : UUIDEntityClass<ApiConnectionSettingEntity>(ApiConnectionSettingsTable)

    var name by ApiConnectionSettingsTable.name
    var baseUrl by ApiConnectionSettingsTable.baseUrl
    var clientId by ApiConnectionSettingsTable.clientId
    var clientSecret by ApiConnectionSettingsTable.clientSecret
    var apiType by ApiConnectionSettingsTable.apiType
    var authType by ApiConnectionSettingsTable.authType
    var validationEnabled by ApiConnectionSettingsTable.validationEnabled
    var enabled by ApiConnectionSettingsTable.enabled
    var readonly by ApiConnectionSettingsTable.readonly
    var createdAt by ApiConnectionSettingsTable.createdAt
    var updatedAt by ApiConnectionSettingsTable.updatedAt
}
