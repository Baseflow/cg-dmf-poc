// SPDX-License-Identifier: EUPL-1.2
// Copyright (C) 2026 Gemeente Utrecht
package com.baseflow.api.models

import kotlinx.serialization.Serializable

@Serializable
data class OidcSettingsResponse(val issuer: String, val clientId: String, val hasSecret: Boolean, val updatedAt: String)

@Serializable
data class UpdateOidcSettingsRequest(
    val issuer: String,
    val clientId: String,
    /** Leave null or omit to keep the existing secret unchanged. */
    val clientSecret: String? = null,
)
