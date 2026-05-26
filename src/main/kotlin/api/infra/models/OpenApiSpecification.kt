// SPDX-License-Identifier: EUPL-1.2
// Copyright (C) 2026 Gemeente Utrecht
package com.baseflow.api.infra.models

import com.baseflow.config.WopiConfig
import io.ktor.openapi.OpenApiInfo
import io.ktor.openapi.Tag
import org.koin.java.KoinJavaComponent.inject

internal val openApiSpecifications = listOf(
    DocumentenOpenApiSpecification(),
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
}
