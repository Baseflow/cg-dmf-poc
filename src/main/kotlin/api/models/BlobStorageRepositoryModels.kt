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
    val enabled: Boolean,
    val createdAt: String,
    val updatedAt: String,
    /** Decrypted access key. Null for env-var-synced entries where only a hash is stored. */
    val accessKey: String? = null,
    /** Decrypted secret key (S3 only). Null for Azure or env-var-synced entries. */
    val secretKey: String? = null,
    val storageAccountName: String? = null,
)

@Serializable
data class SetDefaultRepositoryRequest(val name: String)

@Serializable
data class CreateStorageRepositoryRequest(
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
data class UpdateStorageRepositoryRequest(
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
