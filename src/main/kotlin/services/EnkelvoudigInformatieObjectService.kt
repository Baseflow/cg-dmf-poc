// SPDX-License-Identifier: EUPL-1.2
// Copyright (C) 2025-2026 Gemeente Utrecht
package com.baseflow.services

import com.baseflow.EIORecordEntity
import com.baseflow.EIORecords
import com.baseflow.EIOVersionEntity
import com.baseflow.EIOVersions
import com.baseflow.entities.OIORecords
import com.baseflow.api.ApiUrlBuilder
import com.baseflow.api.DOCUMENTEN_API_BASE_PATH
import com.baseflow.api.models.CreateEIORequest
import com.baseflow.api.models.EnkelvoudigInformatieObjectResponse
import com.baseflow.api.models.EnkelvoudigInformatieObjectStatus
import com.baseflow.api.models.Vertrouwelijkheidaanduiding
import com.baseflow.api.models.Integriteit
import com.baseflow.api.models.IntegriteitAlgoritme
import com.baseflow.api.models.Ondertekening
import com.baseflow.api.models.OndertekeningSoort
import com.baseflow.config.ApplicationConfig
import com.baseflow.services.models.LockPayload
import com.baseflow.services.models.LockResult
import com.baseflow.services.models.DeleteResult
import com.baseflow.services.models.QueryEnkelvoudigeInformatieObjectenFilter
import com.baseflow.services.models.UnlockResult
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atTime
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.exposed.v1.core.ArrayColumnType
import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.QueryBuilder
import org.jetbrains.exposed.v1.core.QueryParameter
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.jdbc.andWhere
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.dao.with
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.slf4j.LoggerFactory
import java.io.OutputStream
import java.util.UUID
import kotlin.io.encoding.Base64
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/**
 * Service for handling EnkelvoudigInformatieObject operations
 * Manages EIORecords and EIOVersions with proper transaction handling
 */
class EnkelvoudigInformatieObjectService {

    private val logger = LoggerFactory.getLogger(EnkelvoudigInformatieObjectService::class.java)
    private val storageService : StorageService
    private val applicationConfig : ApplicationConfig
    private val openZaakService : OpenZaakService

    constructor(storageService: StorageService, applicationConfig: ApplicationConfig, openZaakService: OpenZaakService) {
        this.storageService = storageService
        this.applicationConfig = applicationConfig
        this.openZaakService = openZaakService
    }

    /**
     * Create a new EnkelvoudigInformatieObject
     * Creates both EIORecord and initial EIOVersion in a transaction
     */
    @OptIn(ExperimentalTime::class)
    suspend fun create(request: CreateEIORequest): EnkelvoudigInformatieObjectResponse {
        return suspendTransaction {
            val record = EIORecordEntity.new {
            }

            // Validate informatieobjecttype against catalogus
            val ioType = openZaakService.validateInformatieobjecttype(request.informatieobjecttype)
            val version = 1;

            val locatie = (!request.inhoud.isNullOrEmpty() &&
                            request.bestandsomvang != null &&
                            request.bestandsomvang > 0 &&
                            request.bestandsnaam != null).let {
                "${record.id.value}/$version/${request.bestandsnaam}"
            } ?: ""

            storeFileVersion(request, locatie)

            val eioVersion = EIOVersionEntity.new {
                recordId = record
                versie = version
                bronOrganisatie = request.bronorganisatie
                informatieobject_type = request.informatieobjecttype
                taal = request.taal
                bestandsnaam = request.bestandsnaam.orEmpty()
                titel = request.titel
                auteur = request.auteur
                creatieDatum = request.creatiedatum
                beginRegistratie = Clock.System.now().toLocalDateTime(TimeZone.UTC)
                formaat = request.formaat.orEmpty()
                bestandsomvang = request.bestandsomvang ?: 0
                link = request.link.orEmpty()
                integriteitAlgoritme = request.integriteit?.algoritme?.toString().orEmpty()
                integriteitWaarde = request.integriteit?.waarde.orEmpty()
                integriteitsDatum = request.integriteit?.datum?.atTime(0,0,0,0)
                verschijningsVorm = request.verschijningsvorm.orEmpty()
                trefwoorden = request.trefwoorden ?: emptyList()
                vertrouwlijkheidsAanduiding = request.vertrouwelijkheidaanduiding?.toString()
                    ?: ioType?.vertrouwelijkheidaanduiding
                    ?: ""
                status = request.status?.toString().orEmpty()
                beschrijving = request.beschrijving.orEmpty()
                indicatieGebruiksrecht = request.indicatieGebruiksrecht ?: false
                ondertekening_soort = request.ondertekening?.soort?.toString().orEmpty()
                ondertekenings_datum = request.ondertekening?.datum?.atTime(0,0,0,0)
                identificatie = request.identificatie.orEmpty()
                bestandsLocatie = locatie
            }
            record.toResponse(eioVersion)
        }
    }

