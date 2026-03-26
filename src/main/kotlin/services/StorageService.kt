// SPDX-License-Identifier: EUPL-1.2
// Copyright (C) 2025-2026 Gemeente Utrecht
package com.baseflow.services

import com.baseflow.config.MinioConfig
import com.baseflow.config.S3ClientFactory
import org.koin.core.annotation.Singleton
import org.reactivestreams.Subscriber
import org.reactivestreams.Subscription
import org.slf4j.LoggerFactory
import software.amazon.awssdk.core.async.AsyncRequestBody
import software.amazon.awssdk.core.async.AsyncResponseTransformer
import software.amazon.awssdk.services.s3.S3AsyncClient
import software.amazon.awssdk.services.s3.model.GetObjectRequest
import software.amazon.awssdk.services.s3.model.HeadBucketRequest
import software.amazon.awssdk.services.s3.model.NoSuchBucketException
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import software.amazon.awssdk.services.s3.model.S3Exception
import java.io.OutputStream
import java.nio.ByteBuffer
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/**
 * S3-compatible storage backend (MinIO, AWS S3, …).
 *
 * The backend is selected at startup via the `STORAGE_BACKEND` environment variable.
 * Set `STORAGE_BACKEND=s3` (default) to use this implementation.
 *
 * @see IStorageService
 * @see AzureStorageService
 */
@Singleton
open class S3StorageService(s3ClientFactory: S3ClientFactory) : IStorageService {

    private val logger = LoggerFactory.getLogger(S3StorageService::class.java)

    private val bucketName = MinioConfig.bucketName
    private val s3TimeoutSeconds = S3ClientFactory.S3_OPERATION_TIMEOUT.toSeconds()

    private val s3Client: S3AsyncClient = s3ClientFactory.create()

    init {
        logger.info("Created S3 client for bucket {}", bucketName)
    }

    override fun uploadFile(objectName: String, content: ByteArray) {
        try {
            if (!s3Client.listBuckets().join().buckets()
                    .any { it.name() == bucketName }
            ) {
                logger.info("Bucket {} does not exist, creating it", bucketName)
                val createBucketResponse =
                    s3Client.createBucket { it.bucket(bucketName) }.join()
                logger.debug("Bucket created: {}", createBucketResponse)
            }

            logger.debug(
                "Uploading file {} to bucket {} (size: {} bytes)",
                objectName,
                bucketName,
                content.size,
            )
            val putObjectRequest = PutObjectRequest.builder()
                .bucket(bucketName)
                .key(objectName).build()
            val requestBody = AsyncRequestBody.fromBytes(content)
            val putObjectResponse =
                s3Client.putObject(putObjectRequest, requestBody).join()
            logger.info(
                "Successfully uploaded data to {}/{} (ETag: {})",
                bucketName,
                objectName,
                putObjectResponse.eTag(),
            )
        } catch (e: Exception) {
            logger.error(
                "Failed to upload file {} to bucket {}: {}",
                objectName,
                bucketName,
                e.message,
                e,
            )
            throw e
        }
    }

    /**
     * Streams an object directly to the provided OutputStream without loading it fully into memory.
     */
    override fun downloadFileTo(objectName: String, output: OutputStream): CompletableFuture<Void> {
        logger.debug(
            "Streaming download of {} from bucket {}",
            objectName,
            bucketName,
        )
        val getObjectRequest = GetObjectRequest.builder()
            .bucket(bucketName)
            .key(objectName)
            .build()

        val result = CompletableFuture<Void>()

        s3Client
            .getObject(getObjectRequest, AsyncResponseTransformer.toPublisher())
            .whenComplete { responsePublisher, throwable ->
                if (throwable != null) {
                    result.completeExceptionally(throwable)
                    return@whenComplete
                }

                responsePublisher.subscribe(object : Subscriber<ByteBuffer> {
                    private lateinit var subscription: Subscription

                    override fun onSubscribe(s: Subscription) {
                        subscription = s
                        s.request(1)
                    }

                    override fun onNext(buffer: ByteBuffer) {
                        try {
                            val bytes = ByteArray(buffer.remaining())
                            buffer.get(bytes)
                            output.write(bytes)
                            output.flush()
                            subscription.request(1)
                        } catch (e: Exception) {
                            subscription.cancel()
                            result.completeExceptionally(e)
                        }
                    }

                    override fun onError(t: Throwable) {
                        result.completeExceptionally(t)
                    }

                    override fun onComplete() {
                        try {
                            output.flush()
                        } catch (_: Exception) {
                        }
                        result.complete(null)
                    }
                })
            }

        return result
    }

