// SPDX-License-Identifier: EUPL-1.2
// Copyright (C) 2026 Gemeente Utrecht
package com.baseflow.shared.api.models.settings

import com.baseflow.shared.entities.settings.ApiAuthType
import kotlinx.serialization.Serializable

@Serializable
data class ApiConnectionSettingResponse(
    val id: String,
    val name: String,
    val baseUrl: String,
    val clientId: String,
    val hasSecret: Boolean,
    val clientSecret: String?,
    val apiType: String,
    val authType: String,
    val validationEnabled: Boolean,
    val enabled: Boolean,
    val readonly: Boolean,
    val createdAt: String,
    val updatedAt: String,
)

@Serializable
data class CreateApiConnectionSettingRequest(
    val name: String,
    val baseUrl: String,
    val clientId: String,
    val clientSecret: String? = null,
    val apiType: String,
    val authType: String = ApiAuthType.ZGW_AUTH.value,
    val validationEnabled: Boolean = true,
    val enabled: Boolean = true,
)

@Serializable
data class UpdateApiConnectionSettingRequest(
    val name: String,
    val baseUrl: String,
    val clientId: String,
    val clientSecret: String? = null,
    val apiType: String,
    val authType: String = ApiAuthType.ZGW_AUTH.value,
    val validationEnabled: Boolean = true,
    val enabled: Boolean = true,
)
