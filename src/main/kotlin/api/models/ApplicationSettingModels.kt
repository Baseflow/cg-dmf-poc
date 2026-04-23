// SPDX-License-Identifier: EUPL-1.2
// Copyright (C) 2026 Gemeente Utrecht
package com.baseflow.api.models

import kotlinx.serialization.Serializable

@Serializable
data class ApplicationSettingResponse(
    val id: String,
    val name: String,
    val clientId: String,
    val hasSecret: Boolean,
    val updatedAt: String,
)

@Serializable
data class CreateApplicationSettingRequest(
    val name: String,
    val clientId: String,
    val clientSecret: String? = null,
)

@Serializable
data class UpdateApplicationSettingRequest(
    val name: String,
    val clientId: String,
    /** Null or omit to keep the existing secret unchanged. */
    val clientSecret: String? = null,
)

@Serializable
data class RotateSecretRequest(
    /** New secret value. Null or omit to auto-generate a 32-byte hex secret. */
    val newSecret: String? = null,
)

@Serializable
data class RotateSecretResponse(
    /** The new plaintext secret. Returned once — never stored in plain text. */
    val secret: String,
)
