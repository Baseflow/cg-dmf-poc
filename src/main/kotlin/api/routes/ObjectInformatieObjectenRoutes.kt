// SPDX-License-Identifier: EUPL-1.2
// Copyright (C) 2025-2026 Gemeente Utrecht
package com.baseflow.api.routes

import com.baseflow.api.ApiUrlBuilder
import com.baseflow.api.DOCUMENTEN_API_VERSION
import com.baseflow.api.middleware.ApiVersionHeader
import com.baseflow.api.middleware.RequestScopeKey
import com.baseflow.api.models.*
import com.baseflow.services.ObjectInformatieObjectService
import com.baseflow.services.models.CreateOIOResult
import com.baseflow.services.models.DeleteOIOResult
import com.baseflow.services.models.QueryObjectInformatieObjectenFilter
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.koin.core.parameter.parametersOf
import java.util.*

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

/**
 * Common implementation for ObjectInformatieObject routes.
 * Used by both ObjectInformatieObjecten and SubjectInformatieObjecten.
 */
open class ObjectInformatieObjectenRoutes(
    private val route: Route,
    private val resourceSegment: String,
    private val experimental: Boolean = false,
) {
    fun register() {
        with(route) {
            // Ensure API-version header is added for all responses
            install(ApiVersionHeader) { version = DOCUMENTEN_API_VERSION }

            // List all document-object relations (with optional filters)
            get { list() }

            // Create new document-object relation
            post { create() }

            // Single relation operations
            route("/{uuid}") {
                val resourceTitle = if (resourceSegment ==
                    "subjectinformatieobjecten"
                ) {
                    "SubjectInformatieObject"
                } else {
                    "ObjectInformatieObject"
                }
                // HEAD - existence check
                head { head(resourceTitle) }

                // Get single relation
                get { get(resourceTitle) }

                // Delete relation
                delete { delete(resourceTitle) }
            }
        }
    }

    private suspend fun RoutingContext.list() {
        val informatieobject = call.request.queryParameters["informatieobject"]
        val subjectObject = call.request.queryParameters["object"]
        val expand = call.request.queryParameters.getAll("expand") ?: emptyList()
        val page = call.request.queryParameters["page"]?.toIntOrNull() ?: 1
        val pageSize = call.request.queryParameters["pageSize"]?.toIntOrNull() ?: 100

        val filter = QueryObjectInformatieObjectenFilter(
            informatieobject = informatieobject,
            subjectObject = subjectObject,
            expand = expand,
            page = page,
            pageSize = pageSize,
        )

        val (items, totalCount) = service.getAll(filter)

        if (experimental) {
            call.respond(PaginatedResponse.from(call, resourceSegment, items, totalCount, page, pageSize))
        } else {
            // Note: ObjectInformatieObjecten are not paginated in the specification,
            // Changing this is a breaking API change.
            call.respond(HttpStatusCode.OK, items)
        }
    }

    private suspend fun RoutingContext.create() {
        val request = call.receive<CreateOIORequest>()

        when (val result = service.create(request)) {
            is CreateOIOResult.Success -> {
                val locationUrl = ApiUrlBuilder.absolute(
                    resourceSegment,
                    result.payload.url?.substringAfterLast("/") ?: "",
                )
                call.response.headers.append(HttpHeaders.Location, locationUrl)
                call.respond(HttpStatusCode.Created, result.payload)
            }
            is CreateOIOResult.Conflict -> {
                call.respondProblem(
                    HttpStatusCode.BadRequest,
                    badRequest(result.message, call.request.path()),
                )
            }
        }
    }

    private suspend fun RoutingContext.head(resourceTitle: String) {
        val uuidString = call.parameters["uuid"]
        if (uuidString == null) {
            call.respondProblem(
                HttpStatusCode.BadRequest,
                badRequest("UUID parameter is required", call.request.path()),
            )
            return
        }

        try {
            val uuid = UUID.fromString(uuidString)
            if (service.exists(uuid)) {
                call.respond(HttpStatusCode.OK)
            } else {
                call.respondProblem(
                    HttpStatusCode.NotFound,
                    notFound("$resourceTitle not found", call.request.path()),
                )
            }
        } catch (_: IllegalArgumentException) {
            call.respondProblem(HttpStatusCode.BadRequest, badRequest("Invalid UUID format", call.request.path()))
        }
    }

    private suspend fun RoutingContext.get(resourceTitle: String) {
        val uuidString = call.parameters["uuid"]
        if (uuidString == null) {
            call.respondProblem(
                HttpStatusCode.BadRequest,
                badRequest("UUID parameter is required", call.request.path()),
            )
            return
        }

        try {
            val uuid = UUID.fromString(uuidString)
            val result = service.getById(uuid)

            if (result == null) {
                call.respondProblem(
                    HttpStatusCode.NotFound,
                    notFound("$resourceTitle not found", call.request.path()),
                )
            } else {
                call.respond(HttpStatusCode.OK, result)
            }
        } catch (_: IllegalArgumentException) {
            call.respondProblem(HttpStatusCode.BadRequest, badRequest("Invalid UUID format", call.request.path()))
        }
    }

    private suspend fun RoutingContext.delete(resourceTitle: String) {
        val uuidString = call.parameters["uuid"]
        if (uuidString == null) {
            call.respondProblem(
                HttpStatusCode.BadRequest,
                badRequest("UUID parameter is required", call.request.path()),
            )
            return
        }

        try {
            val uuid = UUID.fromString(uuidString)
            when (service.delete(uuid)) {
                is DeleteOIOResult.Success -> call.respond(HttpStatusCode.NoContent)
                is DeleteOIOResult.NotFound -> call.respondProblem(
                    HttpStatusCode.NotFound,
                    notFound("$resourceTitle not found", call.request.path()),
                )
            }
        } catch (_: IllegalArgumentException) {
            call.respondProblem(HttpStatusCode.BadRequest, badRequest("Invalid UUID format", call.request.path()))
        }
    }

    private val RoutingContext.service: ObjectInformatieObjectService
        // construct service by injecting resourceSegment
        get() = call.attributes[RequestScopeKey].inject<ObjectInformatieObjectService> {
            parametersOf(resourceSegment)
        }.value
}

fun Route.objectInformatieObjectenRoutes() {
    ObjectInformatieObjectenRoutes(this, "objectinformatieobjecten", experimental = false).register()
}
