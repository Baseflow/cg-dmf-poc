// SPDX-License-Identifier: EUPL-1.2
// Copyright (C) 2026 Gemeente Utrecht

package com.baseflow.api.middleware

import com.baseflow.config.RequestScope
import com.baseflow.services.AuditTrailService
import io.ktor.server.application.*
import io.ktor.util.*
import org.koin.core.scope.Scope
import org.koin.ktor.ext.getKoin

val AuditContextKey = AttributeKey<AuditContext>("AuditContext")
val RequestScopeKey = AttributeKey<Scope>("RequestScope")

val AuditTrailPlugin = createRouteScopedPlugin("AuditTrail") {
    onCall { call ->
        // Create a new Koin scope for this request
        val koin = call.application.getKoin()
        val requestScope = koin.createScope<RequestScope>()
        call.attributes.put(RequestScopeKey, requestScope)

        // Get AuditContext from the scope (same instance will be used everywhere in this scope)
        val auditContext = requestScope.get<AuditContext>()
        call.attributes.put(AuditContextKey, auditContext)
    }

    onCallRespond { call, _ ->
        val context = call.attributes.getOrNull(AuditContextKey) ?: return@onCallRespond
        if (context.hasChanges()) {
            val auditTrailService: AuditTrailService = call.attributes[RequestScopeKey].get()
            auditTrailService.create(call)
        }

        // Close the request scope when the call is complete
        call.attributes.getOrNull(RequestScopeKey)?.close()
    }
}
