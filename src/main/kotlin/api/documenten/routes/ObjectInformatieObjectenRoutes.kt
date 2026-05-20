// SPDX-License-Identifier: EUPL-1.2
// Copyright (C) 2025-2026 Gemeente Utrecht
package com.baseflow.api.documenten.routes

import com.baseflow.api.ApiUrlBuilder
import com.baseflow.api.DOCUMENTEN_API_VERSION
import com.baseflow.api.ResourceUuidParser
import com.baseflow.api.middleware.ApiVersionHeader
import com.baseflow.api.models.*
import com.baseflow.services.ObjectInformatieObjectService
import com.baseflow.services.models.CreateOIOResult
import com.baseflow.services.models.DeleteOIOResult
import com.baseflow.services.models.QueryObjectInformatieObjectenFilter
import io.ktor.http.*
import io.ktor.openapi.jsonSchema
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.routing.openapi.describe
import io.ktor.utils.io.ExperimentalKtorApi
import org.koin.core.context.GlobalContext
import org.koin.core.parameter.parametersOf
import org.koin.ktor.plugin.scope
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
    private val resourceSegment: ResourceSegments,
    private val experimental: Boolean = false,
) {
    @OptIn(ExperimentalKtorApi::class)
    fun register() {
        with(route) {
            // Ensure API-version header is added for all responses
            install(ApiVersionHeader) { version = DOCUMENTEN_API_VERSION }

            val tag = resourceSegment.value

            /**
             * Alle OBJECT-INFORMATIEOBJECT relaties opvragen.
             *
             * Deze lijst kan gefilterd worden met query-string parameters.
             *
             * Query parameters:
             *   - `informatieobject`: Filter op URL-referentie naar het INFORMATIEOBJECT.
             *   - `object`: Filter op URL-referentie naar het gerelateerde OBJECT.
             *   - `expand`: Velden om te expanderen.
             *   - `page`: Paginanummer.
             *   - `pageSize`: Aantal resultaten per pagina.
             *
             * Responses:
             *   - 200 Lijst van OBJECT-INFORMATIEOBJECT relaties.
             *   - 400 Bad request.
             *   - 401 Unauthorized.
             *   - 403 Forbidden.
             *   - 406 Not acceptable.
             *   - 409 Conflict.
             *   - 410 Gone.
             *   - 415 Unsupported media type.
             *   - 429 Too many requests.
             *   - 500 Internal server error.
             *
             * @tag ObjectInformatieObjecten
             */
            get { list() }
                .describe {
                    operationId = "${tag}_list"
                    tag(tag)
                    summary = "Alle ${resourceSegment.title} relaties opvragen."
                    description = "Geeft een lijst van OBJECTINFORMATIEOBJECT relaties, gefilterd via query-parameters."
                    parameters {
                        query("informatieobject") {
                            description = "Filter op URL-referentie naar het INFORMATIEOBJECT."
                        }
                        query("object") { description = "Filter op URL-referentie naar het gerelateerde OBJECT." }
                        query("expand") { description = "Velden om te expanderen." }
                        query("page") { description = "**EXPERIMENTEEL** Paginanummer." }
                        query("pageSize") { description = "**EXPERIMENTEEL** Aantal resultaten per pagina." }
                    }
                    responses {
                        response(200) {
                            description = "Lijst van ${resourceSegment.title} relaties."
                            ContentType.Application.Json { schema = jsonSchema<List<ObjectInformatieObjectResponse>>() }
                        }
                        response(400) { description = "Bad request." }
                        response(401) { description = "Unauthorized." }
                        response(403) { description = "Forbidden." }
                    }
                }

            /**
             * Maak een OBJECT-INFORMATIEOBJECT relatie aan.
             *
             * **LET OP: Dit endpoint hoor je als consumer niet zelf aan te spreken.**
             * Andere API's, zoals de Zaken API en de Besluiten API, gebruiken dit endpoint
             * bij het synchroniseren van relaties.
             *
             * Responses:
             *   - 201 Created.
             *   - 400 Bad request.
             *   - 401 Unauthorized.
             *   - 403 Forbidden.
             *   - 406 Not acceptable.
             *   - 409 Conflict.
             *   - 410 Gone.
             *   - 415 Unsupported media type.
             *   - 429 Too many requests.
             *   - 500 Internal server error.
             *
             * @tag ObjectInformatieObjecten
             */
            post { create() }
                .describe {
                    operationId = "${tag}_create"
                    tag(tag)
                    summary = "Maak een ${resourceSegment.title} relatie aan."
                    description =
                        "LET OP: Dit endpoint hoor je als consumer niet zelf aan te spreken. " +
                        "Andere API's gebruiken dit endpoint bij het synchroniseren van relaties."
                    requestBody {
                        required = true
                        description = "Gegevens van de aan te maken relatie."
                        content {
                            schema = jsonSchema<CreateOIORequest>()
                        }
                    }
                    responses {
                        response(201) {
                            description = "Aangemaakt."
                            ContentType.Application.Json { schema = jsonSchema<ObjectInformatieObjectResponse>() }
                            headers {
                                header("Location") { description = "URL van de aangemaakte relatie." }
                                header("API-version") { description = "Geeft de specifieke API-versie aan." }
                            }
                        }
                        response(400) { description = "Bad request." }
                        response(401) { description = "Unauthorized." }
                        response(403) { description = "Forbidden." }
                    }
                }

            /**
             * Verwijder OBJECT-INFORMATIEOBJECT relaties op basis van een filter.
             *
             * Verwijdert alle relaties die voldoen aan het opgegeven filter. Precies één van de
             * volgende query parameters is verplicht:
             *
             * Query parameters:
             *   - `informatieobject`: URL van de EIO waarvoor alle relaties verwijderd worden.
             *     Moet een geldige URL zijn van de vorm `.../enkelvoudiginformatieobjecten/{uuid}`.
             *   - `object`: URL van het subject-object waarvoor alle relaties verwijderd worden.
             *
             * Responses:
             *   - 204 No content.
             *   - 400 Bad request: ontbrekende of ongeldige parameter.
             *   - 404 Not found: geen relaties gevonden voor het opgegeven filter.
             *
             * @tag ObjectInformatieObjecten
             */
            delete { deleteByFilter() }
                .describe {
                    operationId = "${tag}_delete_by_informatieobject"
                    tag(tag)
                    summary = "Verwijder ${resourceSegment.title} relaties op basis van informatieobject of object."
                    description =
                        "**EXPERIMENTEEL** Verwijdert alle relaties die gekoppeld zijn aan de opgegeven EIO " +
                        "(`informatieobject`) of het opgegeven subject-object (`object`). " +
                        "Precies één van beide parameters is verplicht; het opgeven van beide geeft een 400."
                    parameters {
                        query("informatieobject") {
                            description =
                                "URL van de EIO (informatieobject) waarvoor alle relaties verwijderd worden."
                        }
                        query("object") {
                            description =
                                "URL van het subject-object waarvoor alle relaties verwijderd worden."
                        }
                    }
                    responses {
                        response(204) { description = "No content." }
                        response(400) { description = "Bad request: ontbrekende of ongeldige parameter." }
                        response(404) {
                            description = "Not found: geen relaties gevonden voor de opgegeven parameter."
                        }
                    }
                }

            // Single relation operations
            route("/{uuid}") {
                val resourceTitle = resourceSegment.title

                /**
                 * De headers voor een specifiek(e) OBJECT-INFORMATIEOBJECT opvragen.
                 *
                 * Vraag de headers op die je bij een GET request zou krijgen.
                 *
                 * Responses:
                 *   - 200 OK.
                 *   - 400 (missing/invalid UUID).
                 *   - 404 Not found.
                 *
                 * @tag ObjectInformatieObjecten
                 */
                head { head(resourceTitle) }
                    .describe {
                        operationId = "${tag}_headers"
                        tag(tag)
                        summary = "De headers voor een specifieke ${resourceSegment.title} opvragen."
                        parameters {
                            path("uuid") { description = "Unieke resource identifier (UUID4)." }
                        }
                        responses {
                            response(200) { description = "OK." }
                            response(400) { description = "Bad request: ontbrekende of ongeldige UUID." }
                            response(404) { description = "Not found." }
                        }
                    }

                /**
                 * Een specifieke OBJECT-INFORMATIEOBJECT relatie opvragen.
                 *
                 * Responses:
                 *   - 200 OK.
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
                 * @tag ObjectInformatieObjecten
                 */
                get { get(resourceTitle) }
                    .describe {
                        operationId = "${tag}_read"
                        tag(tag)
                        summary = "Een specifieke ${resourceSegment.title} relatie opvragen."
                        parameters {
                            path("uuid") { description = "Unieke resource identifier (UUID4)." }
                        }
                        responses {
                            response(200) {
                                description = "OK."
                                ContentType.Application.Json { schema = jsonSchema<ObjectInformatieObjectResponse>() }
                            }
                            response(401) { description = "Unauthorized." }
                            response(403) { description = "Forbidden." }
                            response(404) { description = "Not found." }
                        }
                    }

                /**
                 * Verwijder een OBJECT-INFORMATIEOBJECT relatie.
                 *
                 * **LET OP: Dit endpoint hoor je als consumer niet zelf aan te spreken.**
                 * Andere API's, zoals de Zaken API en de Besluiten API, gebruiken dit endpoint
                 * bij het synchroniseren van relaties.
                 *
                 * Responses:
                 *   - 204 No content.
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
                 * @tag ObjectInformatieObjecten
                 */
                delete { delete(resourceTitle) }
                    .describe {
                        operationId = "${tag}_delete"
                        tag(tag)
                        summary = "Verwijder een ${resourceSegment.title} relatie."
                        description =
                            "LET OP: Dit endpoint hoor je als consumer niet zelf aan te spreken. " +
                            "Andere API's gebruiken dit endpoint bij het synchroniseren van relaties."
                        parameters {
                            path("uuid") { description = "Unieke resource identifier (UUID4)." }
                        }
                        responses {
                            response(204) { description = "No content." }
                            response(401) { description = "Unauthorized." }
                            response(403) { description = "Forbidden." }
                            response(404) { description = "Not found." }
                        }
                    }
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
            call.respond(PaginatedResponse.from(call, resourceSegment.value, items, totalCount, page, pageSize))
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
                val locationUrl =
                    ApiUrlBuilder.absolute(
                        resourceSegment.value,
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

    private suspend fun RoutingContext.deleteByFilter() {
        val informatieObjectUrl = call.request.queryParameters["informatieobject"]
        val subjectObjectUrl = call.request.queryParameters["object"]

        if (informatieObjectUrl != null && subjectObjectUrl != null) {
            call.respondProblem(
                HttpStatusCode.BadRequest,
                badRequest(
                    "Provide either 'informatieobject' or 'object', not both.",
                    call.request.path(),
                ),
            )
            return
        }

        when {
            informatieObjectUrl != null -> {
                val id = ResourceUuidParser.parseUuid(
                    informatieObjectUrl,
                    ResourceSegments.ENKELVOUDIG_INFORMATIE_OBJECTEN.value,
                )
                if (id == null) {
                    call.respondProblem(
                        HttpStatusCode.BadRequest,
                        badRequest(
                            "informatieobject must be a valid URL ending in .../enkelvoudiginformatieobjecten/{uuid}",
                            call.request.path(),
                        ),
                    )
                    return
                }
                when (service.deleteByEioId(id)) {
                    is DeleteOIOResult.Success -> call.respond(HttpStatusCode.NoContent)
                    is DeleteOIOResult.NotFound -> call.respondProblem(
                        HttpStatusCode.NotFound,
                        notFound(
                            "No OIO relations found for informatieobject $informatieObjectUrl",
                            call.request.path(),
                        ),
                    )
                }
            }

            subjectObjectUrl != null -> {
                when (service.deleteBySubjectObject(subjectObjectUrl)) {
                    is DeleteOIOResult.Success -> call.respond(HttpStatusCode.NoContent)
                    is DeleteOIOResult.NotFound -> call.respondProblem(
                        HttpStatusCode.NotFound,
                        notFound("No OIO relations found for object $subjectObjectUrl", call.request.path()),
                    )
                }
            }

            else -> call.respondProblem(
                HttpStatusCode.BadRequest,
                badRequest("Either 'informatieobject' or 'object' query parameter is required", call.request.path()),
            )
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
        get() = call.scope.get<ObjectInformatieObjectService> {
            parametersOf(resourceSegment)
        }
}

fun Route.objectInformatieObjectenRoutes() {
    ObjectInformatieObjectenRoutes(this, ResourceSegments.OBJECT_INFORMATIE_OBJECTEN, experimental = false).register()
}