    private fun storeFileVersion(
        request: CreateEIORequest,
        bestandsLocatie: String,
    ) {
        if (!request.inhoud.isNullOrEmpty() &&
            (request.bestandsomvang ?: 0) > 0) {
            val content = Base64.decode(request.inhoud)
            storageService.uploadFile(bestandsLocatie, content)
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
            record.toResponse(version)
        }
    }

    fun exists(id: UUID): Boolean {
        return transaction {
            EIORecordEntity.findById(id) != null
        }
    }

    /**
     * Gets all EnkelvoudigInformatieObjects with their latest versions
     * Returns the latest versions of all records
     */
    fun getAll(filters: QueryEnkelvoudigeInformatieObjectenFilter): Pair<List<EnkelvoudigInformatieObjectResponse>, Long> {
        return transaction {
            val condition = buildFilterOp(filters)

            val pageSize = filters.pageSize
            val page = if (filters.page > 0) filters.page else 1
            val offset = (page - 1L) * pageSize

            // Base query: Record + Version, filtered and ordered by versie desc
            var query = EIORecords.innerJoin(EIOVersions)
                .selectAll()

            if (filters.objectUrl != null || filters.objectType != null) {
                query = EIORecords.innerJoin(EIOVersions).innerJoin(OIORecords)
                    .selectAll()
            }

            query.apply {
                    if (condition != Op.TRUE) {
                        andWhere { condition }
                    }
                }

            val totalCount = query.count()

            val records: List<EIORecordEntity> = EIORecordEntity.wrapRows(
                query.limit(pageSize).offset(offset)
            )
                .with(EIORecordEntity::versions)
                .toList()

            // get the latest version for each record
            val results = records.mapNotNull { rec ->
                val version = rec.versions.maxByOrNull { it.versie }
                    ?: return@mapNotNull null
                rec.toResponse(version)
            }

            results to totalCount
        }
    }

    /**
     * TODO this needs cleanup, should not expose this logic
     * Streams a file by its stored name. Use when you already know the object key.
     */
    fun streamByBestandsnaam(bestandsnaam: String, output: OutputStream) {
        storageService.downloadFileTo(bestandsnaam, output).join()
    }

