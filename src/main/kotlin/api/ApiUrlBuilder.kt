// SPDX-License-Identifier: EUPL-1.2
// Copyright (C) 2025 Gemeente Utrecht
package com.baseflow.api

import com.baseflow.config.ApplicationConfig

/**
 * Helper to construct relative and absolute API URLs for resources.
 */
object ApiUrlBuilder {
    fun path(segment: String, id: String): String =
        "$DOCUMENTEN_API_BASE_PATH/$segment/$id"

    fun absolute(segment: String, id: String): String =
        ApplicationConfig.baseUrl() + path(segment, id)
}
