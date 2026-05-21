// SPDX-License-Identifier: EUPL-1.2
// Copyright (C) 2026 Gemeente Utrecht
package com.baseflow.api.middleware

import com.baseflow.services.AuditTrailService
import io.ktor.server.application.*
import io.ktor.util.*
import org.koin.ktor.plugin.scope

val AuditContextKey = AttributeKey<AuditContext>("AuditContext")

val AuditTrailPlugin = createRouteScopedPlugin("AuditTrail") {
    onCall { call ->
        // Get AuditContext from the scope (same instance will be used everywhere in this scope)
        val auditContext: AuditContext = getAuditContext(call)
        call.attributes.put(AuditContextKey, auditContext)
    }

    onCallRespond { call, _ ->
        val context = call.attributes.getOrNull(AuditContextKey) ?: return@onCallRespond
        if (context.hasChanges()) {
            val auditTrailService: AuditTrailService = call.scope.get<AuditTrailService>()
            auditTrailService.create(call)
        }
    }
}

private fun getAuditContext(call: ApplicationCall): AuditContext = call.scope.get<AuditContext>()
