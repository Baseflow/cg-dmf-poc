// SPDX-License-Identifier: EUPL-1.2
// Copyright (C) 2026 Gemeente Utrecht
package com.baseflow.services

import com.azure.storage.blob.BlobContainerClientBuilder
import com.azure.storage.blob.BlobServiceClientBuilder
import com.azure.storage.common.StorageSharedKeyCredential
import com.baseflow.config.BlobStorageRepoConfig
import org.slf4j.LoggerFactory
import java.io.OutputStream
import java.util.concurrent.CompletableFuture

/**
 * [BlobStorageProvider] implementation backed by Azure Blob Storage.
 *
 * Extra properties recognised from env:
 * - `CONTAINER_NAME` — overrides [BlobStorageRepoConfig.bucket] as the container name.
 */
class AzureBlobStorageProvider(private val config: BlobStorageRepoConfig) : BlobStorageProvider {

    private val logger = LoggerFactory.getLogger(AzureBlobStorageProvider::class.java)

    override val name: String = config.name

    private val containerName: String = config.extraProperties["CONTAINER_NAME"] ?: config.bucket

    private val credential = StorageSharedKeyCredential(config.accessKey, config.secretKey)

    private val blobServiceClient = BlobServiceClientBuilder()
        .endpoint(config.url)
        .credential(credential)
        .buildClient()

    private val containerClient = BlobContainerClientBuilder()
        .endpoint(config.url)
        .credential(credential)
        .containerName(containerName)
        .buildClient()

    init {
        logger.info("Created AzureBlobStorageProvider '{}' for container {} at {}", name, containerName, config.url)
        ensureContainerExists()
    }

    override fun uploadFile(objectName: String, content: ByteArray) {
        try {
            val blobClient = containerClient.getBlobClient(objectName)
            blobClient.upload(content.inputStream(), content.size.toLong(), true)
            logger.info("Uploaded blob {}/{} ({} bytes)", containerName, objectName, content.size)
        } catch (e: Exception) {
            logger.error("Failed to upload blob {} to container {}: {}", objectName, containerName, e.message, e)
            throw e
        }
    }

    override fun downloadFileTo(objectName: String, output: OutputStream): CompletableFuture<Void> {
        val future = CompletableFuture<Void>()
        try {
            val blobClient = containerClient.getBlobClient(objectName)
            blobClient.downloadStream(output)
            output.flush()
            future.complete(null)
        } catch (e: Exception) {
            logger.error("Failed to download blob {} from container {}: {}", objectName, containerName, e.message, e)
            future.completeExceptionally(e)
        }
        return future
    }

    override fun isHealthy(): Boolean = try {
        blobServiceClient.accountInfo
        true
    } catch (_: Exception) {
        false
    }

    private fun ensureContainerExists() {
        if (!containerClient.exists()) {
            logger.info("Container {} does not exist, creating it", containerName)
            containerClient.create()
        }
    }
}
