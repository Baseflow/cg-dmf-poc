// SPDX-License-Identifier: EUPL-1.2
// Copyright (C) 2026 Gemeente Utrecht
package com.baseflow.api.admin

import com.baseflow.api.admin.routes.blobStorageRepositoryRoutes
import io.ktor.server.application.Application
import io.ktor.server.auth.AuthenticationStrategy
import io.ktor.server.auth.authenticate
import io.ktor.server.routing.Route
import io.ktor.server.routing.route
import io.ktor.server.routing.routing

/**
 * Admin API Module
 *
 * Provides internal management endpoints (not part of the public Documenten API).
 *
 * Endpoints:
 * - /admin/storage-repositories — manage blob storage repositories
 */
fun Route.adminRoutes() {
    route("/admin") {
        blobStorageRepositoryRoutes()
    }
}

fun Application.adminModule(useAuthentication: Boolean = true) {
    routing {
        if (useAuthentication) {
            // TODO: do we really want auth-zgw enabled on admin routes ?
            // For now enable for integration tests, but we should find another way.
            authenticate("auth-jwt", "auth-zgw", "auth-zgw", strategy = AuthenticationStrategy.FirstSuccessful) {
                adminRoutes()
            }
        } else {
            adminRoutes()
        }
    }
}