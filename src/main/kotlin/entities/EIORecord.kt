// SPDX-License-Identifier: EUPL-1.2
// Copyright (C) 2025 Gemeente Utrecht
package com.baseflow.entities

import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.dao.id.UUIDTable
import org.jetbrains.exposed.v1.dao.UUIDEntity
import org.jetbrains.exposed.v1.dao.UUIDEntityClass
import java.util.UUID

object EIORecords : UUIDTable("eio_records") {
    val lockToken = varchar("lock_token", 100).nullable()
}

class EIORecordEntity(id: EntityID<UUID>) : UUIDEntity(id) {
    companion object : UUIDEntityClass<EIORecordEntity>(EIORecords)
    var lockToken by EIORecords.lockToken
    val versions by EIOVersionEntity.Companion referrersOn EIOVersions.recordId
}
