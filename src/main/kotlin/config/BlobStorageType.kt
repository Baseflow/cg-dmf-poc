// SPDX-License-Identifier: EUPL-1.2
// Copyright (C) 2026 Gemeente Utrecht
package com.baseflow.config

/**
 * Supported blob storage types.
 */
enum class BlobStorageType(val label: String) {
    S3("S3"),
    AZURE_BLOB_STORAGE("Azure Blob Storage"),
    ;

    companion object {
        fun fromLabel(label: String): BlobStorageType = entries.firstOrNull { it.label.equals(label, ignoreCase = true) }
            ?: throw IllegalArgumentException(
                "Unknown blob storage type: '$label'. Supported types: ${entries.joinToString { it.label }}",
            )
    }
}
