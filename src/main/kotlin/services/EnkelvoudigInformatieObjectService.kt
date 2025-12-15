// SPDX-License-Identifier: EUPL-1.2
// Copyright (C) 2025 Gemeente Utrecht
package com.baseflow.services

import com.baseflow.EIORecordEntity
import com.baseflow.EIORecords
import com.baseflow.EIOVersionEntity
import com.baseflow.EIOVersions
import com.baseflow.api.ApiUrlBuilder
import com.baseflow.api.DOCUMENTEN_API_BASE_PATH
import com.baseflow.api.models.CreateEIORequest
import com.baseflow.api.models.EnkelvoudigInformatieObjectResponse
import com.baseflow.api.models.EnkelvoudigInformatieObjectStatus
import com.baseflow.api.models.Integriteit
import com.baseflow.api.models.IntegriteitAlgoritme
import com.baseflow.api.models.Ondertekening
import com.baseflow.api.models.OndertekeningSoort
import com.baseflow.config.ApplicationConfig
import com.baseflow.config.OpenZaakConfig
import com.baseflow.services.models.LockPayload
import com.baseflow.services.models.LockResult
import com.baseflow.services.models.DeleteResult
import com.baseflow.services.models.QueryEnkelvoudigeInformatieObjectenFilter
import com.baseflow.services.models.UnlockResult
import io.ktor.client.HttpClient
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atTime
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.exposed.v1.core.ArrayColumnType
import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.JoinType
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.QueryBuilder
import org.jetbrains.exposed.v1.core.QueryParameter
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.andWhere
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.dao.with
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
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

    constructor(storageService: StorageService, applicationConfig: ApplicationConfig) {
        this.storageService = storageService
        this.applicationConfig = applicationConfig
    }

    /**
     * Create a new EnkelvoudigInformatieObject
     * Creates both EIORecord and initial EIOVersion in a transaction
     */
    @OptIn(ExperimentalTime::class)
    fun create(request: CreateEIORequest): EnkelvoudigInformatieObjectResponse {
        return transaction {
            val record = EIORecordEntity.new {
            }

            if (!request.inhoud.isNullOrEmpty() &&
                (request.bestandsomvang ?: 0) > 0 &&
                !request.bestandsnaam.isNullOrEmpty()
            ) {
                val content = Base64.decode(request.inhoud)
                storageService.uploadFile(request.bestandsnaam, content)
            }

            val jwtToken = OpenZaakConfig.generateJwtToken()
            val httpClient = HttpClient();
            httpClient.
            // make http get request to openzaak.endpoint + /catalogi/api/v1/informatieobjecttypen + request.informatieobjecttype



            val version = EIOVersionEntity.new {
                recordId = record
                versie = 1
                bronOrganisatie = request.bronorganisatie
//                informatieobject type = request.informatieobjecttype
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
                vertrouwlijkheidsAanduiding = request.vertrouwelijkheidaanduiding.orEmpty()
                status = request.status?.toString().orEmpty()
                beschrijving = request.beschrijving.orEmpty()
                indicatieGebruiksrecht = request.indicatieGebruiksrecht ?: false
                ondertekening_soort = request.ondertekening?.soort?.toString().orEmpty()
                ondertekenings_datum = request.ondertekening?.datum?.atTime(0,0,0,0)
                identificatie = request.identificatie.orEmpty()

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

    fun exists(id: UUID): Boolean {
        return transaction {
            EIORecordEntity.findById(id) != null
        }
    }

    /**
     * Gets all EnkelvoudigInformatieObjects with their latest versions
     * Returns the latest versions of all records
     */
    fun getAll(filters: QueryEnkelvoudigeInformatieObjectenFilter): List<EnkelvoudigInformatieObjectResponse> {
        return transaction {
            val condition = buildFilterOp(filters)

            val pageSize = 10
            val page = if (filters.page > 0) filters.page else 1
            val offset = (page - 1L) * pageSize

            // Base query: Record + Version, filtered and ordered by versie desc
            val query = EIORecords
                .selectAll()
                .apply {
                    if (condition != Op.TRUE) {
                        andWhere { condition }
                    }
                }
                .offset(offset)
                .limit(pageSize)

            val records: List<EIORecordEntity> = EIORecordEntity.wrapRows(query)
                .with(EIORecordEntity::versions)
                .toList()

            // get the latest version for each record
            records.mapNotNull { rec ->
                val version = rec.versions.maxByOrNull { it.versie }
                    ?: return@mapNotNull null
                mapToResponse(rec, version)
            }
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
    fun update(id: UUID, request: CreateEIORequest): EnkelvoudigInformatieObjectResponse? {
        return transaction {
            val record = EIORecordEntity.findById(id) ?: return@transaction null
            val latestVersion = record.versions.maxByOrNull { it.versie }
            val newVersionNumber = (latestVersion?.versie ?: 1) + 1
            val version = EIOVersionEntity.new {
                recordId = record
                versie = newVersionNumber
                taal = request.taal
                bestandsnaam = request.bestandsnaam.orEmpty()
                titel = request.titel
                auteur = request.auteur
                creatieDatum = request.creatiedatum
                status = request.status?.toString().orEmpty()
                beginRegistratie = Clock.System.now().toLocalDateTime(TimeZone.UTC)
            }
            mapToResponse(record, version)
        }
    }

    @OptIn(ExperimentalTime::class)
    fun mapToResponse(
        record: EIORecordEntity,
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
                val base = this.applicationConfig.baseUrl()
                "$base${DOCUMENTEN_API_BASE_PATH}/enkelvoudiginformatieobjecten/${record.id}/download?versie=${version.versie}"
            }
            else -> null
        }

        return EnkelvoudigInformatieObjectResponse(
            id = record.id.value.toString(),
            url = ApiUrlBuilder.absolute("enkelvoudiginformatieobjecten", record.id.value.toString()),
            identificatie = version.identificatie,
            bronorganisatie = version.bronOrganisatie,
            creatiedatum = version.creatieDatum,
            titel = version.titel,
            vertrouwelijkheidaanduiding = version.vertrouwlijkheidsAanduiding,
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
            informatieobjecttype = "EnkelvoudigInformatieObject",
            trefwoorden = version.trefwoorden,
            inhoudIsVervallen = false, // Placeholder for inhoudIsVervallen
            locked = record.lockToken != null,
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
