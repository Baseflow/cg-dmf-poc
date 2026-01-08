// SPDX-License-Identifier: EUPL-1.2
// Copyright (C) 2025 Gemeente Utrecht
package com.baseflow.api.routes

import com.baseflow.api.ApiUrlBuilder
import com.baseflow.api.models.CreateEIORequest
import com.baseflow.api.models.PaginatedResponse
import com.baseflow.api.models.UnlockEIORequest
import com.baseflow.config.ApplicationConfig
import com.baseflow.services.EnkelvoudigInformatieObjectService
import com.baseflow.services.StorageService
import com.baseflow.services.models.DeleteResult
import com.baseflow.services.models.LockResult
import com.baseflow.services.models.QueryEnkelvoudigeInformatieObjectenFilter
import com.baseflow.services.models.UnlockResult
import com.baseflow.api.middleware.ApiVersionHeader
import com.baseflow.api.models.respondProblem
import com.baseflow.api.models.badRequest
import com.baseflow.api.models.notFound
import com.baseflow.api.models.conflict
import com.baseflow.api.DOCUMENTEN_API_VERSION
import io.ktor.http.ContentDisposition
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.util.UUID

/**
 * Routes for EnkelvoudigInformatieObjecten (Single Information Objects).
 */

private const val RESOURCE_SEGMENT = "enkelvoudiginformatieobjecten"

