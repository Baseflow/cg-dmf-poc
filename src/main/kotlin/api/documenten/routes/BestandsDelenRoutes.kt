// SPDX-License-Identifier: EUPL-1.2
// Copyright (C) 2025-2026 Gemeente Utrecht
package com.baseflow.api.documenten.routes

import com.baseflow.api.models.BestandsDeelResponse
import com.baseflow.services.BestandsDeelService
import com.baseflow.services.StorageService
import com.baseflow.services.UploadFilePartResult
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.openapi.jsonSchema
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.routing.openapi.*
import io.ktor.utils.io.*
import io.ktor.utils.io.streams.inputStream
import java.io.InputStream
import org.koin.ktor.plugin.scope
import java.util.*

/**
 * BestandsDeel routes
 *
 * Handles chunked file uploads for large documents (>4GB threshold):
 * - PUT /{uuid} - Upload a file chunk
 *
 * Part of the 1.1.0+ workflow for handling files larger than the configured threshold.
 * The `bestandsdelen` array is returned in the EIO create response when the declared
 * `bestandsomvang` exceeds the trigger size.
 */
@OptIn(ExperimentalKtorApi::class)
fun Route.bestandsDelenRoutes() {
    /**
     * Upload een BESTANDSDEEL.
     *
     * Accepts the binary content of one chunk together with the lock token.
     * The request body must be multipart/form-data with fields:
     *   - `inhoud`  – binary file content of the chunk
     *   - `lock`    – lock token obtained when the EIO was created / locked
     *
     * When all parts have been successfully uploaded (voltooid == true), the API
     * consumer should unlock the parent EIO to finalise the document.
     *
     * Responses:
     *   - 200 OK – returns the updated BestandsDeel.
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
     * @tag BestandsDelen
     */
    put("/{uuid}") {
        val service: BestandsDeelService = call.scope.get<BestandsDeelService>()
        val storageService: StorageService = call.scope.get<StorageService>()

        val uuid =
            call.parameters["uuid"]
                ?: return@put call.respond(HttpStatusCode.BadRequest, mapOf("detail" to "uuid is required"))

        val id =
            try {
                UUID.fromString(uuid)
            } catch (_: IllegalArgumentException) {
                return@put call.respond(
                    HttpStatusCode.BadRequest,
                    mapOf("detail" to "uuid is not a valid UUID"),
                )
            }

        // Parse multipart body to extract lock token and file content.
        // The 'inhoud' binary part is read into memory and forwarded to the storage backend.
        var lockToken: String? = null
        var inputStream: InputStream? = null

        call.receiveMultipart().forEachPart { part ->
            when {
                part is PartData.FormItem && part.name == "lock" -> lockToken = part.value
                part is PartData.FileItem && part.name == "inhoud" ->
                    inputStream = part.provider().readRemaining().inputStream()
            }
            part.release()
            part.release()
        }

        if (lockToken.isNullOrBlank()) {
            return@put call.respond(
                HttpStatusCode.BadRequest,
                mapOf("detail" to "lock is required"),
            )
        }

        when (val result = service.uploadFilePart(id, requireNotNull(lockToken), inputStream, storageService)) {
            is UploadFilePartResult.NotFound ->
                call.respond(HttpStatusCode.NotFound, mapOf("detail" to "BestandsDeel niet gevonden"))

            is UploadFilePartResult.InvalidLock ->
                call.respond(HttpStatusCode.Forbidden, mapOf("detail" to "Ongeldige lock token"))

            is UploadFilePartResult.OmvangMismatch ->
                call.respond(
                    HttpStatusCode.BadRequest,
                    mapOf(
                        "detail" to "Bestandsomvang komt niet overeen: verwacht ${result.expected} bytes, ontvangen ${result.actual} bytes",
                    ),
                )

            is UploadFilePartResult.Success ->
                call.respond(HttpStatusCode.OK, result.response)
        }
    }
        .describe {
            operationId = "bestandsdelen_update"
            tag("bestandsdelen")
            summary = "Upload een BESTANDSDEEL."
            description =
                "Upload een BESTANDSDEEL als onderdeel van de chunked upload workflow voor grote bestanden. " +
                "De request body is multipart/form-data met velden 'inhoud' (binary) en 'lock' (string). " +
                "Wanneer alle delen zijn geupload kan het bovenliggende INFORMATIEOBJECT worden ontgrendeld."
            parameters {
                path("uuid") { description = "Unieke resource identifier (UUID4) van het BESTANDSDEEL." }
            }
            responses {
                response(200) {
                    description = "OK – BESTANDSDEEL bijgewerkt."
                    ContentType.Application.Json { schema = jsonSchema<BestandsDeelResponse>() }
                }
                response(400) { description = "Bad request." }
                response(401) { description = "Unauthorized." }
                response(403) { description = "Forbidden – ongeldige lock token." }
                response(404) { description = "Not found." }
            }
        }
}
