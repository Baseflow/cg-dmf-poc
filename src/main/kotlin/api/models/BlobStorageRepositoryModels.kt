// SPDX-License-Identifier: EUPL-1.2
// Copyright (C) 2026 Gemeente Utrecht
package com.baseflow.api.models

import kotlinx.serialization.Serializable

@Serializable
data class BlobStorageRepositoryResponse(
    val id: String,
    val name: String,
    val storageType: String,
    val url: String,
    val accessKeyMasked: String,
    val secretKeyMasked: String,
    val bucket: String,
    val region: String? = null,
    val disableChecksums: Boolean,
    val disableChunkedEncoding: Boolean,
    val extraProperties: Map<String, String>,
    val isDefault: Boolean,
    val createdAt: String,
    val updatedAt: String,
)

@Serializable
data class SetDefaultRepositoryRequest(val name: String)

@Serializable
data class CreateBlobStorageRepositoryRequest(
    val name: String,
    val storageType: String,
    val url: String,
    val accessKey: String,
    val secretKey: String,
    val bucket: String,
    val region: String? = null,
    val disableChecksums: Boolean = false,
    val disableChunkedEncoding: Boolean = false,
    val extraProperties: Map<String, String> = emptyMap(),
    val isDefault: Boolean = false,
)

@Serializable
data class UpdateBlobStorageRepositoryRequest(
    val name: String? = null,
    val storageType: String? = null,
    val url: String? = null,
    val accessKey: String? = null,
    val secretKey: String? = null,
    val bucket: String? = null,
    val region: String? = null,
    val disableChecksums: Boolean? = null,
    val disableChunkedEncoding: Boolean? = null,
    val extraProperties: Map<String, String>? = null,
    val isDefault: Boolean? = null,
)