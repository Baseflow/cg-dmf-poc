// SPDX-License-Identifier: EUPL-1.2
// Copyright (C) 2025 Gemeente Utrecht

package com.baseflow.api.routes

import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

/**
 * EnkelvoudigInformatieObject routes
 *
 * Handles CRUD operations for document objects:
 * - POST / - Create new document
 * - GET / - List documents (with filtering)
 * - GET /{uuid} - Get single document
 * - PUT /{uuid} - Update document
 * - PATCH /{uuid} - Partial update
 * - DELETE /{uuid} - Delete document
 * - GET /{uuid}/download - Download document content
 * - POST /{uuid}/lock - Lock document for editing
 * - POST /{uuid}/unlock - Unlock document
 * - POST /_zoek - Advanced search
 */
fun Route.enkelvoudigInformatieObjectenRoutes() {
    // List all documents (with optional filters)
    get {
        call.respond(
            mapOf(
                "count" to 0,
                "next" to null,
                "previous" to null,
                "results" to emptyList<Any>()
            )
        )
    }

    // Create new document
    post {
        call.respond(mapOf("message" to "Create EnkelvoudigInformatieObject - to be implemented"))
    }

    // Advanced search endpoint
    post("/_zoek") {
        call.respond(mapOf("message" to "Search EnkelvoudigInformatieObject - to be implemented"))
    }

    // Single document operations
    route("/{uuid}") {
        // Get single document
        get {
            val uuid = call.parameters["uuid"]
            call.respond(mapOf("message" to "Get EnkelvoudigInformatieObject $uuid - to be implemented"))
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

