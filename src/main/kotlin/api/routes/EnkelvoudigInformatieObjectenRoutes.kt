// SPDX-License-Identifier: EUPL-1.2
// Copyright (C) 2025 Gemeente Utrecht
package com.baseflow.api.routes

import com.baseflow.api.models.CreateEIORequest
import com.baseflow.services.EnkelvoudigInformatieObjectService
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.util.UUID

/**
 * Routes for EnkelvoudigInformatieObjecten (Single Information Objects).
 */
fun Route.enkelvoudigInformatieObjectenRoutes() {
    val service = EnkelvoudigInformatieObjectService()

    // List all documents (with optional filters)
    get {
        call.respond(
            service.getAll()
        )
    }

    // Create new document
    post {
        val request = call.receive<CreateEIORequest>()
        val response = service.create(request)
        call.respond(response)
    }

    // Advanced search endpoint
    post("/_zoek") {
        call.respond(mapOf("message" to "Search EnkelvoudigInformatieObject - to be implemented"))
    }

    // Single document operations
    route("/{uuid}") {
        // Get single document
        get {
            val uuidString = call.parameters["uuid"]
            if (uuidString == null) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "UUID parameter is required"))
                return@get
            }

            try {
                val uuid = UUID.fromString(uuidString)
                val result = service.getById(uuid)

                if (result == null) {
                    call.respond(HttpStatusCode.NotFound, mapOf("error" to "EnkelvoudigInformatieObject not found"))
                } else {
                    call.respond(HttpStatusCode.OK, result)
                }
            } catch (e: IllegalArgumentException) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid UUID format"))
            }
        }

        // Update document (full)
        put {
            val uuid = call.parameters["uuid"]
            call.respond(mapOf("message" to "Update EnkelvoudigInformatieObject $uuid - to be implemented"))
        }

        // Partial update
        patch {
            val uuid = call.parameters["uuid"]
            call.respond(mapOf("message" to "Partial update EnkelvoudigInformatieObject $uuid - to be implemented"))
        }

        // Delete document
        delete {
            val uuid = call.parameters["uuid"]
            call.respond(mapOf("message" to "Delete EnkelvoudigInformatieObject $uuid - to be implemented"))
        }

        // Download document content
        get("/download") {
            val uuid = call.parameters["uuid"]
            call.respond(mapOf("message" to "Download EnkelvoudigInformatieObject $uuid - to be implemented"))
        }

        // Lock document for editing
        post("/lock") {
            val uuid = call.parameters["uuid"]
            call.respond(mapOf("message" to "Lock EnkelvoudigInformatieObject $uuid - to be implemented"))
        }

        // Unlock document
        post("/unlock") {
            val uuid = call.parameters["uuid"]
            call.respond(mapOf("message" to "Unlock EnkelvoudigInformatieObject $uuid - to be implemented"))
        }
    }
}
