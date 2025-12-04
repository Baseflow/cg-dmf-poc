// SPDX-License-Identifier: EUPL-1.2
// Copyright (C) 2025 Gemeente Utrecht
package com.baseflow.services

import com.baseflow.EIORecordEntity
import com.baseflow.EIOVersionEntity
import com.baseflow.api.models.CreateEIORequest
import com.baseflow.api.models.EnkelvoudigInformatieObjectResponse
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.util.UUID

/**
 * Service for handling EnkelvoudigInformatieObject operations
 * Manages EIORecords and EIOVersions with proper transaction handling
 */
class EnkelvoudigInformatieObjectService {
    /**
     * Create a new EnkelvoudigInformatieObject
     * Creates both EIORecord and initial EIOVersion in a transaction
     */
    fun create(request: CreateEIORequest): EnkelvoudigInformatieObjectResponse {
        return transaction {
            val record = EIORecordEntity.new {
            }
            val version = EIOVersionEntity.new {
                recordId = record
                versie = 1
                taal = request.taal
                bestandsnaam = request.bestandsnaam
            }
            EnkelvoudigInformatieObjectResponse(
                id = record.id.value.toString(),
                versie = version.versie,
                taal = version.taal,
                bestandsnaam = version.bestandsnaam
            )
        }
    }

    /**
     * Get an EnkelvoudigInformatieObject by ID
     * Returns the latest version data
     */
    fun getById(id: UUID): EnkelvoudigInformatieObjectResponse? {
        return transaction {
            val record = EIORecordEntity.findById(id) ?: return@transaction null
            val version = record.versions.maxByOrNull { it.versie } ?: return@transaction null
            EnkelvoudigInformatieObjectResponse(
                id = record.id.value.toString(),
                versie = version.versie,
                taal = version.taal,
                bestandsnaam = version.bestandsnaam
            )
        }
    }

    /**
     * Get an EnkelvoudigInformatieObject by ID
     * Returns the latest version data
     */
    fun getAll(): List<EnkelvoudigInformatieObjectResponse> {
        return transaction {
            val record = EIORecordEntity.all()
            // get the latest version for each record
            record.mapNotNull { rec ->
                val version = rec.versions.maxByOrNull { it.versie }
                    ?: return@mapNotNull null
                EnkelvoudigInformatieObjectResponse(
                    id = rec.id.value.toString(),
                    versie = version.versie,
                    taal = version.taal,
                    bestandsnaam = version.bestandsnaam
                )
            }
        }
    }

    /**
     * Update an EnkelvoudigInformatieObject (creates new version)
     * Increments version and creates new EIOVersion in a transaction
     */
    fun update(id: UUID, request: CreateEIORequest): EnkelvoudigInformatieObjectResponse? {
        return transaction {
            val record = EIORecordEntity.findById(id) ?: return@transaction null
            val latestVersion = record.versions.maxByOrNull { it.versie }
            val newVersionNumber = (latestVersion?.versie ?: 1) + 1
            val version = EIOVersionEntity.new {
                recordId = record
                versie = newVersionNumber
                taal = request.taal
                bestandsnaam = request.bestandsnaam
            }
            EnkelvoudigInformatieObjectResponse(
                id = record.id.value.toString(),
                versie = version.versie,
                taal = version.taal,
                bestandsnaam = version.bestandsnaam
            )
        }
    }
}
