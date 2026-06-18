// SPDX-License-Identifier: EUPL-1.2
// Copyright (C) 2026 Gemeente Utrecht
package com.baseflow.shared.services

import com.baseflow.shared.api.ApiUrlBuilder
import com.baseflow.shared.api.ResourceUuidParser
import com.baseflow.shared.api.middleware.AuditContext
import com.baseflow.shared.api.models.CreateOIORequest
import com.baseflow.shared.api.models.ObjectInformatieObjectResponse
import com.baseflow.shared.api.models.ResourceSegments
import com.baseflow.shared.api.models.SubjectType
import com.baseflow.shared.entities.EIORecordEntity
import com.baseflow.shared.entities.EIOVersionEntity
import com.baseflow.shared.entities.EIOVersions
import com.baseflow.shared.entities.OIORecordEntity
import com.baseflow.shared.entities.OIORecords
import com.baseflow.shared.services.models.CreateOIOResult
import com.baseflow.shared.services.models.DeleteOIOResult
import com.baseflow.shared.services.models.QueryObjectInformatieObjectenFilter
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.dao.with
import org.jetbrains.exposed.v1.jdbc.andWhere
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.koin.core.annotation.InjectedParam
import org.slf4j.LoggerFactory
import java.util.*
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/**
 * Service for ObjectInformatieObject operations
 */
