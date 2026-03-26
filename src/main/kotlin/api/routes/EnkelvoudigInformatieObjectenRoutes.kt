// SPDX-License-Identifier: EUPL-1.2
// Copyright (C) 2025-2026 Gemeente Utrecht
package com.baseflow.api.routes

import com.baseflow.api.ApiUrlBuilder
import com.baseflow.api.DOCUMENTEN_API_VERSION
import com.baseflow.api.middleware.ApiVersionHeader
import com.baseflow.api.middleware.RequestScopeKey
import com.baseflow.api.models.*
import com.baseflow.entities.EIORecordEntity
import com.baseflow.services.EnkelvoudigInformatieObjectService
import com.baseflow.services.models.DeleteResult
import com.baseflow.services.models.LockResult
import com.baseflow.services.models.QueryEnkelvoudigeInformatieObjectenFilter
import com.baseflow.services.models.UnlockResult
import io.ktor.http.*
import io.ktor.openapi.jsonSchema
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.routing.openapi.describe
import io.ktor.utils.io.ExperimentalKtorApi
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.util.*

/**
 * Routes for EnkelvoudigInformatieObjecten (Single Information Objects).
 */

val RESOURCE_SEGMENT = ResourceSegments.ENKELVOUDIG_INFORMATIE_OBJECTEN.value

