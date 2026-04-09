// SPDX-License-Identifier: EUPL-1.2
// Copyright (C) 2025-2026 Gemeente Utrecht
package com.baseflow.api.routes

import com.baseflow.api.middleware.RequestScopeKey
import com.baseflow.services.BestandsDeelService
import com.baseflow.services.MarkVoltooidResult
import io.ktor.http.*
import io.ktor.http.content.PartData
import io.ktor.http.content.forEachPart
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.routing.openapi.describe
import io.ktor.utils.io.ExperimentalKtorApi
import java.util.UUID

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
     * Upload een bestandsdeel.
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

        // Parse multipart body to extract lock token.
        // The 'inhoud' file part is consumed and discarded here; in a production
        // implementation it would be streamed directly to the storage backend.
        var lockToken: String? = null

        call.receiveMultipart().forEachPart { part ->
            if (part is PartData.FormItem && part.name == "lock") {
                lockToken = part.value
            }
            part.dispose()
        }

        if (lockToken.isNullOrBlank()) {
            return@put call.respond(
                HttpStatusCode.BadRequest,
                mapOf("detail" to "lock is required"),
            )
        }

        val service: BestandsDeelService =
            call.attributes.getOrNull(RequestScopeKey)?.get()
                ?: return@put call.respond(
                    HttpStatusCode.InternalServerError,
                    mapOf("detail" to "Service not available"),
                )

        when (val result = service.markVoltooid(id, requireNotNull(lockToken))) {
            is MarkVoltooidResult.NotFound ->
                call.respond(HttpStatusCode.NotFound, mapOf("detail" to "BestandsDeel niet gevonden"))

            is MarkVoltooidResult.InvalidLock ->
                call.respond(HttpStatusCode.Forbidden, mapOf("detail" to "Ongeldige lock token"))

            is MarkVoltooidResult.Success ->
                call.respond(HttpStatusCode.OK, result.response)
        }
    }
        .describe {
            operationId = "bestandsdelen_update"
            tag("bestandsdelen")
            summary = "Upload een bestandsdeel."
            description =
                "Upload een bestandsdeel als onderdeel van de chunked upload workflow voor grote bestanden. " +
                "De request body is multipart/form-data met velden 'inhoud' (binary) en 'lock' (string). " +
                "Wanneer alle delen zijn geupload kan het bovenliggende informatieobject worden ontgrendeld."
            parameters {
                path("uuid") { description = "Unieke resource identifier (UUID4) van het bestandsdeel." }
            }
            responses {
                response(200) { description = "OK – bestandsdeel bijgewerkt." }
                response(400) { description = "Bad request." }
                response(401) { description = "Unauthorized." }
                response(403) { description = "Forbidden – ongeldige lock token." }
                response(404) { description = "Not found." }
            }
        }
}
