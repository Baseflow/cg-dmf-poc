// SPDX-License-Identifier: EUPL-1.2
// Copyright (C) 2026 Gemeente Utrecht
package com.baseflow.shared.api.models.settings

import kotlinx.serialization.Serializable

@Serializable
data class BlobStorageRepositorySettingsResponse(
    val id: String,
    val name: String,
    val storageType: String,
    val url: String,
    val bucket: String,
    val region: String? = null,
    val disableChecksums: Boolean = false,
    val disableChunkedEncoding: Boolean = false,
    val extraProperties: Map<String, String> = emptyMap(),
    val isDefault: Boolean,
    val enabled: Boolean,
    val accessKey: String? = null,
    val secretKey: String? = null,
    val storageAccountName: String? = null,
    val createdAt: String,
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
    val region: String? = null,
    val disableChecksums: Boolean = false,
    val disableChunkedEncoding: Boolean = false,
    val extraProperties: Map<String, String> = emptyMap(),
    val isDefault: Boolean = false,
    val enabled: Boolean = true,
)

@Serializable
data class PatchBlobStorageRepositorySettingsRequest(
    val name: String? = null,
    val storageType: String? = null,
    val url: String? = null,
    val accessKey: String? = null,
    val secretKey: String? = null,
    val storageAccountName: String? = null,
    val bucket: String? = null,
    val region: String? = null,
    val disableChecksums: Boolean? = null,
    val disableChunkedEncoding: Boolean? = null,
    val extraProperties: Map<String, String>? = null,
    val isDefault: Boolean? = null,
    val enabled: Boolean? = null,
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
    val region: String? = null,
    val disableChecksums: Boolean = false,
    val disableChunkedEncoding: Boolean = false,
    val extraProperties: Map<String, String> = emptyMap(),
    val isDefault: Boolean = false,
    val enabled: Boolean = true,
)
