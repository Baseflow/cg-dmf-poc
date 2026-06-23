// SPDX-License-Identifier: EUPL-1.2
// Copyright (C) 2025-2026 Gemeente Utrecht
package com.baseflow.documenten.api

import com.baseflow.documenten.api.routes.documentenApiRoutes
import com.baseflow.shared.api.middleware.configureStatusPages
import io.ktor.server.application.Application
import io.ktor.server.auth.AuthenticationStrategy
import io.ktor.server.auth.authenticate
import io.ktor.server.routing.routing

fun Application.documentenApiModule(useAuthentication: Boolean = true) {
    // Configure StatusPages for global exception handling
    configureStatusPages()

    routing {
        if (useAuthentication) {
            authenticate("auth-zgw", "auth-jwt", strategy = AuthenticationStrategy.FirstSuccessful) {
                documentenApiRoutes()
            }
        } else {
            documentenApiRoutes()
        }
    }
}
