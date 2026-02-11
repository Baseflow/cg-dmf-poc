// SPDX-License-Identifier: EUPL-1.2
// Copyright (C) 2025-2026 Gemeente Utrecht
package com.baseflow.api.routes

import com.baseflow.entities.EIORecordEntity
import com.baseflow.api.ApiUrlBuilder
import com.baseflow.api.DOCUMENTEN_API_VERSION
import com.baseflow.api.middleware.ApiVersionHeader
import com.baseflow.api.middleware.AuditTrailPlugin
import com.baseflow.api.middleware.auditContext
import com.baseflow.api.models.*
import com.baseflow.config.ApplicationConfig
import com.baseflow.config.OpenZaakConfig
import com.baseflow.services.EnkelvoudigInformatieObjectService
import com.baseflow.services.OpenZaakService
import com.baseflow.services.StorageService
import com.baseflow.services.models.DeleteResult
import com.baseflow.services.models.LockResult
import com.baseflow.services.models.QueryEnkelvoudigeInformatieObjectenFilter
import com.baseflow.services.models.UnlockResult
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.util.*

/**
 * Routes for EnkelvoudigInformatieObjecten (Single Information Objects).
 */

const val RESOURCE_SEGMENT = "enkelvoudiginformatieobjecten"

fun Route.enkelvoudigInformatieObjectenRoutes(openZaakConfig: OpenZaakConfig = OpenZaakConfig.fromEnv()) {
    // Ensure API-version header is added for all responses under this subtree,
    // including tests that don't install the plugin at the parent route.
    install(ApiVersionHeader) { version = DOCUMENTEN_API_VERSION }
    install(AuditTrailPlugin)

    val openZaakService = OpenZaakService(openZaakConfig)

    fun getService(call: RoutingCall) = EnkelvoudigInformatieObjectService(StorageService(), ApplicationConfig, openZaakService, call.auditContext())

    // List all documents (with optional filters)
    get { list(this.call, getService(this.call)) }

    // Create new document
    post { create(this.call, getService(this.call)) }

    // Advanced search endpoint
    post("/_zoek") { zoek(this.call, getService(this.call)) }

    // Single document operations
    route("/{uuid}") {
        // HEAD - existence check
        head { head(this.call, getService(this.call)) }
        // Get single document
        get { get(this.call, getService(this.call)) }

        // Update document (full)
        put { put(this.call, getService(this.call)) }

        // Partial update
        patch { patch(this.call, getService(this.call)) }

        // Delete document
        delete { delete(this.call, getService(this.call)) }

        // Download document content (streamed from storage)
        get("/download") { download(this.call, getService(this.call)) }

        // Lock document for editing
        post("/lock") { lock(this.call, getService(this.call)) }

        // Unlock document
        post("/unlock") { unlock(this.call, getService(this.call)) }
    }
}

private suspend fun list(call: RoutingCall, service: EnkelvoudigInformatieObjectService) {
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
        expand = expand,
        page = page,
        pageSize = pageSize,
        objectUrl = objectUrl,
        objectType = objectType
    )

    val (items, totalCount) = service.getAll(filter)
    call.respond(PaginatedResponse.from(call, RESOURCE_SEGMENT, items, totalCount, page, pageSize))
}

private suspend fun create(call: RoutingCall, service: EnkelvoudigInformatieObjectService) {
    val request = call.receive<EnkelvoudigInformatieObjectRequest>()
    try {
        val response = service.create(request)
        // Location header with the URL of the created resource
        val locationUrl = ApiUrlBuilder.absolute(RESOURCE_SEGMENT, response.id)
        call.response.headers.append(HttpHeaders.Location, locationUrl)

        call.respond(HttpStatusCode.Created, response)
    } catch (e: IllegalArgumentException) {
        call.respondProblem(HttpStatusCode.BadRequest, badRequest(e.message ?: "Validation failed", call.request.path()))
        return
    }
}

private suspend fun zoek(call: RoutingCall, service: EnkelvoudigInformatieObjectService) {
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
        expand = expand,
        page = page,
        pageSize = pageSize,
        objectUrl = objectUrl,
        objectType = objectType
    )

    val (items, totalCount) = service.getAll(filter)
    val response = PaginatedResponse.from(call, RESOURCE_SEGMENT, items, totalCount, page, pageSize)

    call.respond(response)
}

private suspend fun head(call: RoutingCall, service: EnkelvoudigInformatieObjectService) {
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
            call.respondProblem(HttpStatusCode.NotFound, notFound("EnkelvoudigInformatieObject not found", call.request.path()))
        }
    } catch (_: IllegalArgumentException) {
        call.respondProblem(HttpStatusCode.BadRequest, badRequest("Invalid UUID format", call.request.path()))
    }
}

private suspend fun get(call: RoutingCall, service: EnkelvoudigInformatieObjectService) {
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
            call.respondProblem(HttpStatusCode.NotFound, notFound("EnkelvoudigInformatieObject not found", call.request.path()))
        } else {
            call.respond(HttpStatusCode.OK, result)
        }
    } catch (_: IllegalArgumentException) {
        call.respondProblem(HttpStatusCode.BadRequest, badRequest("Invalid UUID format", call.request.path()))
    }
}

private suspend fun put(call: RoutingCall, service: EnkelvoudigInformatieObjectService) {
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
            call.respondProblem(HttpStatusCode.NotFound, badRequest("EnkelvoudigInformatieObject not found", call.request.path()))
            return
        }
        call.respond(HttpStatusCode.OK, response)
    } catch (e: IllegalArgumentException) {
        call.respondProblem(HttpStatusCode.BadRequest, badRequest(e.message ?: "Invalid UUID format", call.request.path()))
        return
    }
}

private suspend fun patch(call: RoutingCall, service: EnkelvoudigInformatieObjectService) {
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
            call.respondProblem(HttpStatusCode.NotFound, badRequest("EnkelvoudigInformatieObject not found", call.request.path()))
            return
        }
        call.respond(HttpStatusCode.OK, response)
    } catch (e: IllegalArgumentException) {
        call.respondProblem(HttpStatusCode.BadRequest, badRequest(e.message ?: "Invalid UUID format", call.request.path()))
    }

}

private suspend fun delete(call: RoutingCall, service: EnkelvoudigInformatieObjectService) {
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

private suspend fun download(call: RoutingCall, service: EnkelvoudigInformatieObjectService) {
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
                notFound("EnkelvoudigInformatieObject not found", call.request.path())
            )
            return
        }

        // Ensure we have a stored object key to stream
        val objectKey = eio.bestandsLocatie
        if (objectKey.isBlank()) {
            call.respondProblem(
                HttpStatusCode.NotFound,
                notFound("Document content not available for download", call.request.path())
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

private suspend fun lock(call: RoutingCall, service: EnkelvoudigInformatieObjectService) {
    val uuidString = call.parameters["uuid"]
    if (uuidString == null) {
        call.respondProblem(HttpStatusCode.BadRequest, badRequest("UUID parameter is required", call.request.path()))
        return
    }

    try {
        val uuid = UUID.fromString(uuidString)
        when (val result = service.lock(uuid)) {
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

private suspend fun unlock(call: RoutingCall, service: EnkelvoudigInformatieObjectService) {
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
