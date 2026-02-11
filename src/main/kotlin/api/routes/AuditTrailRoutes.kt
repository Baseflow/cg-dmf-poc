// SPDX-License-Identifier: EUPL-1.2
// Copyright (C) 2026 Gemeente Utrecht

package com.baseflow.api.routes

import com.baseflow.services.AuditTrailRetrievalService
import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.util.UUID

fun Route.auditTrailRoutes() {
    val service = AuditTrailRetrievalService()

    route("/{uuid}/audittrail/{auditTrailUuid}") {
        get {
            val resourceUuid = call.parameters["uuid"]
                ?.let { UUID.fromString(it) }
                ?: return@get call.respond(HttpStatusCode.BadRequest, "Invalid resource UUID")

            val auditTrailUuid = call.parameters["auditTrailUuid"]
                ?.let { UUID.fromString(it) }
                ?: return@get call.respond(HttpStatusCode.BadRequest, "Invalid audit trail UUID")

            val auditTrail = service.getByUuid(resourceUuid, auditTrailUuid)
                ?: return@get call.respond(HttpStatusCode.NotFound)

            call.respond(auditTrail)
        }
    }

    route("/{uuid}/audittrail") {
        get {
            val resourceUuid = call.parameters["uuid"]
                ?.let { UUID.fromString(it) }
                ?: return@get call.respond(HttpStatusCode.BadRequest, "Invalid UUID")

            val auditTrails = service.listByResource(resourceUuid)
            call.respond(auditTrails)
        }
    }
}