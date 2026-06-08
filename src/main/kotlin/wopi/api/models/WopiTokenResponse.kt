// SPDX-License-Identifier: EUPL-1.2
// Copyright (C) 2026 Gemeente Utrecht
package com.baseflow.wopi.api.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class WopiTokenResponse(
    @SerialName("access_token")
    val accessToken: String,
    /** Unix epoch seconds when the token expires. */
    @SerialName("access_token_expires_at")
    val expiresAt: Long,
)