    /**
     * Update an EnkelvoudigInformatieObject (creates new version)
     * Increments version and creates new EIOVersion in a transaction
     */
    @OptIn(ExperimentalTime::class)
    suspend fun update(id: UUID, request: CreateEIORequest): EnkelvoudigInformatieObjectResponse? {
        return suspendTransaction {
            val record = EIORecordEntity.findById(id) ?: return@suspendTransaction null

            // Validate informatieobjecttype against OpenZaak
            val ioType = openZaakService.validateInformatieobjecttype(request.informatieobjecttype)

            val latestVersion = record.versions.maxByOrNull { it.versie }
            val newVersionNumber = (latestVersion?.versie ?: 1) + 1

            // if we have new content, upload with new version number, otherwise use previous location
            val locatie = ( !request.inhoud.isNullOrEmpty() &&
                            request.bestandsomvang != null &&
                            request.bestandsomvang > 0 &&
                            request.bestandsnaam != null).let {
                latestVersion?.bestandsLocatie.orEmpty()
            } ?:"${record.id.value}/$newVersionNumber/${request.bestandsnaam}"

            storeFileVersion(request, locatie)

            // create a new version. If values in the request are empty, use existing values from latest version
            val version = EIOVersionEntity.new {
                recordId = record
                versie = newVersionNumber
                bronOrganisatie = request.bronorganisatie.ifEmpty { latestVersion?.bronOrganisatie.orEmpty() }
                informatieobject_type = request.informatieobjecttype.ifEmpty { latestVersion?.informatieobject_type.orEmpty() }
                taal = request.taal.ifEmpty { latestVersion?.taal.orEmpty() }
                bestandsnaam = request.bestandsnaam.orEmpty().ifEmpty { latestVersion?.bestandsnaam.orEmpty() }
                titel = request.titel.ifEmpty { latestVersion?.titel.orEmpty() }
                auteur = request.auteur.ifEmpty { latestVersion?.auteur.orEmpty() }
                bestandsLocatie = locatie
                beginRegistratie = Clock.System.now().toLocalDateTime(TimeZone.UTC)
                link = request.link.orEmpty()
                creatieDatum = request.creatiedatum
                formaat = request.formaat.orEmpty().ifEmpty { latestVersion?.formaat.orEmpty() }
                bestandsomvang = request.bestandsomvang ?: latestVersion?.bestandsomvang ?: 0
                integriteitAlgoritme = request.integriteit?.algoritme?.toString().orEmpty().ifEmpty { latestVersion?.integriteitAlgoritme.orEmpty() }
                integriteitWaarde = request.integriteit?.waarde.orEmpty().ifEmpty { latestVersion?.integriteitWaarde.orEmpty() }
                integriteitsDatum = request.integriteit?.datum?.atTime(0,0,0,0) ?: latestVersion?.integriteitsDatum
                verschijningsVorm = request.verschijningsvorm.orEmpty().ifEmpty { latestVersion?.verschijningsVorm.orEmpty() }
                trefwoorden = request.trefwoorden ?: latestVersion?.trefwoorden ?: emptyList()
                vertrouwlijkheidsAanduiding = request.vertrouwelijkheidaanduiding?.toString()
                    ?: ioType?.vertrouwelijkheidaanduiding
                            ?: ""
                status = request.status?.toString().orEmpty().ifEmpty { latestVersion?.status.orEmpty() }
                beschrijving = request.beschrijving.orEmpty().ifEmpty { latestVersion?.beschrijving.orEmpty() }
                indicatieGebruiksrecht = request.indicatieGebruiksrecht ?: latestVersion?.indicatieGebruiksrecht ?: false
                ondertekening_soort = request.ondertekening?.soort?.toString().orEmpty().ifEmpty { latestVersion?.ondertekening_soort.orEmpty() }
                ondertekenings_datum = request.ondertekening?.datum?.atTime(0,0,0,0) ?: latestVersion?.ondertekenings_datum
                identificatie = request.identificatie.orEmpty().ifEmpty { latestVersion?.identificatie.orEmpty() }
            }
            record.toResponse(version)
        }
    }