    override fun checkHealth(): StorageStatus {
        return try {
            val readStatus = try {
                val headRequest = HeadBucketRequest.builder()
                    .bucket(bucketName)
                    .build()
                try {
                    s3Client.headBucket(headRequest)
                        .orTimeout(s3TimeoutSeconds, TimeUnit.SECONDS)
                        .join()
                } catch (_: TimeoutException) {
                    throw TimeoutException("headBucket timed out after ${s3TimeoutSeconds}s")
                } catch (_: Exception) {
                    s3Client.listBuckets()
                        .orTimeout(s3TimeoutSeconds, TimeUnit.SECONDS)
                        .join()
                }
                DependencyStatus(status = "ok")
            } catch (e: TimeoutException) {
                logger.warn("Storage read health check timed out: {}", e.message)
                DependencyStatus(status = "error", detail = "Storage read timed out after ${s3TimeoutSeconds}s")
            } catch (e: Exception) {
                logger.warn("Storage read health check failed: {}", e.message)
                DependencyStatus(status = "error", detail = e.message)
            }

            val writeStatus = try {
                val probeKey = ".healthcheck-probe-${UUID.randomUUID()}"
                val putRequest = PutObjectRequest.builder()
                    .bucket(bucketName)
                    .key(probeKey)
                    .build()
                try {
                    s3Client.putObject(
                        putRequest,
                        AsyncRequestBody.fromBytes(byteArrayOf()),
                    ).orTimeout(s3TimeoutSeconds, TimeUnit.SECONDS).join()
                } catch (e: CompletionException) {
                    throw e.cause ?: e
                }
                try {
                    s3Client.deleteObject { it.bucket(bucketName).key(probeKey) }
                        .orTimeout(s3TimeoutSeconds, TimeUnit.SECONDS)
                        .join()
                } catch (_: Exception) {
                    logger.warn("Storage write health check: probe object cleanup failed (key={})", probeKey)
                }
                DependencyStatus(status = "ok")
            } catch (e: TimeoutException) {
                logger.warn("Storage write health check timed out: {}", e.message)
                DependencyStatus(status = "error", detail = "Storage write timed out after ${s3TimeoutSeconds}s")
            } catch (_: NoSuchBucketException) {
                logger.warn("Storage write health check failed – bucket not found: {}", bucketName)
                DependencyStatus(status = "error", detail = "Bucket '$bucketName' does not exist")
            } catch (e: S3Exception) {
                if (e.statusCode() == 403) {
                    logger.warn("Storage write health check failed – access denied: {}", e.message)
                    DependencyStatus(
                        status = "error",
                        detail = "Access denied for bucket '$bucketName': ${e.awsErrorDetails()?.errorMessage() ?: e.message}",
                    )
                } else {
                    logger.warn("Storage write health check failed: {}", e.message)
                    DependencyStatus(status = "error", detail = e.message)
                }
            } catch (e: Exception) {
                logger.warn("Storage write health check failed: {}", e.message)
                DependencyStatus(status = "error", detail = e.message)
            }

            val storageOk = readStatus.status == "ok" && writeStatus.status == "ok"
            StorageStatus(
                status = if (storageOk) "ok" else "error",
                read = readStatus,
                write = writeStatus,
            )
        } catch (e: Exception) {
            logger.warn("Storage health check failed: {}", e.message)
            val detail = e.message
            StorageStatus(
                status = "error",
                read = DependencyStatus(status = "error", detail = detail),
                write = DependencyStatus(status = "error", detail = detail),
            )
        }
    }
}
