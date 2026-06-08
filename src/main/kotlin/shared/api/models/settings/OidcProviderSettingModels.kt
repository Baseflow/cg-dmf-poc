// SPDX-License-Identifier: EUPL-1.2
// Copyright (C) 2026 Gemeente Utrecht
package com.baseflow.shared.api.models.settings

import kotlinx.serialization.Serializable

@Serializable
data class OidcProviderSettingsResponse(
    val id: String,
    val name: String,
    val issuer: String,
    val clientId: String,
    /** Whether an encrypted secret is stored for this provider. */
    val hasSecret: Boolean,
    /**
     * The decrypted client secret. Null when [hasSecret] is false or the secret
     * has not been revealed.
     */
    val clientSecret: String?,
    val updatedAt: String,
)

@Serializable
data class CreateOidcProviderSettingsRequest(
    val name: String,
    val issuer: String,
    val clientId: String,
    /** Null or omit to create a provider without a secret. */
    val clientSecret: String? = null,
)

@Serializable
data class UpdateOidcProviderSettingsRequest(
    val name: String,
    val issuer: String,
    val clientId: String,
    /**
     * New client secret. Null or omit to keep the existing secret unchanged.
     * Note: there is no mechanism to clear an existing secret via this field.
     */
    val clientSecret: String? = null,
)
