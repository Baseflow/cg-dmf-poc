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
    // Upload a file chunk
    put("/{uuid}") {
        val uuid = call.parameters["uuid"]
        call.respond(mapOf("message" to "Upload BestandsDeel $uuid - to be implemented"))
    }
}
