// SPDX-License-Identifier: EUPL-1.2
// Copyright (C) 2026 Gemeente Utrecht
package com.baseflow.api.models.settings

import kotlinx.serialization.Serializable

@Serializable
data class ZgwApiSettingsResponse(
    val id: String,
    val name: String,
    val baseUrl: String,
    val clientId: String,
    val hasSecret: Boolean,
    val clientSecret: String?,
    val updatedAt: String,
)

@Serializable
data class CreateZgwApiSettingsRequest(
    val name: String,
    val baseUrl: String,
    val clientId: String,
    val clientSecret: String? = null,
)

@Serializable
data class UpdateZgwApiSettingsRequest(
    val name: String,
    val baseUrl: String,
    val clientId: String,
    val clientSecret: String? = null,
)
