// SPDX-License-Identifier: EUPL-1.2
// Copyright (C) 2026 Gemeente Utrecht
package com.baseflow.api.documenten.routes

import com.baseflow.api.models.AuditTrailResponse
import com.baseflow.services.AuditTrailService
import io.ktor.http.*
import io.ktor.openapi.jsonSchema
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.routing.openapi.*
import io.ktor.utils.io.*
import org.koin.ktor.plugin.scope
import java.util.*

private val RoutingContext.service: AuditTrailService
    get() = call.scope.get<AuditTrailService>()

@OptIn(ExperimentalKtorApi::class)
fun Route.auditTrailRoutes() {

    route("/{uuid}/audittrail/{auditTrailUuid}") {
        /**
         * Een specifieke audit trail regel opvragen.
         *
         * Responses:
         *   - 200 OK.
         *   - 400 Bad request (invalid UUID).
         *   - 401 Unauthorized.
         *   - 403 Forbidden.
         *   - 404 Not found.
         *   - 406 Not acceptable.
         *   - 409 Conflict.
         *   - 410 Gone.
         *   - 415 Unsupported media type.
         *   - 429 Too many requests.
         *   - 500 Internal server error.
         *
         * @tag AuditTrail
         */
        get {
            val resourceUuid =
                call.parameters["uuid"]
                    ?.let { UUID.fromString(it) }
                    ?: return@get call.respond(HttpStatusCode.BadRequest, "Invalid resource UUID")

            val auditTrailUuid =
                call.parameters["auditTrailUuid"]
                    ?.let { UUID.fromString(it) }
                    ?: return@get call.respond(HttpStatusCode.BadRequest, "Invalid audit trail UUID")

            val auditTrail = service.getByUuid(resourceUuid, auditTrailUuid)
                ?: return@get call.respond(HttpStatusCode.NotFound)

            call.respond(auditTrail)
        }
            .describe {
                operationId = "enkelvoudiginformatieobjecten_audittrail_read"
                tag("audittrail")
                summary = "Een specifieke audit trail regel opvragen."
                parameters {
                    path("uuid") { description = "UUID van het INFORMATIEOBJECT." }
                    path("auditTrailUuid") { description = "UUID van de audit trail regel." }
                }
                responses {
                    response(200) {
                        description = "OK."
                        ContentType.Application.Json { schema = jsonSchema<AuditTrailResponse>() }
                    }
                    response(400) { description = "Bad request: ongeldige UUID." }
                    response(401) { description = "Unauthorized." }
                    response(403) { description = "Forbidden." }
                    response(404) { description = "Not found." }
                }
            }
    }

    route("/{uuid}/audittrail") {
        /**
         * Alle audit trail regels behorend bij het INFORMATIEOBJECT.
         *
         * Responses:
         *   - 200 OK.
         *   - 400 Bad request (invalid UUID).
         *   - 401 Unauthorized.
         *   - 403 Forbidden.
         *   - 406 Not acceptable.
         *   - 409 Conflict.
         *   - 410 Gone.
         *   - 415 Unsupported media type.
         *   - 429 Too many requests.
         *   - 500 Internal server error.
         *
         * @tag AuditTrail
         */
        get {
            val resourceUuid =
                call.parameters["uuid"]
                    ?.let { UUID.fromString(it) }
                    ?: return@get call.respond(HttpStatusCode.BadRequest, "Invalid UUID")

            val auditTrails = service.listByResource(resourceUuid)
            call.respond(auditTrails)
        }
            .describe {
                operationId = "enkelvoudiginformatieobjecten_audittrail_list"
                tag("audittrail")
                summary = "Alle audit trail regels behorend bij het INFORMATIEOBJECT opvragen."
                parameters {
                    path("uuid") { description = "UUID van het INFORMATIEOBJECT." }
                }
                responses {
                    response(200) {
                        description = "Lijst van audit trail regels."
                        ContentType.Application.Json { schema = jsonSchema<List<AuditTrailResponse>>() }
                    }
                    response(400) { description = "Bad request: ongeldige UUID." }
                    response(401) { description = "Unauthorized." }
                    response(403) { description = "Forbidden." }
                }
            }
    }
}