fun Route.enkelvoudigInformatieObjectenRoutes() {
    // Ensure API-version header is added for all responses under this subtree,
    // including tests that don't install the plugin at the parent route.
    install(ApiVersionHeader) { version = DOCUMENTEN_API_VERSION }
    val service = EnkelvoudigInformatieObjectService(StorageService(), ApplicationConfig)

    // List all documents (with optional filters)
    get {
        val bronOrganisatie = call.request.queryParameters["bronorganisatie"]
        val trefwoorden = call.request.queryParameters.getAll("trefwoorden") ?: emptyList()
        val identificatie = call.request.queryParameters["identificatie"]
        val expand = call.request.queryParameters.getAll("expand") ?: emptyList()
        val page = call.request.queryParameters["page"]?.toIntOrNull() ?: 0

        val filter = QueryEnkelvoudigeInformatieObjectenFilter(
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

        // Location header with the URL of the created resource
        val locationUrl = ApiUrlBuilder.absolute(RESOURCE_SEGMENT, response.id)
        call.response.headers.append(HttpHeaders.Location, locationUrl)

        call.respond(HttpStatusCode.Created, response)
    }

    // Advanced search endpoint
    post("/_zoek") {
        call.respond(mapOf("message" to "Search EnkelvoudigInformatieObject - to be implemented"))
    }

    // Single document operations
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
                    call.respondProblem(HttpStatusCode.NotFound, notFound("EnkelvoudigInformatieObject not found", call.request.path()))
                }
            } catch (_: IllegalArgumentException) {
                call.respondProblem(HttpStatusCode.BadRequest, badRequest("Invalid UUID format", call.request.path()))
            }
        }
        // Get single document
        get {
            // TODO add version and registratieOp query parameters support
            val uuidString = call.parameters["uuid"]
            if (uuidString == null) {
                call.respondProblem(HttpStatusCode.BadRequest, badRequest("UUID parameter is required", call.request.path()))
                return@get
            }

            try {
                val uuid = UUID.fromString(uuidString)
                val result = service.getById(uuid)

                if (result == null) {
                    call.respondProblem(HttpStatusCode.NotFound, notFound("EnkelvoudigInformatieObject not found", call.request.path()))
                } else {
                    call.respond(HttpStatusCode.OK, result)
                }
            } catch (_: IllegalArgumentException) {
                call.respondProblem(HttpStatusCode.BadRequest, badRequest("Invalid UUID format", call.request.path()))
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
            val uuidString = call.parameters["uuid"]
            if (uuidString == null) {
                call.respondProblem(HttpStatusCode.BadRequest, badRequest("UUID parameter is required", call.request.path()))
                return@delete
            }

            try {
                val uuid = UUID.fromString(uuidString)
                when (service.delete(uuid)) {
                    is DeleteResult.Success -> call.respond(HttpStatusCode.NoContent)
                    is DeleteResult.NotFound -> call.respondProblem(
                        HttpStatusCode.NotFound,
                        notFound("EnkelvoudigInformatieObject not found", call.request.path())
                    )
                    is DeleteResult.Locked -> call.respondProblem(
                        HttpStatusCode.Conflict,
                        conflict("EnkelvoudigInformatieObject is locked", call.request.path())
                    )
                }
            } catch (_: IllegalArgumentException) {
                call.respondProblem(HttpStatusCode.BadRequest, badRequest("Invalid UUID format", call.request.path()))
            }
        }

        // Download document content (streamed from storage)
        get("/download") {
            // TODO add version and registratieOp query parameters support
            val uuidString = call.parameters["uuid"]
            if (uuidString == null) {
                call.respondProblem(HttpStatusCode.BadRequest, badRequest("UUID parameter is required", call.request.path()))
                return@get
            }

            try {
                val uuid = UUID.fromString(uuidString)
                val eio = service.getById(uuid)
                if (eio == null) {
                    call.respondProblem(
                        HttpStatusCode.NotFound,
                        notFound("EnkelvoudigInformatieObject not found", call.request.path())
                    )
                    return@get
                }

                // Ensure we have a stored object key to stream
                val objectKey = eio.bestandsnaam
                if (objectKey.isNullOrBlank()) {
                    call.respondProblem(
                        HttpStatusCode.NotFound,
                        notFound("Document content not available for download", call.request.path())
                    )
                    return@get
                }

                // Derive filename and content type when possible;
                val fileName = objectKey.ifBlank({ "document-${eio.id}}" } )
                val contentType = try {
                    // eio.formaat is expected to be a MIME type; if not, fallback below
                    eio.formaat?.let { ContentType.parse(it) }
                } catch (_: Exception) {
                    ContentType.Application.OctetStream
                }

                // Set headers before starting the stream
                call.response.headers.append(
                    HttpHeaders.ContentDisposition,
                    ContentDisposition.Attachment
                        .withParameter(ContentDisposition.Parameters.FileName, fileName)
                        .toString()
                )
                call.response.headers.append(HttpHeaders.ContentType, contentType.toString())
                // TODO: support Range requests, ETag, Last-Modified when metadata is available

                // Stream the object from storage directly to the HTTP response
                call.respondOutputStream {
                    service.streamByBestandsnaam(bestandsnaam = objectKey, output = this)
                }
            } catch (_: IllegalArgumentException) {
                call.respondProblem(HttpStatusCode.BadRequest, badRequest("Invalid UUID format", call.request.path()))
            }
        }

        // Lock document for editing
        post("/lock") {
            val uuidString = call.parameters["uuid"]
            if (uuidString == null) {
                call.respondProblem(HttpStatusCode.BadRequest, badRequest("UUID parameter is required", call.request.path()))
                return@post
            }

            try {
                val uuid = UUID.fromString(uuidString)
                val result = service.lock(uuid)
                when (result) {
                    null -> call.respondProblem(HttpStatusCode.NotFound, notFound("EnkelvoudigInformatieObject not found", call.request.path()))
                    is LockResult.Success -> call.respond(result.payload)
                    is LockResult.AlreadyLocked -> call.respondProblem(
                        HttpStatusCode.Conflict,
                        conflict("EnkelvoudigInformatieObject is already locked", call.request.path())
                    )
                }
            } catch (_: IllegalArgumentException) {
                call.respondProblem(HttpStatusCode.BadRequest, badRequest("Invalid UUID format", call.request.path()))
            }
        }

        // Unlock document
        post("/unlock") {
            val uuidString = call.parameters["uuid"]
            if (uuidString == null) {
                call.respondProblem(HttpStatusCode.BadRequest, badRequest("UUID parameter is required", call.request.path()))
                return@post
            }

            try {
                val uuid = UUID.fromString(uuidString)
                val body = call.receive<UnlockEIORequest>()
                when (service.unlock(uuid, body.lock)) {
                    is UnlockResult.Success -> call.respond(HttpStatusCode.NoContent)
                    is UnlockResult.InvalidLock -> call.respondProblem(
                        HttpStatusCode.Conflict,
                        conflict("Invalid lock token for unlock", call.request.path())
                    )
                    is UnlockResult.NotLocked -> call.respondProblem(
                        HttpStatusCode.Conflict,
                        conflict("EnkelvoudigInformatieObject is not locked", call.request.path())
                    )
                    null -> call.respondProblem(HttpStatusCode.NotFound, notFound("EnkelvoudigInformatieObject not found", call.request.path()))
                }
            } catch (_: IllegalArgumentException) {
                call.respondProblem(HttpStatusCode.BadRequest, badRequest("Invalid UUID format", call.request.path()))
            }
        }
    }
}
