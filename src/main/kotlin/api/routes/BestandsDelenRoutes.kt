// SPDX-License-Identifier: EUPL-1.2
// Copyright (C) 2025-2026 Gemeente Utrecht
package com.baseflow.api.routes

import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.routing.openapi.describe
import io.ktor.utils.io.ExperimentalKtorApi

/**
 * BestandsDeel routes
 *
 * Handles chunked file uploads for large documents (>4GB):
 * - PUT /{uuid} - Upload a file chunk
 *
 * Part of the 1.1.0+ workflow for handling files larger than the 4GB minimum.
 */
@OptIn(ExperimentalKtorApi::class)
fun Route.bestandsDelenRoutes() {
    /**
     * Upload een bestandsdeel.
     *
     * Based on DRF mixin but without partial_update.
     * Part of the 1.1.0+ workflow for handling files larger than the 4GB minimum.
     *
     * Responses:
     *   - 200 OK.
     *   - 400 Bad request.
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
     * @tag BestandsDelen
     */
    put("/{uuid}") {
        val uuid = call.parameters["uuid"]
        call.respond(mapOf("message" to "Upload BestandsDeel $uuid - to be implemented"))
    }
        .describe {
            operationId = "bestandsdelen_update"
            tag("bestandsdelen")
            summary = "Upload een bestandsdeel."
            description =
                "Upload een bestandsdeel als onderdeel van de chunked upload workflow voor grote bestanden. " +
                "Gebaseerd op DRF mixin maar zonder partial_update."
            parameters {
                path("uuid") { description = "Unieke resource identifier (UUID4) van het bestandsdeel." }
            }
            responses {
                response(200) { description = "OK." }
                response(400) { description = "Bad request." }
                response(401) { description = "Unauthorized." }
                response(403) { description = "Forbidden." }
                response(404) { description = "Not found." }
            }
        }
}
