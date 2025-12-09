// SPDX-License-Identifier: EUPL-1.2
// Copyright (C) 2025 Gemeente Utrecht
package com.baseflow.services

import com.baseflow.EIORecordEntity
import com.baseflow.EIOVersionEntity
import com.baseflow.api.models.CreateEIORequest
import com.baseflow.api.models.EnkelvoudigInformatieObjectResponse
import com.baseflow.api.models.Integriteit
import com.baseflow.api.models.Ondertekening
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.util.UUID

/**
 * Service for handling EnkelvoudigInformatieObject operations
 * Manages EIORecords and EIOVersions with proper transaction handling
 */
class EnkelvoudigInformatieObjectService {
    @kotlinx.serialization.Serializable
    data class LockPayload(val lock: String)

    sealed class LockResult {
        data class Success(val payload: LockPayload) : LockResult()
        data object AlreadyLocked : LockResult()
    }

    sealed class UnlockResult {
        data object Success : UnlockResult()
        data object InvalidLock : UnlockResult()
        data object NotLocked : UnlockResult()
    }
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
                taal = request.taal.orEmpty()
                bestandsnaam = request.bestandsnaam.orEmpty()
            }
            mapToResponse(record, version)
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
            mapToResponse(record, version)
        }
    }

    /**
     * Gets all EnkelvoudigInformatieObjects with their latest versions
     * Returns the latest versions of all records
     */
    fun getAll(): List<EnkelvoudigInformatieObjectResponse> {
        return transaction {
            val record = EIORecordEntity.all()
            // get the latest version for each record
            record.mapNotNull { rec ->
                val version = rec.versions.maxByOrNull { it.versie }
                    ?: return@mapNotNull null
                mapToResponse(rec, version)
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
                taal = request.taal.orEmpty()
                bestandsnaam = request.bestandsnaam.orEmpty()
            }
            mapToResponse(record, version)
        }
    }

    fun mapToResponse(
        record: EIORecordEntity,
        version: EIOVersionEntity
    ): EnkelvoudigInformatieObjectResponse {
        return EnkelvoudigInformatieObjectResponse(
            identificatie = record.id.value.toString(),
            bronorganisatie = version.bronOrganisatie,
            creatiedatum = version.creatieDatum,
            titel = version.titel,
            vertrouwelijkheidaanduiding = version.vertrouwlijkheidsAanduiding,
            auteur = version.auteur,
            status = version.status,
            formaat = version.formaat.orEmpty(),
            taal = version.taal,
            bestandsnaam = version.bestandsnaam,
            inhoud = "", // Placeholder for inhoud
            bestandsomvang = version.bestandsomvang,
            link = version.link,
            beschrijving = version.beschrijving,
            indicatieGebruiksrecht = version.indicatieGebruiksrecht,
            verschijningsvorm = version.verschijningsVorm,
            ondertekening = Ondertekening(
                soort = version.ondertekening_soort,
                datum = version.ondertekenings_datum?.toString().orEmpty()
            ),
            integriteit = Integriteit(
                algoritme = version.integriteitAlgoritme,
                waarde = version.integriteitWaarde,
                datum = version.integriteitsDatum?.toString().orEmpty()
            ),
            informatieobjecttype = "EnkelvoudigInformatieObject",
            trefwoorden = version.trefwoorden,
            inhoudIsVervallen = false, // Placeholder for inhoudIsVervallen
            locked = record.lockToken != null,
            versie = version.versie,
            beginRegistratie = version.beginRegistratie,
        )
    }

    fun lock(id: UUID): LockResult? {
        return transaction {
            val record = EIORecordEntity.findById(id) ?: return@transaction null
            if (record.lockToken != null) {
                return@transaction LockResult.AlreadyLocked
            }
            val token = UUID.randomUUID().toString()
            record.lockToken = token
            LockResult.Success(LockPayload(lock = token))
        }
    }

    fun unlock(id: UUID, lock: String): UnlockResult? {
        return transaction {
            val record = EIORecordEntity.findById(id) ?: return@transaction null
            val current = record.lockToken
            if (current == null) {
                return@transaction UnlockResult.NotLocked
            }
            if (current != lock) {
                return@transaction UnlockResult.InvalidLock
            }
            record.lockToken = null
            UnlockResult.Success
        }
    }
}
