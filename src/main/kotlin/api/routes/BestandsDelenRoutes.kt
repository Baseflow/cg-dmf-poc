// SPDX-License-Identifier: EUPL-1.2
// Copyright (C) 2025 Gemeente Utrecht
package com.baseflow.api.routes

import io.ktor.server.response.*
import io.ktor.server.routing.*

/**
 * BestandsDeel routes
 *
 * Handles chunked file uploads for large documents (>4GB):
 * - PUT /{uuid} - Upload a file chunk
 *
 * Part of the 1.1.0+ workflow for handling files larger than the 4GB minimum.
 */
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
}
