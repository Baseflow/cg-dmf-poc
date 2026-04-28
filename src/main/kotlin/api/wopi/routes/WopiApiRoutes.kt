// SPDX-License-Identifier: EUPL-1.2
// Copyright (C) 2026 Gemeente Utrecht
package com.baseflow.api.wopi.routes

import com.baseflow.api.WOPI_API_BASE_PATH
import com.baseflow.api.middleware.*
import com.baseflow.api.middleware.RequestScopeKey
import com.baseflow.api.models.badRequest
import com.baseflow.api.models.notFound
import com.baseflow.api.models.respondProblem
import com.baseflow.api.wopi.models.CheckFileInfoResponse
import com.baseflow.entities.EIORecordEntity
import com.baseflow.services.EnkelvoudigInformatieObjectService
import io.ktor.http.ContentDisposition
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.path
import io.ktor.server.request.receiveChannel
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.routing.openapi.hide
import io.ktor.utils.io.ExperimentalKtorApi
import io.ktor.utils.io.toByteArray
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.util.UUID

@OptIn(ExperimentalKtorApi::class)
fun Route.wopiApiRoutes() {
    install(AuditTrailPlugin)

    route(WOPI_API_BASE_PATH) {
        get("/files/{file_id}") {
            getFileMetadata()
        }.hide()

        get("/files/{file_id}/contents") {
            getFileContents()
        }.hide()

        post("/files/{file_id}/contents") {
            updateFileContents()
        }
    }
}

private suspend fun RoutingContext.updateFileContents() {
    val fileId = call.parameters["file_id"]
    if (fileId == null) {
        call.respondProblem(HttpStatusCode.BadRequest, badRequest("UUID parameter is required", call.request.path()))
        return
    }

    try {
        val uuid = UUID.fromString(fileId)
        val bytes = call.receiveChannel().toByteArray()
        val response = service.updateWithBytes(id = uuid, bytes = bytes)
        // TODO(elitsa): improve problem response
        if (response == null) {
            call.respondProblem(
                HttpStatusCode.NotFound,
                notFound("Er is iets misgegaan met de update.", call.request.path()),
            )
            return
        }
        call.respond(HttpStatusCode.OK)
    } catch (e: IllegalArgumentException) {
        call.respondProblem(
            HttpStatusCode.BadRequest,
            badRequest(e.message ?: "Invalid UUID format", call.request.path()),
        )
    }
}

private suspend fun RoutingContext.getFileContents() {
    val fileId = call.parameters["file_id"]
    if (fileId == null) {
        call.respondProblem(
            HttpStatusCode.BadRequest,
            badRequest("file_id parameter is required", call.request.path()),
        )
        return
    }

    try {
        val uuid = UUID.fromString(fileId)

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

        // Use internal storage path for lookup, but public filename for the response header
        val objectKey = eio.bestandsLocatie
        if (objectKey.isBlank()) {
            call.respondProblem(
                HttpStatusCode.NotFound,
                notFound("Document content not available for download", call.request.path()),
            )
            return
        }

        // Derive filename and content type when possible;
        val fileName = eio.bestandsnaam.ifBlank { null } ?: eio.titel.ifBlank { null } ?: "document-${eio.id}"
        val contentType = try {
            // eio.formaat is expected to be a MIME type; if not, fallback below
            eio.formaat?.let { ContentType.parse(it) }
        } catch (_: Exception) {
            ContentType.Application.OctetStream
        } ?: ContentType.Application.OctetStream

        // Set headers before starting the stream
        call.response.headers.append(
            HttpHeaders.ContentDisposition,
            ContentDisposition.Attachment
                .withParameter(ContentDisposition.Parameters.FileNameAsterisk, fileName, true)
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

private suspend fun RoutingContext.getFileMetadata() {
    val fileId = call.parameters["file_id"]
    if (fileId == null) {
        call.respondProblem(HttpStatusCode.BadRequest, badRequest("UUID parameter is required", call.request.path()))
        return
    }

    try {
        val uuid = UUID.fromString(fileId)
        val result = service.getById(uuid, emptyList())

        if (result == null) {
            call.respondProblem(
                HttpStatusCode.NotFound,
                notFound("EnkelvoudigInformatieObject not found", call.request.path()),
            )
        } else if (result.bestandsomvang == null) {
            call.respondProblem(
                HttpStatusCode.NotFound,
                notFound("Document content not available", call.request.path()),
            )
        } else {
            val checkFileInfoResponse = CheckFileInfoResponse(
                baseFileName = result.bestandsnaam?.ifBlank { null } ?: result.titel.ifBlank { null } ?: "document",
                size = result.bestandsomvang,
                userCanWrite = true,
                supportsAutosave = false,
            )
            call.respond(
                HttpStatusCode.OK,
                checkFileInfoResponse,
            )
        }
    } catch (_: IllegalArgumentException) {
        call.respondProblem(HttpStatusCode.BadRequest, badRequest("Invalid UUID format", call.request.path()))
    }
}

private val RoutingContext.service: EnkelvoudigInformatieObjectService
    get() = call.attributes[RequestScopeKey].get()
