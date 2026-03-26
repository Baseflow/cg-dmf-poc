// SPDX-License-Identifier: EUPL-1.2
// Copyright (C) 2026 Gemeente Utrecht
package com.baseflow.config

import org.slf4j.LoggerFactory

/**
 * Configuration for the Azure Blob Storage backend.
 *
 * All values are read from environment variables (or a .env file via dotenv-kotlin).
 *
 * Env vars:
 *   AZURE_STORAGE_ACCOUNT_NAME    - Storage account name (required)
 *   AZURE_STORAGE_ACCOUNT_KEY     - Storage account key (required)
 *   AZURE_STORAGE_CONTAINER_NAME  - Blob container name (default: cg-dmf)
 *   AZURE_STORAGE_ENDPOINT        - Override the service endpoint URL (optional, for Azurite / custom endpoints)
 */
internal object AzureStorageConfig : Config() {
    private val logger = LoggerFactory.getLogger(AzureStorageConfig::class.java)

    val accountName: String = envOrThrow("AZURE_STORAGE_ACCOUNT_NAME")
    val accountKey: String = envOrThrow("AZURE_STORAGE_ACCOUNT_KEY")
    val containerName: String = envOrSystem("AZURE_STORAGE_CONTAINER_NAME", "cg-dmf")

    /**
     * Optional endpoint override. Useful for Azurite (local emulator) or custom endpoints.
     * When blank the SDK uses the default https://<accountName>.blob.core.windows.net endpoint.
     */
    val endpoint: String = envOrSystem("AZURE_STORAGE_ENDPOINT", "")

    override fun printConfig() {
        logger.info(
            "AzureStorageConfig: accountName={}, containerName={}, endpoint={}",
            accountName,
            containerName,
            endpoint.ifBlank { "(default)" },
        )
        logger.debug("AzureStorageConfig: accountKey is set: {}", accountKey.isNotEmpty())
    }
}