@OptIn(ExperimentalKtorApi::class)
fun Route.enkelvoudigInformatieObjectenRoutes() {
    // Ensure API-version header is added for all responses under this subtree,
    // including tests that don't install the plugin at the parent route.
    install(ApiVersionHeader) { version = DOCUMENTEN_API_VERSION }

    /**
     * Alle (ENKELVOUDIGe) INFORMATIEOBJECTen opvragen.
     *
     * Deze lijst kan gefilterd worden met query-string parameters.
     * De objecten bevatten metadata over de documenten en de downloadlink (`inhoud`) naar de binary data.
     * Alleen de laatste versie van elk (ENKELVOUDIG) INFORMATIEOBJECT wordt getoond.
     *
     * Query parameters:
     *   - `bronorganisatie`: Filter op bronorganisatie.
     *   - `trefwoorden`: Filter op trefwoorden.
     *   - `identificatie`: Filter op identificatie.
     *   - `expand`: Velden om te expanderen.
     *   - `page`: Paginanummer.
     *   - `pageSize`: Aantal resultaten per pagina.
     *
     * Responses:
     *   - 200 Lijst van (ENKELVOUDIGe) INFORMATIEOBJECTen.
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
     * @tag EnkelvoudigInformatieObjecten
     */
    get { list() }
        .describe {
            operationId = "enkelvoudiginformatieobjecten_list"
            tag("enkelvoudiginformatieobjecten")
            summary = "Alle (enkelvoudige) informatieobjecten opvragen."
            description =
                "Geeft een gepagineerde lijst van enkelvoudige informatieobjecten. " +
                "Alleen de laatste versie van elk informatieobject wordt getoond."
            parameters {
                query("bronorganisatie") { description = "Filter op RSIN van de bronorganisatie." }
                query("trefwoorden") { description = "Filter op trefwoorden." }
                query("identificatie") { description = "Filter op identificatie." }
                query("expand") { description = "Velden om te expanderen." }
                query("page") { description = "Paginanummer." }
                query("pageSize") { description = "Aantal resultaten per pagina." }
                query("objectinformatieobjecten__object") {
                    description = "EXPERIMENTEEL: Filter op URL-referentie naar het gerelateerde object."
                }
                query("objectinformatieobjecten__objectType") { description = "EXPERIMENTEEL: Filter op objecttype." }
            }
            responses {
                response(200) { description = "Lijst van enkelvoudige informatieobjecten." }
                response(400) { description = "Bad request." }
                response(401) { description = "Unauthorized." }
                response(403) { description = "Forbidden." }
            }
        }

    /**
     * Maak een (ENKELVOUDIG) INFORMATIEOBJECT aan.
     *
     * **Er wordt gevalideerd op**
     * - geldigheid `informatieobjecttype` URL - de resource moet opgevraagd kunnen worden uit de catalogi API
     *   en de vorm van een INFORMATIEOBJECTTYPE hebben.
     * - publicatie `informatieobjecttype` - `concept` INFORMATIEOBJECTTYPE-en mogen niet gebruikt worden.
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
     * @tag EnkelvoudigInformatieObjecten
     */
    post { create() }
        .describe {
            operationId = "enkelvoudiginformatieobjecten_create"
            tag("enkelvoudiginformatieobjecten")
            summary = "Maak een enkelvoudig informatieobject aan."
            description = "Maak een (ENKELVOUDIG) INFORMATIEOBJECT aan."
            requestBody {
                required = true
                description = "Gegevens van het aan te maken informatieobject."
                content {
                    schema = jsonSchema<EnkelvoudigInformatieObjectRequest>()
                }
            }
            responses {
                response(201) {
                    description = "Aangemaakt."
                    headers {
                        header("Location") { description = "URL van het aangemaakte informatieobject." }
                        header("API-version") { description = "Geeft de specifieke API-versie aan." }
                    }
                }
                response(400) { description = "Bad request." }
                response(401) { description = "Unauthorized." }
                response(403) { description = "Forbidden." }
            }
        }

    /**
     * Voer een zoekopdracht uit op (ENKELVOUDIG) INFORMATIEOBJECTen.
     *
     * Zoeken/filteren gaat normaal via de `list` operatie, deze is echter niet geschikt
     * voor zoekopdrachten met UUIDs.
     *
     * Responses:
     *   - 200 Lijst van (ENKELVOUDIGe) INFORMATIEOBJECTen.
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
     * @tag EnkelvoudigInformatieObjecten
     */
    post("/_zoek") { zoek() }
        .describe {
            operationId = "enkelvoudiginformatieobjecten_zoek"
            tag("enkelvoudiginformatieobjecten")
            summary = "Voer een zoekopdracht uit op enkelvoudige informatieobjecten."
            description = "Zoeken/filteren op UUID of andere velden. Gebruik dit endpoint voor zoekopdrachten met UUIDs."
            requestBody {
                required = true
                description = "Zoekcriteria."
                content {
                    schema = jsonSchema<EIOZoekRequest>()
                }
            }
            responses {
                response(200) { description = "Lijst van gevonden enkelvoudige informatieobjecten." }
                response(400) { description = "Bad request." }
                response(401) { description = "Unauthorized." }
                response(403) { description = "Forbidden." }
            }
        }

    // Single document operations
    route("/{uuid}") {
        /**
         * De headers voor een specifiek(e) ENKELVOUDIG INFORMATIEOBJECT opvragen.
         *
         * Vraag de headers op die je bij een GET request zou krijgen.
         *
         * Responses:
         *   - 200 OK.
         *   - 400 missing/invalid UUID-parameter.
         *   - 404 Not found.
         *
         * @tag EnkelvoudigInformatieObjecten
         */
        head { head() }
            .describe {
                operationId = "enkelvoudiginformatieobjecten_headers"
                tag("enkelvoudiginformatieobjecten")
                summary = "De headers voor een specifiek enkelvoudig informatieobject opvragen."
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
         * Een specifiek (ENKELVOUDIG) INFORMATIEOBJECT opvragen.
         *
         * Het object bevat metadata over het document en de downloadlink (`inhoud`) naar de binary data.
         * Dit geeft standaard de laatste versie van het (ENKELVOUDIG) INFORMATIEOBJECT.
         * Specifieke versies kunnen worden opgevraagd via de `versie` query parameter.
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
         * @tag EnkelvoudigInformatieObjecten
         */
        get { get() }
            .describe {
                operationId = "enkelvoudiginformatieobjecten_read"
                tag("enkelvoudiginformatieobjecten")
                summary = "Een specifiek enkelvoudig informatieobject opvragen."
                description = "Geeft het informatieobject terug. Standaard de laatste versie."
                parameters {
                    path("uuid") { description = "Unieke resource identifier (UUID4)." }
                    query("versie") { description = "Specifieke versie van het informatieobject." }
                    query("registratieOp") { description = "Filtert op de registratiedatum." }
                }
                responses {
                    response(200) { description = "OK." }
                    response(401) { description = "Unauthorized." }
                    response(403) { description = "Forbidden." }
                    response(404) { description = "Not found." }
                }
            }

        /**
         * Werk een (ENKELVOUDIG) INFORMATIEOBJECT in zijn geheel bij.
         *
         * Dit creëert altijd een nieuwe versie van het (ENKELVOUDIG) INFORMATIEOBJECT.
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
         * @tag EnkelvoudigInformatieObjecten
         */
        put { put() }
            .describe {
                operationId = "enkelvoudiginformatieobjecten_update"
                tag("enkelvoudiginformatieobjecten")
                summary = "Werk een enkelvoudig informatieobject in zijn geheel bij."
                description = "Dit creëert altijd een nieuwe versie van het informatieobject."
                parameters {
                    path("uuid") { description = "Unieke resource identifier (UUID4)." }
                }
                requestBody {
                    required = true
                    description = "Bijgewerkte gegevens van het informatieobject."
                    content {
                        schema = jsonSchema<EnkelvoudigInformatieObjectRequest>()
                    }
                }
                responses {
                    response(200) { description = "OK." }
                    response(400) { description = "Bad request." }
                    response(401) { description = "Unauthorized." }
                    response(403) { description = "Forbidden." }
                    response(404) { description = "Not found." }
                }
            }

        /**
         * Werk een (ENKELVOUDIG) INFORMATIEOBJECT deels bij.
         *
         * Dit creëert altijd een nieuwe versie van het (ENKELVOUDIG) INFORMATIEOBJECT.
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
         * @tag EnkelvoudigInformatieObjecten
         */
        patch { patch() }
            .describe {
                operationId = "enkelvoudiginformatieobjecten_partial_update"
                tag("enkelvoudiginformatieobjecten")
                summary = "Werk een enkelvoudig informatieobject deels bij."
                description = "Dit creëert altijd een nieuwe versie van het informatieobject."
                parameters {
                    path("uuid") { description = "Unieke resource identifier (UUID4)." }
                }
                requestBody {
                    required = false
                    description = "Gedeeltelijk bijgewerkte gegevens van het informatieobject."
                    content {
                        schema = jsonSchema<EnkelvoudigInformatieObjectRequest>()
                    }
                }
                responses {
                    response(200) { description = "OK." }
                    response(400) { description = "Bad request." }
                    response(401) { description = "Unauthorized." }
                    response(403) { description = "Forbidden." }
                    response(404) { description = "Not found." }
                }
            }

        /**
         * Verwijder een (ENKELVOUDIG) INFORMATIEOBJECT.
         *
         * Verwijder een (ENKELVOUDIG) INFORMATIEOBJECT en alle bijbehorende versies, samen met alle
         * gerelateerde resources binnen deze API. Dit is alleen mogelijk als er geen
         * OBJECTINFORMATIEOBJECTen gerelateerd zijn aan het (ENKELVOUDIG) INFORMATIEOBJECT.
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
         * @tag EnkelvoudigInformatieObjecten
         */
        delete { delete() }
            .describe {
                operationId = "enkelvoudiginformatieobjecten_delete"
                tag("enkelvoudiginformatieobjecten")
                summary = "Verwijder een enkelvoudig informatieobject."
                description =
                    "Verwijdert het informatieobject en alle bijbehorende versies. " +
                    "Alleen mogelijk als er geen objectinformatieobjecten aan gerelateerd zijn."
                parameters {
                    path("uuid") { description = "Unieke resource identifier (UUID4)." }
                }
                responses {
                    response(204) { description = "No content." }
                    response(401) { description = "Unauthorized." }
                    response(403) { description = "Forbidden." }
                    response(404) { description = "Not found." }
                    response(409) { description = "Conflict: informatieobject is vergrendeld." }
                }
            }

        /**
         * Download de binaire data van het (ENKELVOUDIG) INFORMATIEOBJECT.
         *
         * Responses:
         *   - 200 OK (binary stream).
         *   - 401 Unauthorized.
         *   - 403 Forbidden.
         *   - 404 Not found.
         *   - 406 Not acceptable.
         *   - 410 Gone.
         *   - 415 Unsupported media type.
         *   - 429 Too many requests.
         *   - 500 Internal server error.
         *
         * @tag EnkelvoudigInformatieObjecten
         */
        get("/download") { download() }
            .describe {
                operationId = "enkelvoudiginformatieobjecten_download"
                tag("enkelvoudiginformatieobjecten")
                summary = "Download de binaire data van het informatieobject."
                parameters {
                    path("uuid") { description = "Unieke resource identifier (UUID4)." }
                }
                responses {
                    response(200) {
                        description = "OK — binaire bestandsinhoud."
                        headers {
                            header("Content-Disposition") { description = "Bestandsnaam voor de download." }
                            header("Content-Type") { description = "MIME-type van het bestand." }
                        }
                    }
                    response(401) { description = "Unauthorized." }
                    response(403) { description = "Forbidden." }
                    response(404) { description = "Not found." }
                }
            }

        /**
         * Vergrendel een (ENKELVOUDIG) INFORMATIEOBJECT.
         *
         * Voert een 'checkout' uit waardoor het (ENKELVOUDIG) INFORMATIEOBJECT vergrendeld wordt
         * met een `lock` waarde. Alleen met deze waarde kan het (ENKELVOUDIG) INFORMATIEOBJECT
         * bijgewerkt (`PUT`, `PATCH`) en ontgrendeld worden.
         *
         * Responses:
         *   - 200 OK (lock value returned).
         *   - 400 Bad request.
         *   - 401 Unauthorized.
         *   - 403 Forbidden.
         *   - 404 Not found.
         *   - 406 Not acceptable.
         *   - 410 Gone.
         *   - 415 Unsupported media type.
         *   - 429 Too many requests.
         *   - 500 Internal server error.
         *
         * @tag EnkelvoudigInformatieObjecten
         */
        post("/lock") { lock() }
            .describe {
                operationId = "enkelvoudiginformatieobjecten_lock"
                tag("enkelvoudiginformatieobjecten")
                summary = "Vergrendel een enkelvoudig informatieobject."
                description =
                    "Voert een checkout uit waardoor het informatieobject vergrendeld wordt met een lock-waarde."
                parameters {
                    path("uuid") { description = "Unieke resource identifier (UUID4)." }
                }
                responses {
                    response(200) { description = "OK — lock-waarde teruggegeven." }
                    response(400) { description = "Bad request." }
                    response(401) { description = "Unauthorized." }
                    response(403) { description = "Forbidden." }
                    response(404) { description = "Not found." }
                    response(409) { description = "Conflict: informatieobject is al vergrendeld." }
                }
            }

        /**
         * Ontgrendel een (ENKELVOUDIG) INFORMATIEOBJECT.
         *
         * Heft de 'checkout' op waardoor het (ENKELVOUDIG) INFORMATIEOBJECT ontgrendeld wordt.
         *
         * Responses:
         *   - 204 No content.
         *   - 400 Bad request.
         *   - 401 Unauthorized.
         *   - 403 Forbidden.
         *   - 404 Not found.
         *   - 406 Not acceptable.
         *   - 410 Gone.
         *   - 415 Unsupported media type.
         *   - 429 Too many requests.
         *   - 500 Internal server error.
         *
         * @tag EnkelvoudigInformatieObjecten
         */
        post("/unlock") { unlock() }
            .describe {
                operationId = "enkelvoudiginformatieobjecten_unlock"
                tag("enkelvoudiginformatieobjecten")
                summary = "Ontgrendel een enkelvoudig informatieobject."
                description = "Heft de checkout op waardoor het informatieobject ontgrendeld wordt."
                parameters {
                    path("uuid") { description = "Unieke resource identifier (UUID4)." }
                }
                responses {
                    response(204) { description = "No content." }
                    response(400) { description = "Bad request." }
                    response(401) { description = "Unauthorized." }
                    response(403) { description = "Forbidden." }
                    response(404) { description = "Not found." }
                    response(409) { description = "Conflict: ongeldige lock-waarde of niet vergrendeld." }
                }
            }
    }
}

/**
 * Extension property to get the request-scoped EnkelvoudigInformatieObjectService.
 */
private val RoutingContext.service: EnkelvoudigInformatieObjectService
    get() = call.attributes[RequestScopeKey].get()

private suspend fun RoutingContext.list() {
    val bronOrganisatie = call.request.queryParameters["bronorganisatie"]
    val trefwoorden = call.request.queryParameters.getAll("trefwoorden") ?: emptyList()
    val identificatie = call.request.queryParameters["identificatie"]
    val expand = call.request.queryParameters.getAll("expand") ?: emptyList()
    val page = call.request.queryParameters["page"]?.toIntOrNull() ?: 1
    // Default pageSize 100 aligns with Open Zaak. Not in Documenten API 1.5.0 spec.
    val pageSize = call.request.queryParameters["pageSize"]?.toIntOrNull() ?: 100

    // EXPERIMENTEEL filters
    val objectUrl = call.request.queryParameters["objectinformatieobjecten__object"]
    val objectType = call.request.queryParameters["objectinformatieobjecten__objectType"]

    val filter = QueryEnkelvoudigeInformatieObjectenFilter(
        bronOrganisatie = bronOrganisatie,
        trefwoorden = trefwoorden,
        identificatie = identificatie,
        page = page,
        pageSize = pageSize,
        objectUrl = objectUrl,
        objectType = objectType,
    )

    val (items, totalCount) = service.getAll(filter)
    call.respond(PaginatedResponse.from(call, RESOURCE_SEGMENT, items, totalCount, page, pageSize))
}

private suspend fun RoutingContext.create() {
    val request = call.receive<EnkelvoudigInformatieObjectRequest>()
    try {
        val response = service.create(request)
        // Location header with the URL of the created resource
        val locationUrl = ApiUrlBuilder.absolute(RESOURCE_SEGMENT, response.id)
        call.response.headers.append(HttpHeaders.Location, locationUrl)

        call.respond(HttpStatusCode.Created, response)
    } catch (e: IllegalArgumentException) {
        call.respondProblem(

            HttpStatusCode.BadRequest,

            badRequest(e.message ?: "Validation failed", call.request.path()),
        )
        return
    }
}

private suspend fun RoutingContext.zoek() {
    val request = call.receive<EIOZoekRequest>()
    val expand = request.expand?.split(",")?.map { it.trim() } ?: emptyList()
    val queryParameters = call.request.queryParameters
    val page = queryParameters["page"]?.toIntOrNull() ?: 1
    // Default pageSize 100 aligns with Open Zaak. Not in Documenten API 1.5.0 spec.
    val pageSize = queryParameters["pageSize"]?.toIntOrNull() ?: 100

    // EXPERIMENTEEL filters
    val objectUrl = queryParameters["objectinformatieobjecten__object"]
    val objectType = queryParameters["objectinformatieobjecten__objectType"]

    val filter = QueryEnkelvoudigeInformatieObjectenFilter(
        uuids = request.uuidIn,
        page = page,
        pageSize = pageSize,
        objectUrl = objectUrl,
        objectType = objectType,
    )

    val (items, totalCount) = service.getAll(filter)
    val response = PaginatedResponse.from(call, RESOURCE_SEGMENT, items, totalCount, page, pageSize)

    call.respond(response)
}

private suspend fun RoutingContext.head() {
    val uuidString = call.parameters["uuid"]
    if (uuidString == null) {
        call.respondProblem(HttpStatusCode.BadRequest, badRequest("UUID parameter is required", call.request.path()))
        return
    }

    try {
        val uuid = UUID.fromString(uuidString)
        if (service.exists(uuid)) {
            call.respond(HttpStatusCode.OK)
        } else {
            call.respondProblem(

                HttpStatusCode.NotFound,

                notFound("EnkelvoudigInformatieObject not found", call.request.path()),
            )
        }
    } catch (_: IllegalArgumentException) {
        call.respondProblem(HttpStatusCode.BadRequest, badRequest("Invalid UUID format", call.request.path()))
    }
}

private suspend fun RoutingContext.get() {
    // TODO add version and registratieOp query parameters support
    val uuidString = call.parameters["uuid"]
    if (uuidString == null) {
        call.respondProblem(HttpStatusCode.BadRequest, badRequest("UUID parameter is required", call.request.path()))
        return
    }

    try {
        val uuid = UUID.fromString(uuidString)
        val result = service.getById(uuid)

        if (result == null) {
            call.respondProblem(

                HttpStatusCode.NotFound,

                notFound("EnkelvoudigInformatieObject not found", call.request.path()),
            )
        } else {
            call.respond(HttpStatusCode.OK, result)
        }
    } catch (_: IllegalArgumentException) {
        call.respondProblem(HttpStatusCode.BadRequest, badRequest("Invalid UUID format", call.request.path()))
    }
}

private suspend fun RoutingContext.put() {
    val uuidString = call.parameters["uuid"]
    if (uuidString == null) {
        call.respondProblem(HttpStatusCode.BadRequest, badRequest("UUID parameter is required", call.request.path()))
        return
    }

    try {
        val uuid = UUID.fromString(uuidString)
        val request = call.receive<EnkelvoudigInformatieObjectRequest>()
        val response = service.update(uuid, request)
        if (response == null) {
            call.respondProblem(

                HttpStatusCode.NotFound,

                notFound("EnkelvoudigInformatieObject not found", call.request.path()),
            )
            return
        }
        call.respond(HttpStatusCode.OK, response)
    } catch (e: IllegalArgumentException) {
        call.respondProblem(

            HttpStatusCode.BadRequest,

            badRequest(e.message ?: "Invalid UUID format", call.request.path()),
        )
        return
    }
}

private suspend fun RoutingContext.patch() {
    val uuidString = call.parameters["uuid"]
    if (uuidString == null) {
        call.respondProblem(HttpStatusCode.BadRequest, badRequest("UUID parameter is required", call.request.path()))
        return
    }
    try {
        val uuid = UUID.fromString(uuidString)
        val request = call.receive<EnkelvoudigInformatieObjectRequest>()
        val response = service.update(uuid, request, true)
        if (response == null) {
            call.respondProblem(

                HttpStatusCode.NotFound,

                notFound("EnkelvoudigInformatieObject not found", call.request.path()),
            )
            return
        }
        call.respond(HttpStatusCode.OK, response)
    } catch (e: IllegalArgumentException) {
        call.respondProblem(

            HttpStatusCode.BadRequest,

            badRequest(e.message ?: "Invalid UUID format", call.request.path()),
        )
    }
}

private suspend fun RoutingContext.delete() {
    val uuidString = call.parameters["uuid"]
    if (uuidString == null) {
        call.respondProblem(HttpStatusCode.BadRequest, badRequest("UUID parameter is required", call.request.path()))
        return
    }

    try {
        val uuid = UUID.fromString(uuidString)
        when (service.delete(uuid)) {
            is DeleteResult.Success -> {
                call.respond(HttpStatusCode.NoContent)
            }

            is DeleteResult.NotFound -> call.respondProblem(
                HttpStatusCode.NotFound,
                notFound("EnkelvoudigInformatieObject not found", call.request.path()),
            )

            is DeleteResult.Locked -> call.respondProblem(
                HttpStatusCode.Conflict,
                conflict("EnkelvoudigInformatieObject is locked", call.request.path()),
            )
        }
    } catch (_: IllegalArgumentException) {
        call.respondProblem(HttpStatusCode.BadRequest, badRequest("Invalid UUID format", call.request.path()))
    }
}

private suspend fun RoutingContext.download() {
    // TODO add version and registratieOp query parameters support
    val uuidString = call.parameters["uuid"]
    if (uuidString == null) {
        call.respondProblem(HttpStatusCode.BadRequest, badRequest("UUID parameter is required", call.request.path()))
        return
    }

    try {
        val uuid = UUID.fromString(uuidString)

        val eio = transaction {
            val record =
                EIORecordEntity.findById(uuid) ?: return@transaction null
            val eio = record.versions.maxByOrNull { it.versie }
            return@transaction eio
        }

        if (eio == null) {
            call.respondProblem(
                HttpStatusCode.NotFound,
                notFound("EnkelvoudigInformatieObject not found", call.request.path()),
            )
            return
        }

        // Ensure we have a stored object key to stream
        val objectKey = eio.bestandsLocatie
        if (objectKey.isBlank()) {
            call.respondProblem(
                HttpStatusCode.NotFound,
                notFound("Document content not available for download", call.request.path()),
            )
            return
        }

        // Derive filename and content type when possible;
        val fileName = objectKey.ifBlank { "document-${eio.id}" }
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
                .toString(),
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

private suspend fun RoutingContext.lock() {
    val uuidString = call.parameters["uuid"]
    if (uuidString == null) {
        call.respondProblem(HttpStatusCode.BadRequest, badRequest("UUID parameter is required", call.request.path()))
        return
    }

    try {
        val uuid = UUID.fromString(uuidString)
        when (val result = service.lock(uuid)) {
            null -> call.respondProblem(
                HttpStatusCode.NotFound,
                notFound("EnkelvoudigInformatieObject not found", call.request.path()),
            )

            is LockResult.Success -> call.respond(result.payload)
            is LockResult.AlreadyLocked -> call.respondProblem(
                HttpStatusCode.Conflict,
                conflict("EnkelvoudigInformatieObject is already locked", call.request.path()),
            )
        }
    } catch (_: IllegalArgumentException) {
        call.respondProblem(HttpStatusCode.BadRequest, badRequest("Invalid UUID format", call.request.path()))
    }
}

private suspend fun RoutingContext.unlock() {
    val uuidString = call.parameters["uuid"]
    if (uuidString == null) {
        call.respondProblem(HttpStatusCode.BadRequest, badRequest("UUID parameter is required", call.request.path()))
        return
    }

    try {
        val uuid = UUID.fromString(uuidString)
        val body = call.receive<UnlockEIORequest>()
        when (service.unlock(uuid, body.lock)) {
            is UnlockResult.Success -> call.respond(HttpStatusCode.NoContent)
            is UnlockResult.InvalidLock -> call.respondProblem(
                HttpStatusCode.Conflict,
                conflict("Invalid lock token for unlock", call.request.path()),
            )

            is UnlockResult.NotLocked -> call.respondProblem(
                HttpStatusCode.Conflict,
                conflict("EnkelvoudigInformatieObject is not locked", call.request.path()),
            )

            null -> call.respondProblem(

                HttpStatusCode.NotFound,

                notFound("EnkelvoudigInformatieObject not found", call.request.path()),
            )
        }
    } catch (_: IllegalArgumentException) {
        call.respondProblem(HttpStatusCode.BadRequest, badRequest("Invalid UUID format", call.request.path()))
    }
}
