// SPDX-License-Identifier: EUPL-1.2
// Copyright (C) 2026 Gemeente Utrecht

package com.baseflow.services

import com.baseflow.api.models.AuditTrailResponse
import com.baseflow.entities.AuditTrailEntity
import com.baseflow.entities.AuditTrails
import com.baseflow.entities.toResponse
import org.jetbrains.exposed.v1.core.like
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.util.*

class AuditTrailRetrievalService {

    fun listByResource(resourceUuid: UUID): List<AuditTrailResponse> {
        return transaction {
            AuditTrailEntity.find {
                AuditTrails.resourceUrl like "%/$resourceUuid"
            }.map { it.toResponse() }
        }
    }

    fun getByUuid(resourceUuid: UUID, auditTrailUuid: UUID): AuditTrailResponse? {
        return transaction {
            val entity = AuditTrailEntity.findById(auditTrailUuid)
            if (entity != null && entity.resourceUrl.endsWith("/$resourceUuid")) {
                entity.toResponse()
            } else {
                null
            }
        }
    }
}