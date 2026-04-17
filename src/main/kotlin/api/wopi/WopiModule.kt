// SPDX-License-Identifier: EUPL-1.2
// Copyright (C) 2026 Gemeente Utrecht
package com.baseflow.api.wopi

import com.baseflow.api.wopi.routes.wopiApiRoutes
import io.ktor.server.application.Application
import io.ktor.server.auth.AuthenticationStrategy
import io.ktor.server.auth.authenticate
import io.ktor.server.routing.routing

fun Application.wopiApiModule(useAuthentication: Boolean = false) {
    routing {
        if (useAuthentication) {
            authenticate("auth-jwt", "auth-zgw", strategy = AuthenticationStrategy.FirstSuccessful) {
                wopiApiRoutes()
            }
        } else {
            wopiApiRoutes()
        }
    }
}
