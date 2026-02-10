// SPDX-License-Identifier: EUPL-1.2
// Copyright (C) 2026 Gemeente Utrecht

package com.baseflow.api.middleware

import api.middleware.AuditContext
import com.baseflow.services.createAuditTrail
import io.ktor.server.application.*
import io.ktor.server.routing.*
import io.ktor.util.*

val AuditContextKey = AttributeKey<AuditContext>("AuditContext")

val AuditTrailPlugin = createRouteScopedPlugin("AuditTrail") {
    onCall { call ->
        call.attributes.put(AuditContextKey, AuditContext())
    }

    onCallRespond { call, _ ->
        val context = call.attributes.getOrNull(AuditContextKey) ?: return@onCallRespond
        if (context.hasChanges()) {
            createAuditTrail(call, context)
        }
    }
}

fun RoutingCall.auditContext(): AuditContext =
    attributes.getOrNull(AuditContextKey) ?: AuditContext()