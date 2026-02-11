// SPDX-License-Identifier: EUPL-1.2
// Copyright (C) 2026 Gemeente Utrecht
package com.baseflow.entities

import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.dao.id.UUIDTable
import org.jetbrains.exposed.v1.dao.UUIDEntity
import org.jetbrains.exposed.v1.dao.UUIDEntityClass
import org.jetbrains.exposed.v1.datetime.datetime
import java.util.UUID
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
object OIORecords : UUIDTable("oio_records") {
    val informatieobject = reference("informatieobject", EIORecords, onDelete = ReferenceOption.CASCADE)
    val informatieobjectVersie = reference("informatieobject_versie", EIOVersions, onDelete = ReferenceOption.CASCADE)
    val subjectObject = varchar("subject_object", 1000)
    val subjectType = varchar("subject_type", 20)
    val createdAt = datetime("created_at").nullable()
    val updatedAt = datetime("updated_at").nullable()

    init {
        uniqueIndex("uq_oio_informatieobject_object", informatieobject, subjectObject)
    }
}

@OptIn(ExperimentalTime::class)
class OIORecordEntity(id: EntityID<UUID>) : UUIDEntity(id) {
    companion object : UUIDEntityClass<OIORecordEntity>(OIORecords)

    var informatieobject by EIORecordEntity referencedOn OIORecords.informatieobject
    var subjectObject by OIORecords.subjectObject
    var subjectType by OIORecords.subjectType
    var informatieobjectVersie by EIOVersionEntity referencedOn OIORecords.informatieobjectVersie
    var createdAt by OIORecords.createdAt
    var updatedAt by OIORecords.updatedAt
}