open class ObjectInformatieObjectService(
    @InjectedParam private val resourceSegment: ResourceSegments,
    private val auditTrailService: AuditTrailService,
    private val auditContext: AuditContext,
) {
    private val logger = LoggerFactory.getLogger(ObjectInformatieObjectService::class.java)

    /**
     * Get all ObjectInformatieObjecten with optional filtering and pagination
     */
    fun getAll(filter: QueryObjectInformatieObjectenFilter): Pair<List<ObjectInformatieObjectResponse>, Long> {
        return transaction {
            val query = OIORecords.selectAll()

            filter.informatieobject?.let { filterUrl ->
                val filterUuid =
                    ResourceUuidParser.parseUuid(filterUrl, ResourceSegments.ENKELVOUDIG_INFORMATIE_OBJECTEN.value)
                if (filterUuid != null) {
                    query.andWhere { OIORecords.informatieobject eq filterUuid }
                } else {
                    // If URL is invalid, we should probably return empty results for this filter
                    return@transaction emptyList<ObjectInformatieObjectResponse>() to 0L
                }
            }

            filter.subjectObject?.let { objUrl ->
                query.andWhere { OIORecords.subjectObject eq objUrl }
            }

            val totalCount = query.count()
            val pageSize = filter.pageSize
            val page = if (filter.page > 0) filter.page else 1
            val offset = (page - 1L) * pageSize

            val items = OIORecordEntity.wrapRows(query.limit(pageSize).offset(offset))
                .with(OIORecordEntity::informatieobject)
                .map { it.toResponse() }

            items to totalCount
        }
    }

    /**
     * Get a single ObjectInformatieObject by ID
     */
    fun getById(id: UUID): ObjectInformatieObjectResponse? = transaction {
        OIORecordEntity.findById(id)?.toResponse()
    }

    /**
     * Check if an ObjectInformatieObject exists
     */
    fun exists(id: UUID): Boolean = transaction {
        OIORecordEntity.findById(id) != null
    }

    /**
     * Create a new ObjectInformatieObject relation
     * Automatically fetches the latest version of the informatieobject
     */
    @OptIn(ExperimentalTime::class)
    fun create(request: CreateOIORequest): CreateOIOResult {
        return transaction {
            // Extract UUID from informatieobject URL and fetch EIO record
            val eioUuid = ResourceUuidParser.parseUuid(
                request.informatieobject,
                ResourceSegments.ENKELVOUDIG_INFORMATIE_OBJECTEN.value,
            )
            if (eioUuid == null) {
                logger.warn("Could not extract UUID from informatieobject URL: ${request.informatieobject}")
                return@transaction CreateOIOResult.Conflict("Invalid informatieobject URL")
            }

            val eioRecord = EIORecordEntity.findById(eioUuid)
            if (eioRecord == null) {
                logger.warn("Could not find EIO record for UUID: $eioUuid")
                return@transaction CreateOIOResult.Conflict("Informatieobject not found")
            }

            // Check if relation already exists
            val existing = OIORecordEntity.find {
                (OIORecords.informatieobject eq eioRecord.id) and (OIORecords.subjectObject eq request.subjectObject)
            }.firstOrNull()

            if (existing != null) {
                logger.warn(
                    "Duplicate OIO relation attempted: informatieobject=${request.informatieobject}, object=${request.subjectObject}",
                )
                return@transaction CreateOIOResult.Conflict(
                    "Relation between informatieobject and object already exists",
                )
            }

            // Fetch latest version entity
            val versionEntity = EIOVersionEntity.find {
                EIOVersions.recordId eq eioRecord.id
            }.maxByOrNull { it.versie }

            if (versionEntity == null) {
                logger.warn("Could not find EIO version for informatieobject: ${request.informatieobject}")
                return@transaction CreateOIOResult.Conflict("Could not find informatieobject version")
            }

            // Create new relation
            val now = Clock.System.now()
            val entity = OIORecordEntity.new {
                informatieobject = eioRecord
                informatieobjectVersie = versionEntity
                subjectObject = request.subjectObject
                subjectType = request.subjectType.value
                createdAt = now
                updatedAt = now
            }

            logger.info(
                "Created OIO relation with id=${entity.id.value}, informatieobject=${eioRecord.id.value}, informatieobjectVersie=${versionEntity.versie}",
            )

            val response = entity.toResponse()
            auditContext.captureNew(response, versionEntity)
            CreateOIOResult.Success(response)
        }
    }

    /**
     * Delete all ObjectInformatieObject relations for a given EIO id
     */
    fun deleteByEioId(eioId: UUID): DeleteOIOResult = transaction {
        val entities = OIORecordEntity.find {
            OIORecords.informatieobject eq eioId
        }.toList()

        if (entities.isEmpty()) {
            logger.warn("No OIO relations found for record_id=$eioId")
            return@transaction DeleteOIOResult.NotFound
        }

        entities.forEach { entity ->
            auditContext.captureOld(entity.toResponse(), entity.informatieobjectVersie)
            auditTrailService.removeAuditTrailsForResource(entity.id.value)
            entity.delete()
            logger.info("Deleted OIO relation with id=${entity.id.value} for record_id=$eioId")
        }

        DeleteOIOResult.Success
    }

    /**
     * Delete all ObjectInformatieObject relations for a given subject object URL.
     */
    fun deleteBySubjectObject(subjectObject: String): DeleteOIOResult = transaction {
        val entities = OIORecordEntity.find {
            OIORecords.subjectObject eq subjectObject
        }.toList()

        if (entities.isEmpty()) {
            logger.warn("No OIO relations found for subjectObject=$subjectObject")
            return@transaction DeleteOIOResult.NotFound
        }

        entities.forEach { entity ->
            auditContext.captureOld(entity.toResponse(), entity.informatieobjectVersie)
            auditTrailService.removeAuditTrailsForResource(entity.id.value)
            entity.delete()
            logger.info("Deleted OIO relation with id=${entity.id.value} for subjectObject=$subjectObject")
        }

        DeleteOIOResult.Success
    }

    /**
     * Delete an ObjectInformatieObject relation
     */
    fun delete(id: UUID): DeleteOIOResult = transaction {
        val entity = OIORecordEntity.findById(id)
        auditContext.captureOld(entity?.toResponse(), entity?.informatieobjectVersie)
        auditTrailService.removeAuditTrailsForResource(id)
        if (entity == null) {
            logger.warn("Attempted to delete non-existent OIO with id=$id")
            DeleteOIOResult.NotFound
        } else {
            entity.delete()
            logger.info("Deleted OIO relation with id=$id")
            DeleteOIOResult.Success
        }
    }

    /**
     * Convert entity to response model
     */
    private fun OIORecordEntity.toResponse(): ObjectInformatieObjectResponse {
        val url = ApiUrlBuilder.absolute(resourceSegment.value, this.id.value.toString())
        val informatieobjectUrl = ApiUrlBuilder.absolute(
            ResourceSegments.ENKELVOUDIG_INFORMATIE_OBJECTEN.value,
            this.informatieobject.id.value.toString(),
        )
        return ObjectInformatieObjectResponse(
            id = this.id.value.toString(),
            url = url,
            informatieobject = informatieobjectUrl,
            subjectObject = this.subjectObject,
            subjectType = SubjectType(this.subjectType),
        )
    }
}
