// SPDX-License-Identifier: EUPL-1.2
// Copyright (C) 2026 Gemeente Utrecht

package com.baseflow.api.middleware

import com.baseflow.services.AuditTrailService
import io.ktor.server.application.*
import io.ktor.util.*
import org.koin.ktor.ext.inject

val AuditContextKey = AttributeKey<AuditContext>("AuditContext")

val AuditTrailPlugin = createRouteScopedPlugin("AuditTrail") {
    onCall { call ->
        call.attributes.put(AuditContextKey, AuditContext(call))
    }

    onCallRespond { call, _ ->
        val context = call.attributes.getOrNull(AuditContextKey) ?: return@onCallRespond
        if (context.hasChanges()) {
            val auditTrailService: AuditTrailService by call.application.inject()
            auditTrailService.create(call, context)
        }
    }
}
