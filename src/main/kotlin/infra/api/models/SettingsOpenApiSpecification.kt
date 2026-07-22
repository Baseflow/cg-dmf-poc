// SPDX-License-Identifier: EUPL-1.2
// Copyright (C) 2026 Gemeente Utrecht
package com.baseflow.infra.api.models

import com.baseflow.shared.api.SETTINGS_API_BASE_PATH
import io.ktor.openapi.OpenApiInfo
import io.ktor.openapi.SecurityRequirement
import io.ktor.openapi.Tag

internal class SettingsOpenApiSpecification : OpenApiSpecification {
    override val name: String get() = "Settings"

    override val basePath: String get() = SETTINGS_API_BASE_PATH

    override val apiInfo: OpenApiInfo get() = OpenApiInfo(
        title = "DMF Settings API",
        version = "1.0.0",
        description = """
        Interne beheer-API voor het configureren van de DMF-applicatie op runtime.

        Alle endpoints vereisen de `dmf-admin` rol. Authenticatie via OIDC (Keycloak) of
        een ZGW Bearer token is verplicht.

        **Beschikbare resource-groepen**

        - `/settings/application-settings` — Applicatie-credentials voor ZGW-authenticatie (clientId/secret paren)
        - `/settings/storage-repositories` — Blob storage backends (S3 / Azure Blob Storage)
        - `/settings/dmf-settings` — Generieke key/value instellingen (chunked upload drempelwaarden, etc.)
        - `/settings/api-connection-settings` — Verbindingsinstellingen voor externe API-koppelingen (NRC, ZRC, ZTC, etc.)
        """.trimIndent(),
        license = OpenApiInfo.License(
            name = "EUPL 1.2",
            url = "https://opensource.org/licenses/EUPL-1.2",
        ),
    )

    override val security: List<SecurityRequirement>
        get() = listOf(
            mapOf("auth-jwt" to listOf("openid", "profile", "email")),
            mapOf("auth-zgw" to emptyList()),
        )

    override val tags: List<Tag> get() = listOf(
        Tag("application-settings", "Beheer van applicatie-credentials voor ZGW-authenticatie"),
        Tag("storage-repositories", "Beheer van blob storage backends (S3 / Azure Blob Storage)"),
        Tag("dmf-settings", "Generieke runtime-instellingen (key/value)"),
        Tag("api-connection-settings", "Beheer van verbindingsinstellingen voor externe API-koppelingen"),
    )
}
