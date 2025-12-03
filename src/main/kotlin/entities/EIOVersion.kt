// SPDX-License-Identifier: EUPL-1.2
// Copyright (C) 2025 Gemeente Utrecht
package com.baseflow

import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.dao.id.UUIDTable
import org.jetbrains.exposed.v1.dao.UUIDEntity
import org.jetbrains.exposed.v1.dao.UUIDEntityClass
import java.util.UUID

object EIOVersions : UUIDTable("eio_versions") {
    val recordId = reference("record_id", EIORecords, onDelete = ReferenceOption.CASCADE)
    val versie = integer("versie")
    val taal = varchar("taal", 3).nullable()
    val bestandsnaam = varchar("bestandsnaam", 255).nullable()
}

class EIOVersionEntity(id: EntityID<UUID>) : UUIDEntity(id) {
    companion object : UUIDEntityClass<EIOVersionEntity>(EIOVersions)
    var recordId by EIORecordEntity referencedOn EIOVersions.recordId
    var versie by EIOVersions.versie
    var taal by EIOVersions.taal
    var bestandsnaam by EIOVersions.bestandsnaam
}
