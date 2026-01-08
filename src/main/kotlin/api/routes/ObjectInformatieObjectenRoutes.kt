// SPDX-License-Identifier: EUPL-1.2
// Copyright (C) 2025-2026 Gemeente Utrecht

package com.baseflow.api.routes

import com.baseflow.api.ApiUrlBuilder
import com.baseflow.api.DOCUMENTEN_API_VERSION
import com.baseflow.api.middleware.ApiVersionHeader
import com.baseflow.api.models.*
import com.baseflow.services.ObjectInformatieObjectService
import com.baseflow.services.models.CreateOIOResult
import com.baseflow.services.models.DeleteOIOResult
import com.baseflow.services.models.QueryObjectInformatieObjectenFilter
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.util.UUID

/**
 * ObjectInformatieObject routes
 *
 * Handles relations between documents and other objects (extended beyond Zaken):
 * - POST / - Create relation
 * - GET / - List relations (with filtering)
 * - GET /{uuid} - Get single relation
 * - HEAD /{uuid} - Check existence
 * - DELETE /{uuid} - Delete relation
 *
 * This PoC extends the standard to support additional object types beyond Zaken.
 */

private const val RESOURCE_SEGMENT = "objectinformatieobjecten"

fun Route.objectInformatieObjectenRoutes() {
    // Ensure API-version header is added for all responses
    install(ApiVersionHeader) { version = DOCUMENTEN_API_VERSION }

    val service = ObjectInformatieObjectService(RESOURCE_SEGMENT)

    // List all document-object relations (with optional filters)
    get {
        val informatieobject = call.request.queryParameters["informatieobject"]
        val subjectObject = call.request.queryParameters["object"]
        val expand = call.request.queryParameters.getAll("expand") ?: emptyList()

        val filter = QueryObjectInformatieObjectenFilter(
            informatieobject = informatieobject,
            subjectObject = subjectObject,
            expand = expand
        )

        val items = service.getAll(filter)
        call.respond(HttpStatusCode.OK, items)
    }

    // Create new document-object relation
    post {
        val request = call.receive<CreateOIORequest>()

        when (val result = service.create(request)) {
            is CreateOIOResult.Success -> {
                val locationUrl = ApiUrlBuilder.absolute(RESOURCE_SEGMENT, result.payload.url?.substringAfterLast("/") ?: "")
                call.response.headers.append(HttpHeaders.Location, locationUrl)
                call.respond(HttpStatusCode.Created, result.payload)
            }
            is CreateOIOResult.Conflict -> {
                call.respondProblem(
                    HttpStatusCode.BadRequest,
                    badRequest(result.message, call.request.path())
                )
            }
        }
    }

    // Single relation operations
    route("/{uuid}") {
        // HEAD - existence check
        head {
            val uuidString = call.parameters["uuid"]
            if (uuidString == null) {
                call.respondProblem(HttpStatusCode.BadRequest, badRequest("UUID parameter is required", call.request.path()))
                return@head
            }

            try {
                val uuid = UUID.fromString(uuidString)
                if (service.exists(uuid)) {
                    call.respond(HttpStatusCode.OK)
                } else {
                    call.respondProblem(HttpStatusCode.NotFound, notFound("ObjectInformatieObject not found", call.request.path()))
                }
            } catch (_: IllegalArgumentException) {
                call.respondProblem(HttpStatusCode.BadRequest, badRequest("Invalid UUID format", call.request.path()))
            }
        }

        // Get single relation
        get {
            val uuidString = call.parameters["uuid"]
            if (uuidString == null) {
                call.respondProblem(HttpStatusCode.BadRequest, badRequest("UUID parameter is required", call.request.path()))
                return@get
            }

            try {
                val uuid = UUID.fromString(uuidString)
                val result = service.getById(uuid)

                if (result == null) {
                    call.respondProblem(HttpStatusCode.NotFound, notFound("ObjectInformatieObject not found", call.request.path()))
                } else {
                    call.respond(HttpStatusCode.OK, result)
                }
            } catch (_: IllegalArgumentException) {
                call.respondProblem(HttpStatusCode.BadRequest, badRequest("Invalid UUID format", call.request.path()))
            }
        }

        // Delete relation
        delete {
            val uuidString = call.parameters["uuid"]
            if (uuidString == null) {
                call.respondProblem(HttpStatusCode.BadRequest, badRequest("UUID parameter is required", call.request.path()))
                return@delete
            }

            try {
                val uuid = UUID.fromString(uuidString)
                when (service.delete(uuid)) {
                    is DeleteOIOResult.Success -> call.respond(HttpStatusCode.NoContent)
                    is DeleteOIOResult.NotFound -> call.respondProblem(
                        HttpStatusCode.NotFound,
                        notFound("ObjectInformatieObject not found", call.request.path())
                    )
                }
            } catch (_: IllegalArgumentException) {
                call.respondProblem(HttpStatusCode.BadRequest, badRequest("Invalid UUID format", call.request.path()))
            }
        }
    }
}
