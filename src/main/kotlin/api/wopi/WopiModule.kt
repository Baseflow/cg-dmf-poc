// SPDX-License-Identifier: EUPL-1.2
// Copyright (C) 2026 Gemeente Utrecht
package com.baseflow.api.wopi

import com.baseflow.api.wopi.routes.wopiApiRoutes
import io.ktor.server.application.Application
import io.ktor.server.routing.routing

/**
 * WOPI_ENABLED env variable controls whether the WOPI routes are registered at all.
 * Allows for unauthenticated access to the WOPI API. Should be used for development and testing only.
 */
val wopiEnabled: Boolean
    get() = System.getenv("WOPI_ENABLED")?.lowercase() == "true"

fun Application.wopiApiModule() {
    if (!wopiEnabled) {
        return
    }
    routing {
        wopiApiRoutes()
    }
}
