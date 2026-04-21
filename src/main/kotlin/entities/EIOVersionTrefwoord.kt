// SPDX-License-Identifier: EUPL-1.2
// Copyright (C) 2026 Gemeente Utrecht
package com.baseflow.entities

import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.dao.id.java.UUIDTable
import org.jetbrains.exposed.v1.dao.java.UUIDEntity
import org.jetbrains.exposed.v1.dao.java.UUIDEntityClass
import java.util.UUID

object EIOVersionTrefwoorden : UUIDTable("eio_version_trefwoorden") {
    val versionId =
        reference("version_id", EIOVersions, onDelete = ReferenceOption.CASCADE)
    val trefwoord = varchar("trefwoord", 100)
}

class EIOVersionTrefwoordEntity(id: EntityID<UUID>) : UUIDEntity(id) {
    companion object : UUIDEntityClass<EIOVersionTrefwoordEntity>(EIOVersionTrefwoorden)

    var versionId by EIOVersionEntity referencedOn EIOVersionTrefwoorden.versionId
    var trefwoord by EIOVersionTrefwoorden.trefwoord
}
