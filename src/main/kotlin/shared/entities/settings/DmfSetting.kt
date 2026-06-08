// SPDX-License-Identifier: EUPL-1.2
// Copyright (C) 2026 Gemeente Utrecht
package com.baseflow.shared.entities.settings

import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.dao.id.java.UUIDTable
import org.jetbrains.exposed.v1.dao.java.UUIDEntity
import org.jetbrains.exposed.v1.dao.java.UUIDEntityClass
import org.jetbrains.exposed.v1.datetime.CurrentDateTime
import org.jetbrains.exposed.v1.datetime.datetime
import java.util.UUID

object DmfSettingsTable : UUIDTable("dmf_settings") {
    val triggerSizeBytes = long("trigger_size_bytes")
    val chunkSizeBytes = long("chunk_size_bytes")
    val validationEnabled = bool("validation_enabled")
    val updatedAt = datetime("updated_at").defaultExpression(CurrentDateTime)
}

class DmfSettingEntity(id: EntityID<UUID>) : UUIDEntity(id) {
    companion object : UUIDEntityClass<DmfSettingEntity>(DmfSettingsTable) {
        val SINGLETON_ID: UUID = UUID.fromString("00000000-0000-0000-0000-000000000001")
    }

    var triggerSizeBytes by DmfSettingsTable.triggerSizeBytes
    var chunkSizeBytes by DmfSettingsTable.chunkSizeBytes
    var validationEnabled by DmfSettingsTable.validationEnabled
    var updatedAt by DmfSettingsTable.updatedAt
}
