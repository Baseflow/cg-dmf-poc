// SPDX-License-Identifier: EUPL-1.2
// Copyright (C) 2026 Gemeente Utrecht
package com.baseflow.api.wopi

import io.ktor.server.application.createRouteScopedPlugin
import io.ktor.server.plugins.BadRequestException
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
            ?: throw BadRequestException("Missing file_id path parameter.")

        val uuid = try {
            UUID.fromString(raw)
        } catch (_: IllegalArgumentException) {
            throw BadRequestException("Invalid file_id path parameter. Expected a UUID.")
        }

        call.attributes.put(WopiValidatedFileIdKey, uuid)
    }
}
