// SPDX-License-Identifier: EUPL-1.2
// Copyright (C) 2026 Gemeente Utrecht
package com.baseflow.entities

import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.dao.id.java.UUIDTable
import org.jetbrains.exposed.v1.dao.java.UUIDEntity
import org.jetbrains.exposed.v1.dao.java.UUIDEntityClass
import org.jetbrains.exposed.v1.exceptions.ExposedSQLException
import java.util.Locale
import java.util.UUID

/**
 * Table holding unique (case-insensitive) trefwoord values.
 * The [woord] column stores the canonical lowercase representation.
 */
object Trefwoorden : UUIDTable("trefwoorden") {
    val woord = varchar("woord", 100).uniqueIndex()
}

class TrefwoordEntity(id: EntityID<UUID>) : UUIDEntity(id) {
    companion object : UUIDEntityClass<TrefwoordEntity>(Trefwoorden) {
        /** Find an existing trefwoord by its lowercase value, or create a new one. */
        fun findOrCreate(woord: String): TrefwoordEntity {
            val lower = woord.lowercase(Locale.ROOT)
            return find { Trefwoorden.woord.eq(lower) }.firstOrNull()
                ?: try {
                    new { this.woord = lower }
                } catch (_: ExposedSQLException) {
                    // Concurrent insert hit the unique constraint; re-select the now-existing row
                    find { Trefwoorden.woord.eq(lower) }.first()
                }
        }
    }

    var woord by Trefwoorden.woord
}

/**
 * Join table linking EIOVersions to Trefwoorden (many-to-many).
 */
object EIOVersionTrefwoorden : UUIDTable("eio_version_trefwoorden") {
    val versionId =
        reference("version_id", EIOVersions, onDelete = ReferenceOption.CASCADE)
    val trefwoordId =
        reference("trefwoord_id", Trefwoorden, onDelete = ReferenceOption.CASCADE)

    init {
        // Enforces uniqueness and supports lookups of all trefwoorden for a known version.
        index(isUnique = true, versionId, trefwoordId)
        // Covering index for trefwoorden filter queries: resolve matching version IDs by trefwoord_id.
        index(isUnique = false, trefwoordId, versionId)
    }
}

class EIOVersionTrefwoordEntity(id: EntityID<UUID>) : UUIDEntity(id) {
    companion object : UUIDEntityClass<EIOVersionTrefwoordEntity>(EIOVersionTrefwoorden)

    var versionId by EIOVersionEntity referencedOn EIOVersionTrefwoorden.versionId
    var trefwoordId by TrefwoordEntity referencedOn EIOVersionTrefwoorden.trefwoordId
}
