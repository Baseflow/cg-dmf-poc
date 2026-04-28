// SPDX-License-Identifier: EUPL-1.2
// Copyright (C) 2026 Gemeente Utrecht
package com.baseflow.api.infra.models

import io.ktor.openapi.OpenApiInfo
import io.ktor.openapi.Tag

internal val openApiSpecifications = listOf(
    DocumentenOpenApiSpecification(),
    WopiOpenApiSpecification(),
)

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
