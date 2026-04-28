// SPDX-License-Identifier: EUPL-1.2
// Copyright (C) 2026 Gemeente Utrecht
package com.baseflow.api.infra.models

import com.baseflow.api.WOPI_API_BASE_PATH
import com.baseflow.api.WOPI_API_VERSION
import io.ktor.openapi.OpenApiInfo
import io.ktor.openapi.Tag

internal class WopiOpenApiSpecification : OpenApiSpecification {
    override val name: String get() = "WOPI"

    override val basePath: String get() = WOPI_API_BASE_PATH

    override val apiInfo: OpenApiInfo get() = OpenApiInfo(
        title = "WOPI Integration API",
        version = WOPI_API_VERSION,
        description = """
        Een API om een WOPI-compatible client te integreren met de Documenten API.
        """.trimIndent(),
        license = OpenApiInfo.License(
            name = "EUPL 1.2",
            url = "https://opensource.org/licenses/EUPL-1.2",
        ),
    )

    override val tags: List<Tag> get() = listOf(
        Tag("wopi", "WOPI (Web Application Open Platform Interface) host endpoints"),
    )
}
