// SPDX-License-Identifier: EUPL-1.2
// Copyright (C) 2025 Gemeente Utrecht
package com.baseflow.api

import com.baseflow.config.ApplicationConfig
import io.ktor.http.*

/**
 * Helper to construct relative and absolute API URLs for resources.
 */
object ApiUrlBuilder {
    fun path(vararg segments: String): String = URLBuilder().apply {
        path(DOCUMENTEN_API_BASE_PATH, *segments)
    }.build().encodedPath

    fun absolute(vararg segments: String, queryParameters: Map<String, String> = emptyMap()): String =
        URLBuilder(ApplicationConfig.baseUrl()).apply {
            path(DOCUMENTEN_API_BASE_PATH, *segments)
            queryParameters.forEach { (key, value) ->
                parameters.append(key, value)
            }
        }.buildString()
}
