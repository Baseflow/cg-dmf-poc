// SPDX-License-Identifier: EUPL-1.2
// Copyright (C) 2026 Gemeente Utrecht
package com.baseflow.shared.services

import com.azure.core.http.netty.NettyAsyncHttpClientBuilder
import com.azure.core.http.rest.Response
import com.azure.storage.blob.BlobContainerClientBuilder
import com.azure.storage.blob.BlobServiceClientBuilder
import com.azure.storage.blob.batch.BlobBatchClientBuilder
import com.azure.storage.blob.models.DeleteSnapshotsOptionType
import com.azure.storage.common.StorageSharedKeyCredential
import com.baseflow.shared.config.BlobStorageRepoConfig
import org.slf4j.LoggerFactory
import reactor.netty.resources.ConnectionProvider
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.CompletableFuture

/**
 * [BlobStorageProvider] implementation backed by Azure Blob Storage.
 *
 * Extra properties recognised from env:
 * - `CONTAINER_NAME` — overrides [BlobStorageRepoConfig.bucket] as the container name.
 */
class AzureBlobStorageProvider(config: BlobStorageRepoConfig) : BlobStorageProvider {

    private val logger = LoggerFactory.getLogger(AzureBlobStorageProvider::class.java)

    override val name: String = config.name

    private val containerName: String = config.extraProperties["CONTAINER_NAME"] ?: config.bucket

    private val credential = StorageSharedKeyCredential(config.accessKey, config.secretKey)

    private val httpClient = NettyAsyncHttpClientBuilder()
        .connectTimeout(config.connectTimeout)
        .readTimeout(config.readWriteTimeout)
        .writeTimeout(config.readWriteTimeout)
        .connectionProvider(
            ConnectionProvider.builder("azure-blob-${config.name}")
                .maxIdleTime(config.maxIdleTime)
                .evictInBackground(config.maxIdleTime)
                .build(),
        )
        .build()

    private val blobServiceClient = BlobServiceClientBuilder()
        .endpoint(config.url)
        .credential(credential)
        .httpClient(httpClient)
        .buildClient()

    private val blobBatchClient = BlobBatchClientBuilder(blobServiceClient).buildClient()

    private val containerClient = BlobContainerClientBuilder()
        .endpoint(config.url)
        .credential(credential)
        .containerName(containerName)
        .httpClient(httpClient)
        .buildClient()

    init {
        logger.info("Created AzureBlobStorageProvider '{}' for container {} at {}", name, containerName, config.url)
        ensureContainerExists()
    }

    override fun uploadFile(objectName: String, content: ByteArray): Long =
        uploadFile(objectName, content.inputStream(), content.size.toLong())

    override fun uploadFile(objectName: String, stream: InputStream, contentLength: Long): Long {
        try {
            var actualBytes = 0L
            val countingStream = object : InputStream() {
                override fun read(): Int = stream.read().also { if (it >= 0) actualBytes++ }
                override fun read(b: ByteArray, off: Int, len: Int) = stream.read(b, off, len).also { if (it > 0) actualBytes += it }
            }
            val blobClient = containerClient.getBlobClient(objectName)
            blobClient.upload(countingStream, contentLength, true)
            if (actualBytes != contentLength) {
                throw IllegalStateException(
                    "Upload size mismatch for $objectName: declared $contentLength bytes but streamed $actualBytes bytes",
                )
            }
            logger.info("Uploaded blob {}/{} via stream ({} bytes)", containerName, objectName, actualBytes)
            return actualBytes
        } catch (e: Exception) {
            logger.error(
                "Failed to stream-upload blob {} to container {}: {}",
                objectName,
                containerName,
                e.message,
                e,
            )
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

    override fun openDownloadStream(objectName: String): InputStream {
        logger.debug("Opening download stream for {} from container {}", objectName, containerName)
        return containerClient.getBlobClient(objectName).openInputStream()
    }

    override fun isHealthy(): Boolean = try {
        blobServiceClient.accountInfo
        true
    } catch (_: Exception) {
        false
    }

    override fun deleteFile(objectName: String) {
        containerClient.getBlobClient(objectName).delete()
        logger.debug("Deleted blob {}/{}", containerName, objectName)
    }

    /**
     * Deletes multiple blobs using the Azure Blob Batch API.
     * Azure accepts at most 256 operations per batch; larger lists are chunked automatically.
     */
    override fun deleteFiles(objectNames: List<String>) {
        if (objectNames.isEmpty()) return
        objectNames.chunked(256).forEach { chunk ->
            val urls = chunk.map { containerClient.getBlobClient(it).blobUrl }
            blobBatchClient.deleteBlobs(urls, DeleteSnapshotsOptionType.INCLUDE)
                .forEach { response: Response<Void> ->
                    if (response.statusCode !in 200..299) {
                        logger.warn(
                            "Azure batch delete: unexpected status {} for blob in container {}",
                            response.statusCode,
                            containerName,
                        )
                    }
                }
            logger.debug("Deleted {} blob(s) from container {}", chunk.size, containerName)
        }
    }

    private fun ensureContainerExists() {
        if (!containerClient.exists()) {
            logger.info("Container {} does not exist, creating it", containerName)
            containerClient.create()
        }
    }
}
