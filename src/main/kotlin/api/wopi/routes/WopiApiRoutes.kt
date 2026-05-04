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
import io.ktor.openapi.jsonSchema
import io.ktor.server.request.path
import io.ktor.server.request.receiveChannel
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.routing.openapi.describe
import io.ktor.utils.io.ExperimentalKtorApi
import io.ktor.utils.io.toByteArray
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.util.UUID

@OptIn(ExperimentalKtorApi::class)
fun Route.wopiApiRoutes() {
    install(AuditTrailPlugin)

    route(WOPI_API_BASE_PATH) {
        route("/files/{file_id}") {
            get { getFileMetadata() }.describe {
                operationId = "getFileMetadata"
                tag("wopi")
                summary = "Get a file metadata."
                description =
                    "Gets the metadata of a file."
                parameters {
                    path("file_id") {
                        description = "The UUID of the file to retrieve metadata for."
                        required = true
                    }
                }
                responses {
                    response(200) {
                        description = "The metadata of a file"
                        ContentType.Application.Json { schema = jsonSchema<CheckFileInfoResponse>() }
                    }
                    response(400) { description = "Bad request." }
                    response(401) { description = "Unauthorized." }
                    response(403) { description = "Forbidden." }
                    response(404) { description = "Not found." }
                    response(500) { description = "Internal server error." }
                }
            }

            route("/contents") {
                get { getFileContents() }.describe {
                    operationId = "getFileContents"
                    tag("wopi")
                    summary = "Get file contents."
                    description =
                        "Gets the contents of a file."
                    parameters {
                        path("file_id") {
                            description = "The UUID of the file to retrieve the contents for."
                            required = true
                        }
                    }
                    responses {
                        response(200) {
                            description = "The binary data contents of a file"
                        }
                        response(400) { description = "Bad request." }
                        response(401) { description = "Unauthorized." }
                        response(403) { description = "Forbidden." }
                        response(404) { description = "Not found." }
                        response(500) { description = "Internal server error." }
                    }
                }

                post { updateFileContents() }.describe {
                    operationId = "updateFileContents"
                    tag("wopi")
                    summary = "Update (Save) file contents."
                    description =
                        "Saves the contents of a file to the host."
                    parameters {
                        path("file_id") {
                            description = "The UUID of the file to save the contents for."
                            required = true
                        }
                    }
                    responses {
                        response(200) {
                            description = "File successfully saved."
                        }
                        response(400) { description = "Bad request." }
                        response(401) { description = "Unauthorized." }
                        response(403) { description = "Forbidden." }
                        response(404) { description = "Not found." }
                        response(500) { description = "Internal server error." }
                    }
                }
            }
        }
    }
}

private suspend fun RoutingContext.updateFileContents() {
    val wopiOverride = call.request.headers["X-WOPI-Override"]
    if (wopiOverride != "PUT") {
        call.respondProblem(
            HttpStatusCode.BadRequest,
            badRequest("X-WOPI-Override header must be PUT", call.request.path()),
        )
        return
    }

    val fileId = call.parameters["file_id"]
    if (fileId == null) {
        call.respondProblem(HttpStatusCode.BadRequest, badRequest("UUID parameter is required", call.request.path()))
        return
    }

    try {
        val uuid = UUID.fromString(fileId)
        val bytes = call.receiveChannel().toByteArray()
        val response = service.updateWithBytes(id = uuid, bytes = bytes)
        if (response == null) {
            call.respondProblem(
                HttpStatusCode.NotFound,
                notFound("Enkelvoudigobject not found", call.request.path()),
            )
            return
        }
        // This is a Collabora-specific response object, not part of the WOPI protocol.
        // Important for supporting collaboration features.
        val lastModified = response.beginRegistratie
        call.response.headers.append("X-WOPI-ItemVersion", response.versie.toString())
        call.respond(HttpStatusCode.OK, mapOf("LastModifiedTime" to lastModified))
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
            service.streamByBestandsnaam(bestandsnaam = objectKey, output = this, repoName = eio.bestandsRepository)
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
                userFriendlyName = "Unknown user",
                supportsLocks = false,
                supportsGetLock = false,
                supportsUpdate = true,
                lastModifiedTime = result.beginRegistratie,
                version = result.versie.toString(),
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
