// SPDX-License-Identifier: EUPL-1.2
// Copyright (C) 2026 Gemeente Utrecht
package com.baseflow.services

import com.baseflow.api.ApiUrlBuilder
import com.baseflow.api.ResourceUuidParser
import com.baseflow.api.middleware.AuditContext
import com.baseflow.api.models.CreateOIORequest
import com.baseflow.api.models.ObjectInformatieObjectResponse
import com.baseflow.api.models.SubjectTypeEnum
import com.baseflow.config.RequestScope
import com.baseflow.entities.EIORecordEntity
import com.baseflow.entities.EIOVersionEntity
import com.baseflow.entities.EIOVersions
import com.baseflow.entities.OIORecordEntity
import com.baseflow.entities.OIORecords
import com.baseflow.services.models.CreateOIOResult
import com.baseflow.services.models.DeleteOIOResult
import com.baseflow.services.models.QueryObjectInformatieObjectenFilter
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.dao.with
import org.jetbrains.exposed.v1.jdbc.andWhere
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.koin.core.annotation.InjectedParam
import org.koin.core.annotation.Scope
import org.koin.core.annotation.Scoped
import org.slf4j.LoggerFactory
import java.util.*
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/**
 * Service for ObjectInformatieObject operations
 */
@Scope(RequestScope::class)
@Scoped
open class ObjectInformatieObjectService(@InjectedParam private val resourceSegment: String,
                                         private val auditTrailService: AuditTrailService,
                                         private val auditContext: AuditContext) {
    private val logger = LoggerFactory.getLogger(ObjectInformatieObjectService::class.java)

    /**
     * Get all ObjectInformatieObjecten with optional filtering and pagination
     */
    fun getAll(filter: QueryObjectInformatieObjectenFilter): Pair<List<ObjectInformatieObjectResponse>, Long> {
        return transaction {
            val query = OIORecords.selectAll()

            filter.informatieobject?.let { filterUrl ->
                val filterUuid = ResourceUuidParser.parseUuid(filterUrl, "enkelvoudiginformatieobjecten")
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
            val eioUuid = ResourceUuidParser.parseUuid(request.informatieobject, "enkelvoudiginformatieobjecten")
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
            val now = Clock.System.now().toLocalDateTime(TimeZone.UTC)
            val entity = OIORecordEntity.new {
                informatieobject = eioRecord
                informatieobjectVersie = versionEntity
                subjectObject = request.subjectObject
                subjectType = request.subjectType.name.lowercase()
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
        val url = ApiUrlBuilder.absolute(resourceSegment, this.id.value.toString())
        val informatieobjectUrl = ApiUrlBuilder.absolute(
            "enkelvoudiginformatieobjecten",
            this.informatieobject.id.value.toString(),
        )
        return ObjectInformatieObjectResponse(
            id = this.id.value.toString(),
            url = url,
            informatieobject = informatieobjectUrl,
            subjectObject = this.subjectObject,
            subjectType = SubjectTypeEnum.valueOf(this.subjectType.uppercase()),
        )
    }
}
