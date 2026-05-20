// SPDX-License-Identifier: EUPL-1.2
// Copyright (C) 2026 Gemeente Utrecht
package com.baseflow.api.wopi

import com.baseflow.api.models.ProblemDetailsResponse
import com.baseflow.api.models.respondProblem
import com.baseflow.config.WopiConfig
import com.baseflow.services.WopiSlatService
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.createRouteScopedPlugin
import io.ktor.server.request.path
import org.koin.core.context.GlobalContext
import org.koin.core.parameter.parametersOf
import java.util.UUID

/**
 * Route-scoped Ktor plugin that validates a WOPI Short-Lived Access Token (SLAT).
 *
 * The token is read from the `access_token` query parameter (WOPI spec convention).
 * The validated file UUID is stored in [WopiValidatedFileIdKey] so route handlers can retrieve it.
 */
val WopiSlatAuthPlugin = createRouteScopedPlugin(
    name = "WopiSlatAuth",
) {
    val slatService: WopiSlatService = GlobalContext.get().get<WopiSlatService> {
        parametersOf(
            WopiConfig.slatSecret,
            WopiConfig.slatTtlSeconds,
        )
    }

    onCall { call ->
        val token = call.request.queryParameters["access_token"]
        if (token == null) {
            call.respondProblem(
                HttpStatusCode.Unauthorized,
                ProblemDetailsResponse(
                    title = "Unauthorized",
                    status = HttpStatusCode.Unauthorized.value,
                    detail = "Missing access_token query parameter.",
                    instance = call.request.path(),
                ),
            )
            return@onCall
        }

        val fileId = slatService.validate(token)
        if (fileId == null) {
            call.respondProblem(
                HttpStatusCode.Unauthorized,
                ProblemDetailsResponse(
                    title = "Unauthorized",
                    status = HttpStatusCode.Unauthorized.value,
                    detail = "Invalid or expired access_token.",
                    instance = call.request.path(),
                ),
            )
            return@onCall
        }

        // Verify the token was issued for the file_id in the URL path.
        // This prevents using a valid token for one file to access a different file.
        // WopiFileIdPlugin (installed on the same route) has already parsed and stored the UUID.
        val pathFileId = call.attributes.getOrNull(WopiValidatedFileIdKey)
        if (pathFileId != null && pathFileId != fileId) {
            call.respondProblem(
                HttpStatusCode.Unauthorized,
                ProblemDetailsResponse(
                    title = "Unauthorized",
                    status = HttpStatusCode.Unauthorized.value,
                    detail = "access_token was not issued for this file.",
                    instance = call.request.path(),
                ),
            )
            return@onCall
        }

        // Store the validated UUID so route handlers can use it without re-parsing path params
        call.attributes.put(WopiValidatedFileIdKey, fileId)
    }
}

/** Attribute key to retrieve the validated WOPI file UUID inside a route handler. */
val WopiValidatedFileIdKey = io.ktor.util.AttributeKey<UUID>("WopiValidatedFileId")
