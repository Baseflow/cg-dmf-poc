// SPDX-License-Identifier: EUPL-1.2
// Copyright (C) 2026 Gemeente Utrecht
package com.baseflow.wopi.api

import com.baseflow.shared.api.middleware.UnauthorizedException
import com.baseflow.shared.config.WopiConfig
import com.baseflow.shared.services.WopiSlatService
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.createRouteScopedPlugin
import io.ktor.util.AttributeKey
import org.koin.core.parameter.parametersOf
import org.koin.ktor.plugin.scope
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
    onCall { call ->
        val slatService: WopiSlatService = getWopiSlatService(call)

        val token = call.request.queryParameters["access_token"]
            ?: throw UnauthorizedException("Missing access_token query parameter.")

        val fileId = slatService.validate(token)
            ?: throw UnauthorizedException("Invalid or expired access_token.")

        // Verify the token was issued for the file_id in the URL path.
        // This prevents using a valid token for one file to access a different file.
        // WopiFileIdPlugin (installed on the same route) has already parsed and stored the UUID.
        val pathFileId = call.attributes.getOrNull(WopiValidatedFileIdKey)
        if (pathFileId != null && pathFileId != fileId) {
            throw UnauthorizedException("access_token was not issued for this file.")
        }

        // Store the validated UUID so route handlers can use it without re-parsing path params
        call.attributes.put(WopiValidatedFileIdKey, fileId)
    }
}

private fun getWopiSlatService(call: ApplicationCall): WopiSlatService = call.scope.get<WopiSlatService> {
    val config = call.scope.get<WopiConfig>()
    parametersOf(
        config.slatSecret,
        config.slatTtlSeconds,
    )
}

/** Attribute key to retrieve the validated WOPI file UUID inside a route handler. */
val WopiValidatedFileIdKey = AttributeKey<UUID>("WopiValidatedFileId")
