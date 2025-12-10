// SPDX-License-Identifier: EUPL-1.2
// Copyright (C) 2025 Gemeente Utrecht

package com.baseflow.api.middleware

import com.baseflow.api.DOCUMENTEN_API_VERSION
import io.ktor.server.application.*

/**
 * Route-scoped plugin to append API-version header to all responses within the
 * Documenten API route subtree. Kept in a dedicated file for separation of concerns.
 */
class ApiVersionHeaderConfig { var version: String = DOCUMENTEN_API_VERSION }

val ApiVersionHeader = createRouteScopedPlugin(
    name = "ApiVersionHeader",
    createConfiguration = ::ApiVersionHeaderConfig
) {
    val version = pluginConfig.version
    onCallRespond { call, _ ->
        call.response.headers.append("API-version", version)
    }
}