    @OptIn(ExperimentalTime::class)
    private fun EIORecordEntity.toResponse(
        version: EIOVersionEntity
    ): EnkelvoudigInformatieObjectResponse {

        val integriteit = when {
            version.integriteitAlgoritme.isNotEmpty() && version.integriteitsDatum != null -> Integriteit(
                algoritme = IntegriteitAlgoritme.valueOf(version.integriteitAlgoritme),
                waarde = version.integriteitWaarde,
                datum = version.integriteitsDatum!!.date
            )
            else -> null
        }

        val ondertekening = when {
            version.ondertekening_soort.isNotEmpty() && version.ondertekenings_datum != null -> Ondertekening(
                soort = OndertekeningSoort.valueOf(version.ondertekening_soort),
                datum = version.ondertekenings_datum!!.date
            )
            else -> null
        }

        val inhoudUrl = when {
            version.bestandsnaam.isNotEmpty() -> {
                val base = applicationConfig.baseUrl()
                "$base${DOCUMENTEN_API_BASE_PATH}/enkelvoudiginformatieobjecten/${this.id}/download?versie=${version.versie}"
            }
            else -> null
        }

        return EnkelvoudigInformatieObjectResponse(
            id = this.id.value.toString(),
            url = ApiUrlBuilder.absolute("enkelvoudiginformatieobjecten", this.id.value.toString()),
            identificatie = version.identificatie,
            bronorganisatie = version.bronOrganisatie,
            creatiedatum = version.creatieDatum,
            titel = version.titel,
            vertrouwelijkheidaanduiding = when {
                version.vertrouwlijkheidsAanduiding.isBlank() -> null
                else -> Vertrouwelijkheidaanduiding.valueOf(version.vertrouwlijkheidsAanduiding.uppercase())
            },
            auteur = version.auteur,
            // TODO this is to deal with empty string values in the DB, needs cleanup
            status = when {
                version.status.isBlank() -> null
                else -> EnkelvoudigInformatieObjectStatus.valueOf(version.status)
            },
            formaat = version.formaat.orEmpty(),
            taal = version.taal,
            bestandsnaam = version.bestandsnaam,
            inhoud = inhoudUrl,
            bestandsomvang = version.bestandsomvang,
            link = version.link,
            beschrijving = version.beschrijving,
            indicatieGebruiksrecht = version.indicatieGebruiksrecht,
            verschijningsvorm = version.verschijningsVorm,
            ondertekening = ondertekening,
            integriteit = integriteit,
            informatieobjecttype = version.informatieobject_type,
            trefwoorden = version.trefwoorden,
            inhoudIsVervallen = false, // Placeholder for inhoudIsVervallen
            locked = this.lockToken != null,
            versie = version.versie,
            beginRegistratie = version.beginRegistratie
                .toInstant(TimeZone.UTC)
                .toString(),
        )
    }

    private fun buildFilterOp(filters: QueryEnkelvoudigeInformatieObjectenFilter): Op<Boolean> {
        var op: Op<Boolean> = Op.TRUE

        filters.identificatie?.let { id ->
            op = op and (EIOVersions.identificatie eq id)
        }

        filters.bronOrganisatie?.let { bronOrganisatie ->
            op = op and (EIOVersions.bronOrganisatie eq bronOrganisatie)
        }

        if (filters.trefwoorden.isNotEmpty()) {
            op = op and arrayContainsAll(EIOVersions.trefwoorden, filters.trefwoorden)
        }

        if (filters.uuids.isNotEmpty()) {
            val uuids = filters.uuids.mapNotNull {
                try {
                    UUID.fromString(it)
                } catch (e: Exception) {
                    null
                }
            }
            if (uuids.isNotEmpty()) {
                op = op and (EIORecords.id inList uuids)
            }
        }

        filters.objectUrl?.let { objUrl ->
            op = op and (OIORecords.subjectObject eq objUrl)
        }

        filters.objectType?.let { objType ->
            op = op and (OIORecords.subjectType eq objType.lowercase())
        }

        return op
    }

    private fun arrayContainsAll(
        column: Column<List<String>>,
        values: List<String>
    ): Op<Boolean> = object : Op<Boolean>() {
        override fun toQueryBuilder(queryBuilder: QueryBuilder) {
            val arrayType = column.columnType as ArrayColumnType<String, *>
            val elementType = arrayType.delegate

            queryBuilder {
                append(column)
                append(" @> ")
                append("ARRAY[")

                values.forEachIndexed { index, value ->
                    if (index > 0) append(", ")
                    append(
                        QueryParameter(
                            value,
                            elementType
                        )
                    )
                }

                append("]")
            }
        }
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
            val current = record.lockToken ?: return@transaction UnlockResult.NotLocked
            if (current != lock) {
                return@transaction UnlockResult.InvalidLock
            }
            record.lockToken = null
            UnlockResult.Success
        }
    }

    fun delete(id: UUID): DeleteResult {
        return transaction {
            val record = EIORecordEntity.findById(id) ?: return@transaction DeleteResult.NotFound
            if (record.lockToken != null) {
                return@transaction DeleteResult.Locked
            }
            record.delete()
            DeleteResult.Success
        }
    }
}
