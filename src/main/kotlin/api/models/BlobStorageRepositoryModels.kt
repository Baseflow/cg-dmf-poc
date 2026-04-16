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
    val accessKeyHash: String,
    val secretKeyHash: String,
    val bucket: String,
    val region: String? = null,
    val disableChecksums: Boolean,
    val disableChunkedEncoding: Boolean,
    val extraProperties: String,
    val isDefault: Boolean,
    val createdAt: String,
    val updatedAt: String,
)

@Serializable
data class SetDefaultRepositoryRequest(val name: String)
