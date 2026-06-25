// SPDX-License-Identifier: EUPL-1.2
// Copyright (C) 2026 Gemeente Utrecht
package com.baseflow.infra.api.models

import com.baseflow.shared.config.WopiConfig
import io.ktor.openapi.OpenApiInfo
import io.ktor.openapi.SecurityRequirement
import io.ktor.openapi.Tag

internal val openApiSpecifications = listOf(
    DocumentenOpenApiSpecification(),
    SettingsOpenApiSpecification(),
).let { specs ->
    val config = WopiConfig.fromEnv()

    if (config.isEnabled()) {
        specs + WopiOpenApiSpecification()
    } else {
        specs
    }
}

/**
 * Represents an OpenAPI specification for API documentation.
 *
 * This class contains the metadata that describes an API in a structured format, so it can be used to generate
 * documentation according to the OpenAPI specification standard.
 */
interface OpenApiSpecification {
    val name: String
    val basePath: String
    val apiInfo: OpenApiInfo
    val tags: List<Tag>
    val security: List<SecurityRequirement>
        get() = listOf(
            mapOf("auth-jwt" to listOf("openid", "profile", "email")),
            mapOf("auth-zgw" to emptyList()),
        )
}
