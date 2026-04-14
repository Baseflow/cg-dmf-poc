// SPDX-License-Identifier: EUPL-1.2
// Copyright (C) 2025-2026 Gemeente Utrecht
package com.baseflow.services

import com.baseflow.api.ApiUrlBuilder
import com.baseflow.api.DOCUMENTEN_API_BASE_PATH
import com.baseflow.api.middleware.AuditContext
import com.baseflow.api.models.*
import com.baseflow.config.ApplicationConfig
import com.baseflow.config.RequestScope
import com.baseflow.entities.*
import com.baseflow.services.models.*
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atTime
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.json.JsonObject
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.jdbc.andWhere
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.koin.core.annotation.Scope
import org.koin.core.annotation.Scoped
import java.io.OutputStream
import java.util.*
import kotlin.io.encoding.Base64
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/**
 * Service for handling EnkelvoudigInformatieObject operations
 * Manages EIORecords and EIOVersions with proper transaction handling
 */
@Scope(RequestScope::class)
@Scoped
class EnkelvoudigInformatieObjectService(
    private val storageService: StorageService,
    private val applicationConfig: ApplicationConfig,
    private val catalogusService: CatalogusService,
    private val auditTrailService: AuditTrailService,
    private val auditContext: AuditContext,
    private val bestandsDeelService: BestandsDeelService,
) {

    /**
     * Create a new EnkelvoudigInformatieObject
     * Creates both EIORecord and initial EIOVersion in a transaction
     */
    @OptIn(ExperimentalTime::class)
    suspend fun create(request: EnkelvoudigInformatieObjectRequest): EnkelvoudigInformatieObjectResponse = suspendTransaction {
        request.controleerVerplichteVelden()

        val record = EIORecordEntity.new {
        }

        // Validate informatieobjecttype against catalogus
        val ioType = catalogusService.validateInformatieobjecttype(request.informatieobjecttype!!)
        val version = 1

        val uploadResultaat = getUploadResultaat(request, record, version)
        val bestandsOmvang = request.bestandsomvang ?: uploadResultaat.bestandsOmvang
        val bestandsFormaat = request.formaat ?: uploadResultaat.bestandsFormaat

        if (!request.inhoud.isNullOrEmpty()) {
            require(bestandsFormaat != null) {
                "Unable to determine file format from content. Please specify the 'formaat' field in the request."
            }
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
            integriteitsDatum = request.integriteit?.datum?.atTime(0, 0, 0, 0)
            verschijningsVorm = request.verschijningsvorm.orEmpty()
            trefwoorden = request.trefwoorden ?: emptyList()
            vertrouwlijkheidsAanduiding = request.vertrouwelijkheidaanduiding?.toString()
                ?: ioType?.vertrouwelijkheidaanduiding
                ?: ""
            status = request.status?.toString().orEmpty()
            beschrijving = request.beschrijving.orEmpty()
            indicatieGebruiksrecht = request.indicatieGebruiksrecht ?: false
            ondertekening_soort = request.ondertekening?.soort?.toString().orEmpty()
            ondertekenings_datum = request.ondertekening?.datum?.atTime(0, 0, 0, 0)
            identificatie = request.identificatie.orEmpty()
            bestandsLocatie = uploadResultaat.bestandsLocatie
        }

        // When the declared file size exceeds the trigger threshold, lock the record and
        // create the bestandsdelen rows so the API consumer can upload the parts individually.
        val bestandsDelen: List<BestandsDeelResponse>
        if (request.inhoud.isNullOrEmpty() && bestandsDeelService.requiresChunking(request.bestandsomvang)) {
            val lockToken = UUID.randomUUID().toString()
            record.lockToken = lockToken
            bestandsDelen = bestandsDeelService.createBestandsDelen(eioVersion, bestandsOmvang!!, lockToken)
        } else {
            bestandsDelen = emptyList()
        }

        val response = record.toResponse(eioVersion, bestandsDelen)
        auditContext.captureNew(response, eioVersion)
        response as EnkelvoudigInformatieObjectResponse
    }

    private fun getUploadResultaat(
        request: EnkelvoudigInformatieObjectRequest,
        record: EIORecordEntity,
        version: Int = 1,
        locatie: String = "",
    ): UploadResultaat {
        var loc = locatie
        if (!request.isFileEmpty()) {
            loc = "${record.id.value}/$version/${request.bestandsnaam}"
        }
        return storeFileVersion(request, loc)
    }

    private fun storeFileVersion(request: EnkelvoudigInformatieObjectRequest, bestandsLocatie: String): UploadResultaat {
        if (!request.inhoud.isNullOrEmpty() && !request.isFileEmpty()) {
            val content = Base64.decode(request.inhoud)
            val fileType = StorageService.detectFileFormat(content)
            require(bestandsLocatie.isNotBlank()) {
                "bestandsLocatie must not be blank when inhoud is present"
            }
            storageService.uploadFile(bestandsLocatie, content)
            return UploadResultaat(
                bestandsFormaat = fileType,
                bestandsOmvang = content.size.toLong(),
                bestandsLocatie = bestandsLocatie,
            )
        }
        return UploadResultaat(bestandsLocatie = bestandsLocatie)
    }

    /**
     * Get an EnkelvoudigInformatieObject by ID
     * Returns the latest version data
     */
    suspend fun getById(id: UUID, expand: List<String> = emptyList()): EnkelvoudigInformatieObjectResponse? {
        val response = transaction {
            val record = EIORecordEntity.findById(id) ?: return@transaction null
            val version = record.versions.maxByOrNull { it.versie } ?: return@transaction null
            val bestandsDelen = bestandsDeelService.getBestandsDelen(version)
            record.toResponse(version, bestandsDelen)
        } ?: return null

        return resolveExpand(response, expand)
    }

    fun exists(id: UUID): Boolean = transaction {
        EIORecordEntity.findById(id) != null
    }

    /**
     * Gets all EnkelvoudigInformatieObjects with their latest versions
     * Returns the latest versions of all records
     */
    suspend fun getAll(filters: QueryEnkelvoudigeInformatieObjectenFilter): Pair<List<EnkelvoudigInformatieObjectResponse>, Long> {
        val (results, totalCount) = suspendTransaction {
            val condition = buildFilterOp(filters)

            val pageSize = filters.pageSize
            val page = if (filters.page > 0) filters.page else 1
            val offset = (page - 1L) * pageSize

            // Build the base join. Each row in this query represents (record, latestVersion).
            // We restrict to only the latest version per record via a correlated subquery.
            var query = EIORecords.innerJoin(EIOVersions)
                .selectAll()

            if (filters.objectUrl != null || filters.objectType != null) {
                query = EIORecords.innerJoin(EIOVersions).innerJoin(OIORecords)
                    .selectAll()
            }

            // Alias for the inner subquery table so the correlated reference to the outer EIORecords.id
            // is unambiguous — without this alias both inner and outer reference the same table name,
            // and PostgreSQL evaluates the subquery as non-correlated (returning a single global MAX).
            val innerVersions = EIOVersions.alias("inner_eio_versions")

            query.apply {
                // Restrict to only the latest version row per record via a correlated subquery:
                // WHERE versie = (SELECT MAX(inner.versie) FROM eio_versions AS inner WHERE inner.record_id = eio_records.id)
                andWhere {
                    EIOVersions.versie eqSubQuery innerVersions
                        .select(innerVersions[EIOVersions.versie].max())
                        .where { innerVersions[EIOVersions.recordId] eq EIORecords.id }
                }
                if (condition != Op.TRUE) {
                    andWhere { condition }
                }
                if (filters.ordering.isNotEmpty()) {
                    val orderClauses = filters.ordering.map { ordering ->
                        val sortOrder = if (ordering.value.startsWith("-")) SortOrder.DESC else SortOrder.ASC
                        when (ordering) {
                            EIOOrdering.AUTEUR_ASC, EIOOrdering.AUTEUR_DESC ->
                                EIOVersions.auteur to sortOrder

                            EIOOrdering.BESTANDSOMVANG_ASC, EIOOrdering.BESTANDSOMVANG_DESC ->
                                EIOVersions.bestandsomvang to sortOrder

                            EIOOrdering.CREATIEDATUM_ASC, EIOOrdering.CREATIEDATUM_DESC ->
                                EIOVersions.creatieDatum to sortOrder

                            EIOOrdering.FORMAAT_ASC, EIOOrdering.FORMAAT_DESC ->
                                EIOVersions.formaat to sortOrder

                            EIOOrdering.STATUS_ASC, EIOOrdering.STATUS_DESC ->
                                EIOVersions.status to sortOrder

                            EIOOrdering.TITEL_ASC, EIOOrdering.TITEL_DESC ->
                                EIOVersions.titel to sortOrder

                            EIOOrdering.VERTROUWELIJKHEIDAANDUIDING_ASC, EIOOrdering.VERTROUWELIJKHEIDAANDUIDING_DESC ->
                                EIOVersions.vertrouwlijkheidsAanduiding to sortOrder
                        }
                    }
                    orderBy(*orderClauses.toTypedArray())
                }
            }

            val totalCount = query.count()

            // Materialise the page rows first so we can collect all version IDs for a
            // single batch bestandsdelen query instead of one query per row (N+1).
            val pageRows = query.limit(pageSize).offset(offset).toList()
            val versionIds = pageRows.map { EIOVersionEntity.wrapRow(it).id.value }
            val bestandsDelenByVersion = bestandsDeelService.getBestandsDelenForVersions(versionIds)

            // Read each ResultRow directly to avoid wrapRows producing duplicate entity instances.
            // Each row already contains exactly one record + its latest version due to the subquery filter.
            val results = pageRows.mapNotNull { row ->
                val record = EIORecordEntity.wrapRow(row)
                val version = EIOVersionEntity.wrapRow(row)
                val bestandsdelen = bestandsDelenByVersion[version.id.value] ?: emptyList()
                record.toResponse(version, bestandsdelen)
            }

            results to totalCount
        }

        val expandedResults = results.map { resolveExpand(it, filters.expand) }
        return expandedResults to totalCount
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
    suspend fun update(
        id: UUID,
        request: EnkelvoudigInformatieObjectRequest,
        partial: Boolean = false,
    ): EnkelvoudigInformatieObjectResponse? {
        return suspendTransaction {
            if (!partial) {
                request.controleerVerplichteVelden()
            }

            val record = EIORecordEntity.findById(id) ?: return@suspendTransaction null

            val latestVersion = record.versions.maxByOrNull { it.versie }
            auditContext.captureOld(record.toResponse(latestVersion))
            val newVersionNumber = (latestVersion?.versie ?: 1) + 1

            // Validate informatieobjecttype against Catalogus
            if (!request.informatieobjecttype.isNullOrEmpty()) {
                catalogusService.validateInformatieobjecttype(request.informatieobjecttype)
            }

            val uploadResultaat =
                getUploadResultaat(request, record, newVersionNumber, latestVersion?.bestandsLocatie.orEmpty())
            val bestandsFormaat =
                mergeNullable(
                    partial,
                    request.formaat,
                    uploadResultaat.bestandsFormaat
                        ?: latestVersion?.formaat,
                )

            if (!partial && !request.inhoud.isNullOrEmpty()) {
                require(bestandsFormaat != null) {
                    "Unable to determine file format from content. Please specify the 'formaat' field in the request."
                }
            }

            // create a new version. If values in the request are empty,
            // use existing values from latest version but only if the update is partial
            val version = EIOVersionEntity.new {
                recordId = record
                versie = newVersionNumber
                bronOrganisatie = mergeString(partial, request.bronorganisatie, latestVersion?.bronOrganisatie)
                informatieobject_type =
                    mergeString(partial, request.informatieobjecttype, latestVersion?.informatieobject_type)
                taal = mergeString(partial, request.taal, latestVersion?.taal)
                bestandsnaam = mergeOptionalString(partial, request.bestandsnaam, latestVersion?.bestandsnaam)
                titel = mergeString(partial, request.titel, latestVersion?.titel)
                auteur = mergeString(partial, request.auteur, latestVersion?.auteur)
                bestandsLocatie = uploadResultaat.bestandsLocatie
                beginRegistratie = Clock.System.now().toLocalDateTime(TimeZone.UTC)
                link = mergeOptionalString(partial, request.link, latestVersion?.link)
                creatieDatum = mergeNullable(partial, request.creatiedatum, latestVersion?.creatieDatum)
                    ?: Clock.System.now()
                        .toLocalDateTime(TimeZone.UTC).date
                formaat = bestandsFormaat
                bestandsomvang = mergeNullable(
                    partial,
                    request.bestandsomvang,
                    latestVersion?.bestandsomvang,
                ) ?: 0

                integriteitAlgoritme = mergeOptionalString(
                    partial,
                    request.integriteit?.algoritme?.toString(),
                    latestVersion?.integriteitAlgoritme,
                )
                integriteitWaarde =
                    mergeOptionalString(partial, request.integriteit?.waarde, latestVersion?.integriteitWaarde)
                integriteitsDatum = mergeNullable(
                    partial,
                    request.integriteit?.datum?.atTime(0, 0, 0, 0),
                    latestVersion?.integriteitsDatum,
                )
                verschijningsVorm =
                    mergeOptionalString(partial, request.verschijningsvorm, latestVersion?.verschijningsVorm)
                trefwoorden = mergeNullable(partial, request.trefwoorden?.ifEmpty { null }, latestVersion?.trefwoorden)
                    ?: emptyList()
                vertrouwlijkheidsAanduiding = mergeOptionalString(
                    partial,
                    request.vertrouwelijkheidaanduiding?.toString(),
                    latestVersion?.vertrouwlijkheidsAanduiding,
                )
                status = mergeOptionalString(partial, request.status?.toString(), latestVersion?.status)
                beschrijving = mergeOptionalString(partial, request.beschrijving, latestVersion?.beschrijving)
                indicatieGebruiksrecht =
                    mergeNullable(
                        partial,
                        request.indicatieGebruiksrecht,
                        latestVersion?.indicatieGebruiksrecht,
                    ) ?: false
                ondertekening_soort = mergeOptionalString(
                    partial,
                    request.ondertekening?.soort?.toString(),
                    latestVersion?.ondertekening_soort,
                )
                ondertekenings_datum = mergeNullable(
                    partial,
                    request.ondertekening?.datum?.atTime(0, 0, 0, 0),
                    latestVersion?.ondertekenings_datum,
                )
                identificatie = mergeOptionalString(partial, request.identificatie, latestVersion?.identificatie)
            }

            // Determine effective bestandsomvang for the new version and create bestandsdelen if needed.
            val effectiveOmvang = version.bestandsomvang
            val bestandsDelen: List<BestandsDeelResponse>
            // We only want to create bestandsdelen if bestandsomvang is specified and changed
            if (request.inhoud.isNullOrEmpty() &&
                request.bestandsomvang != null &&
                request.bestandsomvang != latestVersion?.bestandsomvang &&
                bestandsDeelService.requiresChunking(effectiveOmvang)
            ) {
                val lockToken = record.lockToken ?: run {
                    val newToken = UUID.randomUUID().toString()
                    record.lockToken = newToken
                    newToken
                }
                bestandsDelen = bestandsDeelService.createBestandsDelen(version, effectiveOmvang!!, lockToken)
            } else {
                bestandsDelen = bestandsDeelService.getBestandsDelen(version)
            }

            val response = record.toResponse(version, bestandsDelen)
            auditContext.captureNew(response, version)
            response
        }
    }

    /**
     * In a partial update, returns [fallback] when [newValue] is null; otherwise returns [newValue].
     * In a full update, always returns [newValue].
     */
    private fun <T> mergeNullable(partial: Boolean, newValue: T?, fallback: T?): T? =
        if (partial && newValue == null) fallback else newValue

    /**
     * Merges a required string field: in a partial update falls back to [fallback] when [newValue]
     * is null or empty; in a full update always uses [newValue] (assumed non-null by caller).
     */
    private fun mergeString(partial: Boolean, newValue: String?, fallback: String?): String =
        if (partial && newValue.isNullOrEmpty()) fallback.orEmpty() else newValue!!

    /**
     * Merges an optional string field (empty string is a valid value).
     * In a partial update falls back to [fallback] when [newValue] is null or empty.
     */
    private fun mergeOptionalString(partial: Boolean, newValue: String?, fallback: String?): String =
        if (partial && newValue.isNullOrEmpty()) fallback.orEmpty() else newValue.orEmpty()

    @OptIn(ExperimentalTime::class)
    private fun EIORecordEntity.toResponse(
        version: EIOVersionEntity?,
        bestandsdelen: List<BestandsDeelResponse> = emptyList(),
    ): EnkelvoudigInformatieObjectResponse? {
        if (version == null) return null

        val integriteit = when {
            version.integriteitAlgoritme.isNotEmpty() && version.integriteitsDatum != null -> Integriteit(
                algoritme = IntegriteitAlgoritme.valueOf(version.integriteitAlgoritme),
                waarde = version.integriteitWaarde,
                datum = version.integriteitsDatum!!.date,
            )

            else -> null
        }

        val ondertekening = when {
            version.ondertekening_soort.isNotEmpty() && version.ondertekenings_datum != null -> Ondertekening(
                soort = OndertekeningSoort.valueOf(version.ondertekening_soort),
                datum = version.ondertekenings_datum!!.date,
            )

            else -> null
        }

        val inhoudUrl = when {
            version.bestandsnaam.isNotEmpty() -> {
                val base = applicationConfig.baseUrl()
                "$base${DOCUMENTEN_API_BASE_PATH}/${ResourceSegments.ENKELVOUDIG_INFORMATIE_OBJECTEN.value}/${this.id}/download?versie=${version.versie}"
            }

            else -> null
        }

        return EnkelvoudigInformatieObjectResponse(
            id = this.id.value.toString(),
            url = ApiUrlBuilder.absolute(
                ResourceSegments.ENKELVOUDIG_INFORMATIE_OBJECTEN.value,
                this.id.value.toString(),
            ),
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
            lock = this.lockToken.orEmpty(),
            bestandsdelen = bestandsdelen,
        )
    }

    /**
     * Resolves the _expand field on a response based on the requested expand fields.
     * Currently supports expanding "informatieobjecttype".
     */
    private suspend fun resolveExpand(
        response: EnkelvoudigInformatieObjectResponse,
        expand: List<String>,
    ): EnkelvoudigInformatieObjectResponse {
        if (expand.isEmpty()) return response

        val expandFields = mutableMapOf<String, kotlinx.serialization.json.JsonElement>()

        if ("informatieobjecttype" in expand) {
            val informatieobjecttypeUrl = response.informatieobjecttype
            try {
                val jsonObj = catalogusService.fetchJsonFromUrl(informatieobjecttypeUrl)
                expandFields["informatieobjecttype"] = jsonObj
            } catch (e: Exception) {
                // Log but don't fail the response if expand fails
                org.slf4j.LoggerFactory.getLogger(EnkelvoudigInformatieObjectService::class.java)
                    .warn("Failed to expand informatieobjecttype for URL {}: {}", informatieobjecttypeUrl, e.message)
            }
        }

        return if (expandFields.isNotEmpty()) {
            response.copy(expand = JsonObject(expandFields))
        } else {
            response
        }
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

        if (filters.trefwoordenOverlap.isNotEmpty()) {
            op = op and arrayOverlap(EIOVersions.trefwoorden, filters.trefwoordenOverlap)
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

        // EXPERIMENTEEL filters
        filters.informatieobjecttype?.let { iot ->
            op = op and (EIOVersions.informatieobject_type eq iot)
        }

        if (filters.vertrouwelijkheidaanduiding.isNotEmpty()) {
            val normalized = filters.vertrouwelijkheidaanduiding.map { it.lowercase() }
            op = op and (EIOVersions.vertrouwlijkheidsAanduiding.lowerCase() inList normalized)
        }

        filters.titel?.let { titel ->
            op = op and (EIOVersions.titel.lowerCase() like "%${titel.lowercase()}%")
        }

        filters.auteur?.let { auteur ->
            op = op and (EIOVersions.auteur.lowerCase() like "%${auteur.lowercase()}%")
        }

        filters.status?.let { status ->
            op = op and (EIOVersions.status eq status)
        }

        filters.beschrijving?.let { beschrijving ->
            op = op and (EIOVersions.beschrijving.lowerCase() like "%${beschrijving.lowercase()}%")
        }

        filters.creatiedatumLte?.let { op = op and (EIOVersions.creatieDatum lessEq it) }
        filters.creatiedatumGte?.let { op = op and (EIOVersions.creatieDatum greaterEq it) }

        filters.registratiedatumLte?.let { op = op and (EIOVersions.beginRegistratie lessEq it) }
        filters.registratiedatumGte?.let { op = op and (EIOVersions.beginRegistratie greaterEq it) }

        filters.locked?.let { locked ->
            if (locked) {
                op = op and (EIORecords.lockToken.isNotNull())
            } else {
                op = op and (EIORecords.lockToken.isNull())
            }
        }

        return op
    }

    private fun isH2(): Boolean = TransactionManager.current().db.vendor.contains("h2", ignoreCase = true)

    /** column @> ARRAY[v1, v2, ...] — all values must be present (PostgreSQL) or ARRAY_CONTAINS per value (H2) */
    private fun arrayContainsAll(column: Column<List<String>>, values: List<String>): Op<Boolean> {
        if (isH2()) {
            // H2: ARRAY_CONTAINS(column, value) for each value, combined with AND
            return values
                .map { value -> arrayContainsH2(column, value) }
                .reduce { acc, op -> acc and op }
        }
        return object : Op<Boolean>() {
            override fun toQueryBuilder(queryBuilder: QueryBuilder) {
                val arrayType = column.columnType as ArrayColumnType<String, *>
                val elementType = arrayType.delegate
                queryBuilder {
                    append(column)
                    append(" @> ARRAY[")
                    values.forEachIndexed { index, value ->
                        if (index > 0) append(", ")
                        append(QueryParameter(value, elementType))
                    }
                    append("]")
                }
            }
        }
    }

    /** column && ARRAY[v1, v2, ...] — at least one value must be present (PostgreSQL) or ARRAY_CONTAINS per value OR-ed (H2) */
    private fun arrayOverlap(column: Column<List<String>>, values: List<String>): Op<Boolean> {
        if (isH2()) {
            // H2: ARRAY_CONTAINS(column, value) for each value, combined with OR
            return values
                .map { value -> arrayContainsH2(column, value) }
                .reduce { acc, op -> acc or op }
        }
        return object : Op<Boolean>() {
            override fun toQueryBuilder(queryBuilder: QueryBuilder) {
                val arrayType = column.columnType as ArrayColumnType<String, *>
                val elementType = arrayType.delegate
                queryBuilder {
                    append(column)
                    append(" && ARRAY[")
                    values.forEachIndexed { index, value ->
                        if (index > 0) append(", ")
                        append(QueryParameter(value, elementType))
                    }
                    append("]")
                }
            }
        }
    }

    /** H2-compatible: ARRAY_CONTAINS(column, value) */
    private fun arrayContainsH2(column: Column<List<String>>, value: String): Op<Boolean> = object : Op<Boolean>() {
        override fun toQueryBuilder(queryBuilder: QueryBuilder) {
            val arrayType = column.columnType as ArrayColumnType<String, *>
            val elementType = arrayType.delegate
            queryBuilder {
                append("ARRAY_CONTAINS(")
                append(column)
                append(", ")
                append(QueryParameter(value, elementType))
                append(")")
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
            val latestVersion = record.versions.maxByOrNull { it.versie }
            auditContext.captureOld(record.toResponse(latestVersion), latestVersion)
            auditTrailService.removeAuditTrailsForResource(id)
            if (record.lockToken != null) {
                return@transaction DeleteResult.Locked
            }
            record.delete()
            DeleteResult.Success
        }
    }
}
