// SPDX-License-Identifier: EUPL-1.2
// Copyright (C) 2026 Gemeente Utrecht
package com.baseflow.api.middleware

import com.baseflow.services.AuditTrailService
import io.ktor.server.application.*
import io.ktor.util.*
import org.koin.core.context.GlobalContext

val AuditContextKey = AttributeKey<AuditContext>("AuditContext")

val AuditTrailPlugin = createRouteScopedPlugin("AuditTrail") {
    onCall { call ->
        // Get AuditContext from the scope (same instance will be used everywhere in this scope)
        val auditContext: AuditContext = GlobalContext.get().get<AuditContext>()
        call.attributes.put(AuditContextKey, auditContext)
    }

    onCallRespond { call, _ ->
        val context = call.attributes.getOrNull(AuditContextKey) ?: return@onCallRespond
        if (context.hasChanges()) {
            val auditTrailService: AuditTrailService = GlobalContext.get().get<AuditTrailService>()
            auditTrailService.create(call)
        }
    }
}
