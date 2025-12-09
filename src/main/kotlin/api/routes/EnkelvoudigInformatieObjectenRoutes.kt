// SPDX-License-Identifier: EUPL-1.2
// Copyright (C) 2025 Gemeente Utrecht
package com.baseflow.api.routes

import com.baseflow.api.models.CreateEIORequest
import com.baseflow.api.models.PaginatedResponse
import com.baseflow.services.models.QueryEnkelvoudigeInformatieObjectenFilter
import com.baseflow.services.EnkelvoudigInformatieObjectService
import com.baseflow.services.models.*
import com.baseflow.api.ApiVersionHeader
import com.baseflow.api.DOCUMENTEN_API_VERSION
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.util.UUID

/**
 * Routes for EnkelvoudigInformatieObjecten (Single Information Objects).
 */
fun Route.enkelvoudigInformatieObjectenRoutes() {
    // Ensure API-version header is added for all responses under this subtree,
    // including tests that don't install the plugin at the parent route.
    install(ApiVersionHeader) { version = DOCUMENTEN_API_VERSION }
    val service = EnkelvoudigInformatieObjectService()

    // List all documents (with optional filters)
    get {
        val bronOrganisatie = call.request.queryParameters["bronorganisatie"]
        val trefwoorden = call.request.queryParameters.getAll("trefwoorden") ?: emptyList()
        val identificatie = call.request.queryParameters["identificatie"]
        val expand = call.request.queryParameters.getAll("expand") ?: emptyList()
        val page = call.request.queryParameters["page"]?.toIntOrNull() ?: 0

        var filter = QueryEnkelvoudigeInformatieObjectenFilter(
            bronOrganisatie = bronOrganisatie,
            trefwoorden = trefwoorden,
            identificatie = identificatie,
            expand = expand,
            page = page
        )

        val items = service.getAll(filter)
        val response = PaginatedResponse(
            count = items.size,
            next = null,
            previous = null,
            results = items
        )
        call.respond(response)
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
            val uuidString = call.parameters["uuid"]
            if (uuidString == null) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "UUID parameter is required"))
                return@post
            }

            try {
                val uuid = UUID.fromString(uuidString)
                val result = service.lock(uuid)
                when (result) {
                    null -> call.respond(HttpStatusCode.NotFound, mapOf("error" to "EnkelvoudigInformatieObject not found"))
                    is LockResult.Success -> call.respond(result.payload)
                    is LockResult.AlreadyLocked -> call.respond(
                        HttpStatusCode.Conflict,
                        mapOf("error" to "EnkelvoudigInformatieObject is already locked")
                    )
                }
            } catch (e: IllegalArgumentException) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid UUID format"))
            }
        }

        // Unlock document
        post("/unlock") {
            val uuidString = call.parameters["uuid"]
            if (uuidString == null) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "UUID parameter is required"))
                return@post
            }

            try {
                val uuid = UUID.fromString(uuidString)
                val body = call.receive<com.baseflow.api.models.UnlockEIORequest>()
                val result = service.unlock(uuid, body.lock)
                when (result) {
                    null -> call.respond(HttpStatusCode.NotFound, mapOf("error" to "EnkelvoudigInformatieObject not found"))
                    is UnlockResult.Success -> call.respond(HttpStatusCode.NoContent)
                    is UnlockResult.InvalidLock -> call.respond(
                        HttpStatusCode.Conflict,
                        mapOf("error" to "Invalid lock token for unlock")
                    )
                    is UnlockResult.NotLocked -> call.respond(
                        HttpStatusCode.Conflict,
                        mapOf("error" to "EnkelvoudigInformatieObject is not locked")
                    )
                }
            } catch (e: IllegalArgumentException) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid UUID format"))
            }
        }
    }
}
