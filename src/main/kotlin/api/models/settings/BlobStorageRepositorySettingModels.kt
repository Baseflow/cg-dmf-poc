// SPDX-License-Identifier: EUPL-1.2
// Copyright (C) 2026 Gemeente Utrecht
package com.baseflow.api.models.settings

import kotlinx.serialization.Serializable

@Serializable
data class BlobStorageRepositorySettingsResponse(
    val id: String,
    val name: String,
    val storageType: String,
    val url: String,
    val bucket: String,
    val isDefault: Boolean,
    val enabled: Boolean,
    val accessKey: String? = null,
    val secretKey: String? = null,
    val storageAccountName: String? = null,
    val updatedAt: String,
)

@Serializable
data class SetDefaultRepositorySettingsRequest(val name: String)

@Serializable
data class CreateBlobStorageRepositorySettingsRequest(
    val name: String,
    val storageType: String,
    val url: String = "",
    val accessKey: String,
    val secretKey: String? = null,
    val storageAccountName: String? = null,
    val bucket: String? = null,
    val isDefault: Boolean = false,
    val enabled: Boolean = true,
)

@Serializable
data class UpdateBlobStorageRepositorySettingsRequest(
    val name: String,
    val storageType: String,
    val url: String = "",
    val accessKey: String? = null,
    val secretKey: String? = null,
    val storageAccountName: String? = null,
    val bucket: String? = null,
    val isDefault: Boolean = false,
    val enabled: Boolean = true,
)
