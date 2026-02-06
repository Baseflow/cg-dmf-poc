// SPDX-License-Identifier: EUPL-1.2
// Copyright (C) 2025-2026 Gemeente Utrecht
package com.baseflow.services

import AuditTrailEntity
import api.models.UploadResultaat
import com.baseflow.EIORecordEntity
import com.baseflow.EIORecords
import com.baseflow.EIOVersionEntity
import com.baseflow.EIOVersions
import com.baseflow.entities.OIORecords
import com.baseflow.api.ApiUrlBuilder
import com.baseflow.api.DOCUMENTEN_API_BASE_PATH
import com.baseflow.api.models.EnkelvoudigInformatieObjectRequest
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
    suspend fun create(request: EnkelvoudigInformatieObjectRequest): EnkelvoudigInformatieObjectResponse {
        return suspendTransaction {
            request.controleerVerplichteVelden()

            val record = EIORecordEntity.new {
            }

            // Validate informatieobjecttype against catalogus
            val ioType = openZaakService.validateInformatieobjecttype(request.informatieobjecttype!!)
            val version = 1

            var locatie = ""
            if (!request.inhoud.isNullOrEmpty() &&
                            request.bestandsomvang != null &&
                            request.bestandsomvang > 0 &&
                            request.bestandsnaam != null) {
                locatie = "${record.id.value}/$version/${request.bestandsnaam}"
            }

            val uploadResultaat = storeFileVersion(request, locatie)
            val bestandsOmvang = request.bestandsomvang ?: uploadResultaat?.bestandsOmvang ?: 0
            val bestandsFormaat = request.formaat ?: uploadResultaat?.bestandsFormaat

            if (!request.inhoud.isNullOrEmpty()) {
                require(bestandsFormaat != null) { "Unable to determine file format from content. Please specify the 'formaat' field in the request." }
            }

            val eioVersion = EIOVersionEntity.new {
                recordId = record
                versie = version
                bronOrganisatie = request.bronorganisatie!!
                informatieobject_type = request.informatieobjecttype
                taal = request.taal!!
                bestandsnaam = request.bestandsnaam.orEmpty()
                titel = request.titel!!
                auteur = request.auteur!!
                creatieDatum = request.creatiedatum!!
                beginRegistratie = Clock.System.now().toLocalDateTime(TimeZone.UTC)
                formaat = bestandsFormaat
                bestandsomvang = bestandsOmvang
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
        request: EnkelvoudigInformatieObjectRequest,
        bestandsLocatie: String,
    ): UploadResultaat? {
        if (!request.inhoud.isNullOrEmpty()) {
            val content = Base64.decode(request.inhoud)
            val fileType = StorageService.detectFileFormat(content)
            storageService.uploadFile(bestandsLocatie, content)
            return UploadResultaat(
                bestandsFormaat = fileType,
                bestandsOmvang = content.size.toLong()
            )
        }
        return null
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
     *
     * If `partial` is true, only non-empty fields in the request will be updated;
     * otherwise, all fields will be updated.
     *
     * If no new content is provided, the existing file location will be reused.
     */
    @OptIn(ExperimentalTime::class)
        suspend fun update(id: UUID, request: EnkelvoudigInformatieObjectRequest, partial: Boolean = false): EnkelvoudigInformatieObjectResponse? {
        return suspendTransaction {

            if (!partial) {
                request.controleerVerplichteVelden()
            }

            val record = EIORecordEntity.findById(id) ?: return@suspendTransaction null

            // Validate informatieobjecttype against OpenZaak
            val latestVersion = record.versions.maxByOrNull { it.versie }
            val newVersionNumber = (latestVersion?.versie ?: 1) + 1

            if (!request.informatieobjecttype.isNullOrEmpty()) {
                openZaakService.validateInformatieobjecttype(
                    request.informatieobjecttype)
            }

            var locatie = latestVersion?.bestandsLocatie.orEmpty()
            if ( !request.inhoud.isNullOrEmpty() &&
                            request.bestandsomvang != null &&
                            request.bestandsomvang > 0 &&
                            request.bestandsnaam != null) {
                // if we have new content, upload with new version number, otherwise use previous location
                locatie = "${record.id.value}/$newVersionNumber/${request.bestandsnaam}"
            }

            val uploadResultaat = storeFileVersion(request, locatie)
            val bestandsOmvang = if (partial && request.bestandsomvang == null) uploadResultaat?.bestandsOmvang ?: latestVersion?.bestandsomvang else request.bestandsomvang
            val bestandsFormaat = if (partial && request.formaat == null) uploadResultaat?.bestandsFormaat ?: latestVersion?.formaat else request.formaat

            if (!partial && !request.inhoud.isNullOrEmpty()) {
                require(bestandsFormaat != null) { "Unable to determine file format from content. Please specify the 'formaat' field in the request." }
            }

            AuditTrailEntity.new {
                this.bron = "EnkelvoudigInformatieObjectService"
                this.actie = "UPDATE"
                this.hoofdObject = record.id.value.toString()
                this.resource = "enkelvoudiginformatieobjecten"
                this.resourceUrl = ApiUrlBuilder.absolute("enkelvoudiginformatieobjecten", record.id.value.toString())
                this.resultaat = 200
                this.wijzigingen = "Updated to version $newVersionNumber"
            }

            // create a new version. If values in the request are empty,
            // use existing values from latest version but only if the update is not partial
            val version = EIOVersionEntity.new {
                recordId = record
                versie = newVersionNumber
                bronOrganisatie = if (partial && request.bronorganisatie.isNullOrEmpty()) latestVersion?.bronOrganisatie.orEmpty() else request.bronorganisatie!!
                informatieobject_type = if (partial && request.informatieobjecttype.isNullOrEmpty()) latestVersion?.informatieobject_type.orEmpty() else request.informatieobjecttype!!
                taal = if (partial && request.taal.isNullOrEmpty()) latestVersion?.taal.orEmpty() else request.taal!!
                bestandsnaam = if (partial && request.bestandsnaam.isNullOrEmpty()) latestVersion?.bestandsnaam.orEmpty() else request.bestandsnaam.orEmpty()
                titel = if (partial && request.titel.isNullOrEmpty()) latestVersion?.titel.orEmpty() else request.titel!!
                auteur = if (partial && request.auteur.isNullOrEmpty()) latestVersion?.auteur.orEmpty() else request.auteur!!
                bestandsLocatie = locatie
                beginRegistratie = Clock.System.now().toLocalDateTime(TimeZone.UTC)
                link = if (partial && request.link.isNullOrEmpty()) latestVersion?.link.orEmpty() else request.link.orEmpty()
                creatieDatum = if (partial && request.creatiedatum == null) latestVersion?.creatieDatum ?: Clock.System.now().toLocalDateTime(TimeZone.UTC).date else request.creatiedatum!!
                formaat = bestandsFormaat
                bestandsomvang = if (partial && request.bestandsomvang == null) latestVersion?.bestandsomvang ?: 0 else bestandsOmvang
                integriteitAlgoritme = if (partial && request.integriteit?.algoritme == null) latestVersion?.integriteitAlgoritme.orEmpty() else request.integriteit?.algoritme?.toString().orEmpty()
                integriteitWaarde = if (partial && request.integriteit?.waarde.isNullOrEmpty()) latestVersion?.integriteitWaarde.orEmpty() else request.integriteit?.waarde.orEmpty()
                integriteitsDatum = if (partial && request.integriteit?.datum == null) latestVersion?.integriteitsDatum else request.integriteit?.datum?.atTime(0,0,0,0)
                verschijningsVorm = if (partial && request.verschijningsvorm.isNullOrEmpty()) latestVersion?.verschijningsVorm.orEmpty() else request.verschijningsvorm.orEmpty()
                trefwoorden = if (partial && request.trefwoorden.isNullOrEmpty()) latestVersion?.trefwoorden ?: emptyList() else request.trefwoorden ?: emptyList()
                vertrouwlijkheidsAanduiding = if (partial && request.vertrouwelijkheidaanduiding == null) latestVersion?.vertrouwlijkheidsAanduiding ?: "" else request.vertrouwelijkheidaanduiding?.toString() ?: ""
                status = if (partial && request.status == null) latestVersion?.status.orEmpty() else request.status?.toString().orEmpty()
                beschrijving = if (partial && request.beschrijving.isNullOrEmpty()) latestVersion?.beschrijving.orEmpty() else request.beschrijving.orEmpty()
                indicatieGebruiksrecht = if (partial && request.indicatieGebruiksrecht == null) latestVersion?.indicatieGebruiksrecht ?: false else request.indicatieGebruiksrecht ?: false
                ondertekening_soort = if (partial && request.ondertekening?.soort == null) latestVersion?.ondertekening_soort.orEmpty() else request.ondertekening?.soort?.toString().orEmpty()
                ondertekenings_datum = if (partial && request.ondertekening?.datum == null) latestVersion?.ondertekenings_datum else request.ondertekening?.datum?.atTime(0,0,0,0)
                identificatie = if (partial && request.identificatie.isNullOrEmpty()) latestVersion?.identificatie.orEmpty() else request.identificatie.orEmpty()
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
                } catch (_: Exception) {
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
