// SPDX-License-Identifier: EUPL-1.2
// Copyright (C) 2026 Gemeente Utrecht
package com.baseflow.api.models

import kotlinx.serialization.Serializable

@Serializable
data class OidcProviderResponse(
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
data class CreateOidcProviderRequest(
    val name: String,
    val issuer: String,
    val clientId: String,
    /** Null or omit to create a provider without a secret. */
    val clientSecret: String? = null,
)

@Serializable
data class UpdateOidcProviderRequest(
    val name: String,
    val issuer: String,
    val clientId: String,
    /**
     * New client secret. Null or omit to keep the existing secret unchanged.
     * Note: there is no mechanism to clear an existing secret via this field.
     */
    val clientSecret: String? = null,
)
