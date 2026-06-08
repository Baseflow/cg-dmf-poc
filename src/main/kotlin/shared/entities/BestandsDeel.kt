// SPDX-License-Identifier: EUPL-1.2
// Copyright (C) 2026 Gemeente Utrecht
package com.baseflow.shared.entities

import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.dao.id.java.UUIDTable
import org.jetbrains.exposed.v1.core.greater
import org.jetbrains.exposed.v1.dao.java.UUIDEntity
import org.jetbrains.exposed.v1.dao.java.UUIDEntityClass
import java.util.UUID

/**
 * Database table for individual file parts (bestandsdelen) used in the chunked-upload workflow.
 *
 * A set of bestandsdelen is created when an [EIOVersionEntity] is created with a
 * [bestandsomvang][EIOVersions.bestandsomvang] that exceeds the configured trigger size.  Each part records its
 * sequential number ([volgnummer]), the expected byte size ([omvang]), and whether the
 * upload for this part has been completed ([voltooid]).
 */
object BestandsDelen : UUIDTable("bestandsdelen") {
    val versionId = reference("version_id", EIOVersions, onDelete = ReferenceOption.CASCADE)
    val volgnummer = integer("volgnummer")
        .check("chk_bestandsdelen_volgnummer") { it greater 0 }
    val omvang = long("omvang")
        .check("chk_bestandsdelen_omvang") { it greater 0L }
    val voltooid = bool("voltooid").default(false)
    val lock = varchar("lock", 100).default("")

    init {
        uniqueIndex("uq_bestandsdelen_version_volgnummer", versionId, volgnummer)
        index("idx_bestandsdelen_version_id", isUnique = false, versionId)
    }
}

class BestandsDeelEntity(id: EntityID<UUID>) : UUIDEntity(id) {
    companion object : UUIDEntityClass<BestandsDeelEntity>(BestandsDelen)

    var versionId by EIOVersionEntity referencedOn BestandsDelen.versionId
    var volgnummer by BestandsDelen.volgnummer
    var omvang by BestandsDelen.omvang
    var voltooid by BestandsDelen.voltooid
    var lock by BestandsDelen.lock
}
