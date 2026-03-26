// SPDX-License-Identifier: EUPL-1.2
// Copyright (C) 2026 Gemeente Utrecht
package com.baseflow.services

import com.azure.core.util.BinaryData
import com.azure.storage.blob.BlobContainerClient
import com.azure.storage.blob.BlobServiceClientBuilder
import com.azure.storage.blob.models.BlobStorageException
import com.azure.storage.common.StorageSharedKeyCredential
import com.baseflow.config.AzureStorageConfig
import org.koin.core.annotation.Singleton
import org.slf4j.LoggerFactory
import java.io.OutputStream
import java.util.UUID
import java.util.concurrent.CompletableFuture

/**
 * Azure Blob Storage backend.
 *
 * The backend is selected at startup via the `STORAGE_BACKEND` environment variable.
 * Set `STORAGE_BACKEND=azure` to use this implementation.
 *
 * @see IStorageService
 * @see S3StorageService
 */
@Singleton
open class AzureStorageService(private val containerClient: BlobContainerClient) : IStorageService {

    private val logger = LoggerFactory.getLogger(AzureStorageService::class.java)

    /**
     * Secondary constructor used by Koin when no [BlobContainerClient] is provided in the DI graph:
     * builds the client from [AzureStorageConfig].
     */
    constructor() : this(buildContainerClient())

    private val containerName: String get() = containerClient.blobContainerName

    init {
        logger.info(
            "Created Azure Blob Storage client for container {}",
            containerName,
        )
    }

    override fun uploadFile(objectName: String, content: ByteArray) {
        try {
            logger.debug(
                "Uploading blob {} to container {} (size: {} bytes)",
                objectName,
                containerName,
                content.size,
            )
            if (!containerClient.exists()) {
                logger.info("Container {} does not exist, creating it", containerName)
                containerClient.create()
            }
            val blobClient = containerClient.getBlobClient(objectName)
            blobClient.upload(BinaryData.fromBytes(content), /* overwrite = */ true)
            logger.info(
                "Successfully uploaded blob {}/{}",
                containerName,
                objectName,
            )
        } catch (e: Exception) {
            logger.error(
                "Failed to upload blob {} to container {}: {}",
                objectName,
                containerName,
                e.message,
                e,
            )
            throw e
        }
    }

    override fun downloadFileTo(objectName: String, output: OutputStream): CompletableFuture<Void> {
        logger.debug(
            "Streaming download of blob {} from container {}",
            objectName,
            containerName,
        )
        return CompletableFuture.supplyAsync {
            val blobClient = containerClient.getBlobClient(objectName)
            blobClient.downloadStream(output)
            output.flush()
            null
        }
    }

    override fun checkHealth(): StorageStatus {
        val readStatus = try {
            containerClient.exists()
            DependencyStatus(status = "ok")
        } catch (e: Exception) {
            logger.warn("Azure storage read health check failed: {}", e.message)
            DependencyStatus(status = "error", detail = e.message)
        }

        val writeStatus = try {
            if (!containerClient.exists()) {
                logger.info(
                    "Container {} does not exist, creating it during health check",
                    containerName,
                )
                containerClient.create()
            }
            val probeKey = ".healthcheck-probe-${UUID.randomUUID()}"
            val blobClient = containerClient.getBlobClient(probeKey)
            blobClient.upload(BinaryData.fromBytes(byteArrayOf()), /* overwrite = */ true)
            try {
                blobClient.delete()
            } catch (_: Exception) {
                logger.warn("Azure storage health check: probe blob cleanup failed (key={})", probeKey)
            }
            DependencyStatus(status = "ok")
        } catch (e: BlobStorageException) {
            logger.warn("Azure storage write health check failed (HTTP {}): {}", e.statusCode, e.message)
            DependencyStatus(
                status = "error",
                detail = "Azure Blob Storage error (HTTP ${e.statusCode}): ${e.message}",
            )
        } catch (e: Exception) {
            logger.warn("Azure storage write health check failed: {}", e.message)
            DependencyStatus(status = "error", detail = e.message)
        }

        val storageOk = readStatus.status == "ok" && writeStatus.status == "ok"
        return StorageStatus(
            status = if (storageOk) "ok" else "error",
            read = readStatus,
            write = writeStatus,
        )
    }

    companion object {
        fun buildContainerClient(): BlobContainerClient {
            val credential = StorageSharedKeyCredential(
                AzureStorageConfig.accountName,
                AzureStorageConfig.accountKey,
            )
            val endpoint = AzureStorageConfig.endpoint.ifBlank {
                "https://${AzureStorageConfig.accountName}.blob.core.windows.net"
            }
            val serviceClient = BlobServiceClientBuilder()
                .endpoint(endpoint)
                .credential(credential)
                .buildClient()
            return serviceClient.getBlobContainerClient(AzureStorageConfig.containerName)
        }
    }
}



