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
import com.baseflow.services.models.wopi.WopiLockPayload
import com.baseflow.services.models.wopi.WopiLockResult
import com.baseflow.services.models.wopi.WopiUnlockResult
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atTime
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.json.JsonObject
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.jdbc.Query
import org.jetbrains.exposed.v1.jdbc.andWhere
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.koin.core.annotation.Scope
import org.koin.core.annotation.Scoped
import java.io.OutputStream
import java.nio.file.Files
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
            bestandsRepository = uploadResultaat.bestandsRepository.orEmpty()
        }
        val trefwoorden =
            request.trefwoorden?.map { it.lowercase(Locale.ROOT) }?.distinct()?.sorted() ?: emptyList()
        trefwoorden.forEach { woord ->
            EIOVersionTrefwoordEntity.new {
                versionId = eioVersion
                trefwoordId = TrefwoordEntity.findOrCreate(woord)
            }
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

        val response = record.toResponse(eioVersion, bestandsDelen, trefwoorden)
        auditContext.captureNew(response, eioVersion)
        response as EnkelvoudigInformatieObjectResponse
    }

    private fun getUploadResultaat(
        request: EnkelvoudigInformatieObjectRequest,
        record: EIORecordEntity,
        version: Int = 1,
        locatie: String = "",
        repoName: String? = null,
    ): UploadResultaat {
        var loc = locatie
        if (!request.isFileEmpty()) {
            loc = "${record.id.value}/$version/${request.bestandsnaam}"
        }
        return storeFileVersion(request, loc, resolveBestandsRepository(repoName))
    }

    private fun resolveBestandsRepository(repoName: String?): String? {
        val requestedRepo = repoName?.takeUnless { it.isBlank() }
        return requestedRepo ?: BlobStorageRegistrar.defaultProvider()?.name
    }

    private fun storeFileVersion(
        request: EnkelvoudigInformatieObjectRequest,
        bestandsLocatie: String,
        repoName: String? = null,
    ): UploadResultaat {
        if (!request.inhoud.isNullOrEmpty() && !request.isFileEmpty()) {
            val content = Base64.decode(request.inhoud)
            val fileType = StorageService.detectFileFormat(content)
            require(bestandsLocatie.isNotBlank()) {
                "bestandsLocatie must not be blank when inhoud is present"
            }
            storageService.uploadFile(bestandsLocatie, content, repoName)
            return UploadResultaat(
                bestandsFormaat = fileType,
                bestandsOmvang = content.size.toLong(),
                bestandsLocatie = bestandsLocatie,
                bestandsRepository = repoName,
            )
        }
        return UploadResultaat(bestandsLocatie = bestandsLocatie, bestandsRepository = repoName)
    }

    /**
     * Get an EnkelvoudigInformatieObject by ID
     * Returns the latest version data
     */
    suspend fun getById(id: UUID, expand: List<String> = emptyList()): EnkelvoudigInformatieObjectResponse? {
        val response = transaction {
            val record = EIORecordEntity.findById(id) ?: return@transaction null
            val version = record.latestVersion() ?: return@transaction null
            val bestandsDelen = bestandsDeelService.getBestandsDelen(version)
            val trefwoorden = (EIOVersionTrefwoorden innerJoin Trefwoorden)
                .select(Trefwoorden.woord)
                .where { EIOVersionTrefwoorden.versionId eq version.id }
                .orderBy(Trefwoorden.woord to SortOrder.ASC)
                .map { it[Trefwoorden.woord] }
            record.toResponse(version, bestandsDelen, trefwoorden)
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
                // Latest-version filter: correlated subquery (fast with idx_eio_versions_record_versie)
                andWhere {
                    EIOVersions.versie eqSubQuery innerVersions
                        .select(innerVersions[EIOVersions.versie].max())
                        .where { innerVersions[EIOVersions.recordId] eq EIORecords.id }
                }
                if (filters.trefwoorden.isNotEmpty()) {
                    andWhere { EIOVersions.id inSubQuery trefwoordenContainsAllVersionIds(filters.trefwoorden) }
                }
                if (filters.trefwoordenOverlap.isNotEmpty()) {
                    andWhere { EIOVersions.id inSubQuery trefwoordenOverlapVersionIds(filters.trefwoordenOverlap) }
                }
                if (condition != Op.TRUE) {
                    andWhere { condition }
                }
            }

            // Count without ORDER BY — avoids a redundant sort over all matching rows.
            val totalCount = query.count()

            // Apply ordering only to the paginated data query.
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
                query.orderBy(*orderClauses.toTypedArray())
            }

            // Materialise the page rows first so we can collect all version IDs for a
            // single batch bestandsdelen query instead of one query per row (N+1).
            val pageRows = query.limit(pageSize).offset(offset).toList()
            val versionIds = pageRows.map { EIOVersionEntity.wrapRow(it).id.value }
            val bestandsDelenByVersion = bestandsDeelService.getBestandsDelenForVersions(versionIds)
            val trefwoordenByVersion =
                if (versionIds.isEmpty()) {
                    emptyMap()
                } else {
                    (EIOVersionTrefwoorden innerJoin Trefwoorden)
                        .select(EIOVersionTrefwoorden.versionId, Trefwoorden.woord)
                        .where { EIOVersionTrefwoorden.versionId inList versionIds }
                        .orderBy(Trefwoorden.woord to SortOrder.ASC)
                        .groupBy({ it[EIOVersionTrefwoorden.versionId].value }, { it[Trefwoorden.woord] })
                }

            // Read each ResultRow directly to avoid wrapRows producing duplicate entity instances.
            // Each row already contains exactly one record + its latest version due to the subquery filter.
            val results = pageRows.mapNotNull { row ->
                val record = EIORecordEntity.wrapRow(row)
                val version = EIOVersionEntity.wrapRow(row)
                val bestandsdelen = bestandsDelenByVersion[version.id.value] ?: emptyList()
                val trefwoorden = trefwoordenByVersion[version.id.value] ?: emptyList()
                record.toResponse(version, bestandsdelen, trefwoorden)
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
    fun streamByBestandsnaam(bestandsnaam: String, output: OutputStream, repoName: String? = null) {
        storageService.downloadFileTo(bestandsnaam, output, repoName.takeUnless { it.isNullOrBlank() }).join()
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

            val latestVersion = record.latestVersion()
            auditContext.captureOld(record.toResponse(latestVersion))
            val newVersionNumber = (latestVersion?.versie ?: 1) + 1

            // Validate informatieobjecttype against Catalogus
            if (!request.informatieobjecttype.isNullOrEmpty()) {
                catalogusService.validateInformatieobjecttype(request.informatieobjecttype)
            }

            val repoName = latestVersion?.bestandsRepository?.takeUnless { it.isBlank() }
            val uploadResultaat =
                getUploadResultaat(
                    request,
                    record,
                    newVersionNumber,
                    latestVersion?.bestandsLocatie.orEmpty(),
                    repoName,
                )
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
                bestandsRepository = uploadResultaat.bestandsRepository
                    ?: latestVersion?.bestandsRepository.orEmpty()
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

            val trefwoordenToStore: List<String> = when {
                partial && request.trefwoorden.isNullOrEmpty() ->
                    (EIOVersionTrefwoorden innerJoin Trefwoorden)
                        .select(Trefwoorden.woord)
                        .where { EIOVersionTrefwoorden.versionId eq latestVersion!!.id }
                        .orderBy(Trefwoorden.woord to SortOrder.ASC)
                        .map { it[Trefwoorden.woord] }

                else -> (request.trefwoorden ?: emptyList()).map { it.lowercase(Locale.ROOT) }.distinct().sorted()
            }
            trefwoordenToStore.forEach { woord ->
                EIOVersionTrefwoordEntity.new {
                    versionId = version
                    trefwoordId = TrefwoordEntity.findOrCreate(woord)
                }
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

            val response = record.toResponse(version, bestandsDelen, trefwoordenToStore)
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
        trefwoorden: List<String>? = null,
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
            trefwoorden = trefwoorden
                ?: (EIOVersionTrefwoorden innerJoin Trefwoorden)
                    .select(Trefwoorden.woord)
                    .where { EIOVersionTrefwoorden.versionId eq version.id }
                    .orderBy(Trefwoorden.woord to SortOrder.ASC)
                    .map { it[Trefwoorden.woord] },
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

    /**
     * All provided trefwoorden must be linked to the version.
     * Implemented as a single correlated EXISTS with GROUP BY / HAVING COUNT = N,
     * while resolving trefwoord IDs once in a non-correlated subquery.
     */
    private fun trefwoordenContainsAllVersionIds(words: List<String>): Query {
        val lower = words.map { it.lowercase(Locale.ROOT) }
        val trefwoordIds = Trefwoorden
            .select(Trefwoorden.id)
            .where { Trefwoorden.woord inList lower }

        return EIOVersionTrefwoorden
            .select(EIOVersionTrefwoorden.versionId)
            .where {
                EIOVersionTrefwoorden.trefwoordId inSubQuery trefwoordIds
            }
            .groupBy(EIOVersionTrefwoorden.versionId)
            .having { EIOVersionTrefwoorden.trefwoordId.countDistinct() eq lower.size.toLong() }
    }

    /**
     * At least one of the provided trefwoorden must be linked to the version.
     * Implemented as a single EXISTS subquery using preselected trefwoord IDs.
     */
    private fun trefwoordenOverlapVersionIds(words: List<String>): Query {
        val trefwoordIds = Trefwoorden
            .select(Trefwoorden.id)
            .where { Trefwoorden.woord inList words.map { it.lowercase(Locale.ROOT) } }

        return EIOVersionTrefwoorden
            .select(EIOVersionTrefwoorden.versionId)
            .where {
                EIOVersionTrefwoorden.trefwoordId inSubQuery trefwoordIds
            }
            .withDistinct()
    }

    /**
     * Update an EnkelvoudigInformatieObject with raw bytes (e.g. from WOPI PutFile).
     * Retrieves the existing record and creates a new version with the provided file bytes,
     * preserving all other metadata from the latest version.
     */
    @OptIn(ExperimentalTime::class)
    suspend fun updateWithBytes(id: UUID, bytes: ByteArray): EnkelvoudigInformatieObjectResponse? {
        return suspendTransaction {
            val record = EIORecordEntity.findById(id) ?: return@suspendTransaction null

            val latestVersion = record.versions.maxByOrNull { it.versie }
            auditContext.captureOld(record.toResponse(latestVersion))
            val newVersionNumber = (latestVersion?.versie ?: 0) + 1

            val fileType = StorageService.detectFileFormat(bytes)
            val bestandsnaamVoorOpslag =
                (
                    latestVersion?.bestandsnaam?.ifBlank { null }
                        ?: latestVersion?.titel?.ifBlank { null }
                        ?: "document-${record.id.value}"
                    )
                    .replace("\\", "_")
                    .replace("/", "_")
            val newBestandsLocatie =
                "${record.id.value}/$newVersionNumber/$bestandsnaamVoorOpslag"
            storageService.uploadFile(newBestandsLocatie, bytes)

            val integrityResult =
                IntegrityCalculationService.calculateIntegrity(bytes, latestVersion?.integriteitAlgoritme)
            val version = EIOVersionEntity.new {
                recordId = record
                versie = newVersionNumber
                bronOrganisatie = latestVersion?.bronOrganisatie.orEmpty()
                informatieobject_type = latestVersion?.informatieobject_type.orEmpty()
                taal = latestVersion?.taal.orEmpty()
                bestandsnaam = latestVersion?.bestandsnaam.orEmpty()
                titel = latestVersion?.titel.orEmpty()
                auteur = latestVersion?.auteur.orEmpty()
                creatieDatum = latestVersion?.creatieDatum
                    ?: Clock.System.now().toLocalDateTime(TimeZone.UTC).date
                beginRegistratie = Clock.System.now().toLocalDateTime(TimeZone.UTC)
                formaat = fileType ?: latestVersion?.formaat
                bestandsomvang = bytes.size.toLong()
                bestandsLocatie = newBestandsLocatie
                bestandsRepository = latestVersion?.bestandsRepository.orEmpty()
                link = latestVersion?.link.orEmpty()
                integriteitAlgoritme = integrityResult.algorithm
                integriteitWaarde = integrityResult.hash
                integriteitsDatum = Clock.System.now().toLocalDateTime(TimeZone.UTC)
                verschijningsVorm = latestVersion?.verschijningsVorm.orEmpty()
                vertrouwlijkheidsAanduiding = latestVersion?.vertrouwlijkheidsAanduiding.orEmpty()
                status = latestVersion?.status.orEmpty()
                beschrijving = latestVersion?.beschrijving.orEmpty()
                indicatieGebruiksrecht = latestVersion?.indicatieGebruiksrecht ?: false
                ondertekening_soort = latestVersion?.ondertekening_soort.orEmpty()
                ondertekenings_datum = latestVersion?.ondertekenings_datum
                identificatie = latestVersion?.identificatie.orEmpty()
            }

            val trefwoorden = if (latestVersion != null) {
                (EIOVersionTrefwoorden innerJoin Trefwoorden)
                    .select(Trefwoorden.woord)
                    .where { EIOVersionTrefwoorden.versionId eq latestVersion.id }
                    .orderBy(Trefwoorden.woord to SortOrder.ASC)
                    .map { it[Trefwoorden.woord] }
            } else {
                emptyList()
            }
            trefwoorden.forEach { woord ->
                EIOVersionTrefwoordEntity.new {
                    versionId = version
                    trefwoordId = TrefwoordEntity.findOrCreate(woord)
                }
            }

            val response = record.toResponse(version, emptyList(), trefwoorden)
            auditContext.captureNew(response, version)
            response
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

    fun wopiLock(id: UUID, wopiClientLock: String): WopiLockResult? {
        return transaction {
            val record = EIORecordEntity.findById(id) ?: return@transaction null
            val storedLock = record.lockToken
            if (!storedLock.isNullOrEmpty()) {
                if (storedLock == wopiClientLock) {
                    return@transaction WopiLockResult.AlreadyLocked
                } else {
                    return@transaction WopiLockResult.LockMismatch(currentFileLock = WopiLockPayload(lock = storedLock))
                }
            }
            record.lockToken = wopiClientLock
            WopiLockResult.Success
        }
    }

    fun wopiUnlock(id: UUID, wopiClientLock: String): WopiUnlockResult? {
        return transaction {
            val record = EIORecordEntity.findById(id) ?: return@transaction null
            val storedLock = record.lockToken ?: return@transaction WopiUnlockResult.NotLocked
            if (storedLock != wopiClientLock) {
                return@transaction WopiUnlockResult.LockMismatch(WopiLockPayload(lock = storedLock))
            }
            record.lockToken = null
            WopiUnlockResult.Success
        }
    }

    fun renameFile(id: UUID, newFileName: String): Boolean {
        return transaction {
            val record = EIORecordEntity.findById(id) ?: return@transaction false
            val latestVersion = record.latestVersion() ?: return@transaction false
            // Only update the display filename in the DB. The blob object key is not moved —
            // WOPI RenameFile only changes the name shown to the user, not the storage location.
            latestVersion.bestandsnaam = newFileName

            true
        }
    }

    fun unlock(id: UUID, lock: String): UnlockResult? {
        // Collect bestandsdelen info inside the transaction, then do blob I/O outside.
        data class PartInfo(val storageKey: String, val bestandsDeelId: UUID)
        data class MergeContext(val parts: List<PartInfo>, val mergedLocatie: String, val latestVersionId: UUID)

        // Transaction 1: validate lock and collect part metadata.
        // When there are no parts to merge the lock is also cleared here (nothing can fail after this).
        // When parts exist the lock is intentionally kept until the merge succeeds, so a failed
        // upload does not leave the record permanently unlocked with missing content.
        // No blob I/O happens here so the DB connection is held for the minimum time.
        var mergeCtx: MergeContext? = null
        val preUnlockResult: UnlockResult? = transaction {
            val record = EIORecordEntity.findById(id) ?: return@transaction null
            val current = record.lockToken ?: return@transaction UnlockResult.NotLocked
            if (current != lock) return@transaction UnlockResult.InvalidLock

            val latestVersion = record.versions.maxByOrNull { it.versie }
            if (latestVersion != null) {
                val allParts = BestandsDeelEntity
                    .find { BestandsDelen.versionId eq latestVersion.id }
                    .sortedBy { it.volgnummer }
                val parts = allParts.filter { it.voltooid }
                if (parts.size < allParts.count()) {
                    throw IllegalStateException("Not all parts are marked as completed")
                }

                if (parts.isNotEmpty()) {
                    val partInfos = parts.map { part ->
                        PartInfo(
                            storageKey = bestandsDeelStorageKey(
                                recordId = record.id.value,
                                versie = latestVersion.versie,
                                bestandsDeelId = part.id.value,
                            ),
                            bestandsDeelId = part.id.value,
                        )
                    }
                    val mergedLocatie = "${record.id.value}/${latestVersion.versie}/${latestVersion.bestandsnaam}"
                    mergeCtx = MergeContext(partInfos, mergedLocatie, latestVersion.id.value)
                }
            }

            // No parts to merge: clear the lock immediately (nothing can fail after this point).
            if (mergeCtx == null) {
                record.lockToken = null
            }
            UnlockResult.Success
        }

        if (preUnlockResult != UnlockResult.Success) {
            return preUnlockResult
        }

        // Blob I/O: merge parts → upload → delete part blobs – all outside any DB transaction.
        val ctx = mergeCtx
        if (ctx != null) {
            val logger = org.slf4j.LoggerFactory.getLogger(EnkelvoudigInformatieObjectService::class.java)
            try {
                // Stream each part into a temp file to avoid materialising the full
                // merged content in memory (parts can be gigabytes in total).
                val tempFile = Files.createTempFile("eio-merge-", ".tmp")
                try {
                    Files.newOutputStream(tempFile).use { out ->
                        for (part in ctx.parts) {
                            storageService.downloadFileTo(part.storageKey, out).get()
                        }
                    }
                    val contentLength = Files.size(tempFile)
                    Files.newInputStream(tempFile).use { input ->
                        storageService.uploadFile(ctx.mergedLocatie, input, contentLength)
                    }
                } finally {
                    Files.deleteIfExists(tempFile)
                }

                // Delete individual part blobs now that the merged object is safely stored.
                // Best-effort: blob deletion failures are logged but do not abort the unlock.
                val partKeys = ctx.parts.map { it.storageKey }
                storageService.deleteFiles(partKeys)

                logger.info(
                    "Merged {} bestandsdeel(en) into '{}' for EIO {}",
                    ctx.parts.size,
                    ctx.mergedLocatie,
                    id,
                )

                // Follow-up transaction: persist the new bestandsLocatie, remove all part DB rows
                // in a single batched DELETE, and only now clear the lock – guaranteeing the lock
                // is only released after a successful merge and upload.
                val partIds = ctx.parts.map { it.bestandsDeelId }
                transaction {
                    EIOVersionEntity.findById(ctx.latestVersionId)?.let { v ->
                        v.bestandsLocatie = ctx.mergedLocatie
                    }
                    BestandsDelen.deleteWhere { BestandsDelen.id inList partIds }
                    EIORecordEntity.findById(id)?.lockToken = null
                }
            } catch (e: Exception) {
                logger.error("Failed to merge bestandsdelen for EIO {}: {}", id, e.message, e)
                throw e
            }
        }

        return UnlockResult.Success
    }

    fun delete(id: UUID): DeleteResult {
        // Maps repository name (empty string = default) to the object keys stored there.
        val fileLocationsByRepo = mutableMapOf<String, MutableSet<String>>()
        val result = transaction {
            val lockedRecordExists =
                EIORecords
                    .selectAll()
                    .andWhere { EIORecords.id eq id }
                    .forUpdate()
                    .singleOrNull() != null

            if (!lockedRecordExists) {
                return@transaction DeleteResult.NotFound
            }

            val hasReferences =
                !OIORecords
                    .selectAll()
                    .andWhere { OIORecords.informatieobject eq id }
                    .limit(1)
                    .empty()

            if (hasReferences) {
                return@transaction DeleteResult.HasReferences
            }

            val record = EIORecordEntity.findById(id) ?: return@transaction DeleteResult.NotFound
            val latestVersion = record.latestVersion()
            if (record.lockToken != null) {
                return@transaction DeleteResult.Locked
            }
            auditContext.captureOld(record.toResponse(latestVersion), latestVersion)

            record.versions.forEach { version ->
                val repo = version.bestandsRepository
                if (version.bestandsLocatie.isNotBlank()) {
                    fileLocationsByRepo.getOrPut(repo) { mutableSetOf() }.add(version.bestandsLocatie)
                }

                // Also collect storage keys for completed bestandsdelen chunks.
                BestandsDeelEntity
                    .find { BestandsDelen.versionId eq version.id }
                    .filter { it.voltooid }
                    .forEach {
                        val key = bestandsDeelStorageKey(
                            recordId = record.id.value,
                            versie = version.versie,
                            bestandsDeelId = it.id.value,
                        )
                        fileLocationsByRepo.getOrPut(repo) { mutableSetOf() }.add(key)
                    }
            }

            record.delete()
            DeleteResult.Success
        }
        if (result == DeleteResult.Success) {
            fileLocationsByRepo.forEach { (repo, keys) ->
                storageService.deleteFiles(keys.toList(), repoName = repo.takeUnless { it.isBlank() })
            }
            auditTrailService.removeAuditTrailsForResource(id)
        }
        return result
    }
}
