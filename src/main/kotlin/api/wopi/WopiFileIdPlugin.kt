// SPDX-License-Identifier: EUPL-1.2
// Copyright (C) 2026 Gemeente Utrecht
package com.baseflow.api.wopi

import com.baseflow.api.models.ProblemDetailsResponse
import com.baseflow.api.models.respondProblem
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.createRouteScopedPlugin
import io.ktor.server.request.path
import java.util.UUID

/**
 * Route-scoped Ktor plugin that parses the `file_id` path parameter as a [UUID]
 * and stores it in [WopiValidatedFileIdKey].
 *
 * This can be installed on any route that has a `{file_id}` path segment,
 * including unauthenticated routes like `POST /token/{file_id}`.
 */
val WopiFileIdPlugin = createRouteScopedPlugin(name = "WopiFileId") {
    onCall { call ->
        val raw = call.parameters["file_id"]
        if (raw == null) {
            call.respondProblem(
                HttpStatusCode.BadRequest,
                ProblemDetailsResponse(
                    title = "Bad Request",
                    status = HttpStatusCode.BadRequest.value,
                    detail = "Missing file_id path parameter.",
                    instance = call.request.path(),
                ),
            )
            return@onCall
        }

        val uuid = try {
            UUID.fromString(raw)
        } catch (_: IllegalArgumentException) {
            call.respondProblem(
                HttpStatusCode.BadRequest,
                ProblemDetailsResponse(
                    title = "Bad Request",
                    status = HttpStatusCode.BadRequest.value,
                    detail = "Invalid file_id path parameter. Expected a UUID.",
                    instance = call.request.path(),
                ),
            )
            return@onCall
        }

        call.attributes.put(WopiValidatedFileIdKey, uuid)
    }
}
