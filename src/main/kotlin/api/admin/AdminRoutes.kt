// SPDX-License-Identifier: EUPL-1.2
// Copyright (C) 2026 Gemeente Utrecht
package com.baseflow.api.admin

import com.baseflow.api.admin.routes.blobStorageRepositoryRoutes
import com.baseflow.api.admin.routes.dmfSettingsRoutes
import com.baseflow.api.admin.routes.oidcProvidersRoutes
import com.baseflow.api.admin.routes.zgwApiSettingsRoutes
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
 * - /admin/oidc-providers — manage OIDC provider configurations
 */
fun Route.adminRoutes() {
    route("/admin") {
        dmfSettingsRoutes()
        zgwApiSettingsRoutes()
        blobStorageRepositoryRoutes()
        oidcProvidersRoutes()
    }
}

fun Application.adminModule(useAuthentication: Boolean = true) {
    routing {
        if (useAuthentication) {
            authenticate("auth-jwt", strategy = AuthenticationStrategy.FirstSuccessful) {
                adminRoutes()
            }
        } else {
            adminRoutes()
        }
    }
}
