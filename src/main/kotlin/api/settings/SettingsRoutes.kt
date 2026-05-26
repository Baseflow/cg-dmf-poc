// SPDX-License-Identifier: EUPL-1.2
// Copyright (C) 2026 Gemeente Utrecht
package com.baseflow.api.settings

import com.baseflow.api.settings.routes.applicationSettingsRoutes
import com.baseflow.api.settings.routes.blobStorageRepositorySettingsRoutes
import com.baseflow.api.settings.routes.dmfSettingsRoutes
import com.baseflow.api.settings.routes.oidcProviderSettingsRoutes
import com.baseflow.api.settings.routes.zgwApiSettingsRoutes
import io.ktor.server.application.Application
import io.ktor.server.auth.AuthenticationStrategy
import io.ktor.server.auth.authenticate
import io.ktor.server.routing.Route
import io.ktor.server.routing.route
import io.ktor.server.routing.routing

/**
 * Settings API Module
 *
 * Provides internal management endpoints (not part of the public Documenten API).
 *
 * Endpoints:
 * - /settings/application-settings — manage application credential configurations
 * - /settings/storage-repositories — manage blob storage repositories
 * - /settings/oidc-providers — manage OIDC provider configurations
 * - /settings/dmf-settings — manage DMF settings
 * - /settings/zgw-api-settings — manage ZGW API settings
 */
fun Route.settingsRoutes() {
    route("/settings") {
        applicationSettingsRoutes()
        dmfSettingsRoutes()
        zgwApiSettingsRoutes()
        blobStorageRepositorySettingsRoutes()
        oidcProviderSettingsRoutes()
    }
}

fun Application.settingsModule(useAuthentication: Boolean = true) {
    routing {
        if (useAuthentication) {
            // TODO: do we really want auth-zgw enabled on settings routes ?
            // For now enable for integration tests, but we should find another way.
            authenticate("auth-jwt", "auth-zgw", strategy = AuthenticationStrategy.FirstSuccessful) {
                settingsRoutes()
            }
        } else {
            settingsRoutes()
        }
    }
}
