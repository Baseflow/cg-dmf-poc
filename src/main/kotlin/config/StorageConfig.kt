// SPDX-License-Identifier: EUPL-1.2
// Copyright (C) 2026 Gemeente Utrecht
package com.baseflow.config

import org.slf4j.LoggerFactory

/**
 * Selects the active storage backend.
 *
 * Set the `STORAGE_BACKEND` environment variable to one of:
 * - `s3`    — S3-compatible object storage (MinIO, AWS S3, …) — **default**
 * - `azure` — Azure Blob Storage
 */
internal object StorageConfig : Config() {
    private val logger = LoggerFactory.getLogger(StorageConfig::class.java)

    enum class Backend { S3, AZURE }

    val backend: Backend = when (envOrSystem("STORAGE_BACKEND", "s3").lowercase().trim()) {
        "azure" -> Backend.AZURE
        else -> Backend.S3
    }

    override fun printConfig() {
        logger.info("StorageConfig: backend={}", backend)
    }
}

