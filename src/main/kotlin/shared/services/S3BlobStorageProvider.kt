// SPDX-License-Identifier: EUPL-1.2
// Copyright (C) 2026 Gemeente Utrecht
package com.baseflow.shared.services

import com.baseflow.shared.config.BlobStorageRepoConfig
import org.reactivestreams.Subscriber
import org.reactivestreams.Subscription
import org.slf4j.LoggerFactory
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.core.async.AsyncRequestBody
import software.amazon.awssdk.core.async.AsyncResponseTransformer
import software.amazon.awssdk.core.checksums.RequestChecksumCalculation
import software.amazon.awssdk.core.checksums.ResponseChecksumValidation
import software.amazon.awssdk.http.nio.netty.NettyNioAsyncHttpClient
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3AsyncClient
import software.amazon.awssdk.services.s3.S3Configuration
import software.amazon.awssdk.services.s3.model.*
import java.io.InputStream
import java.io.OutputStream
import java.net.URI
import java.nio.ByteBuffer
import java.util.concurrent.CompletableFuture

/**
 * [BlobStorageProvider] implementation backed by an S3-compatible object store.
 */
class S3BlobStorageProvider(private val config: BlobStorageRepoConfig) : BlobStorageProvider {

    private val logger = LoggerFactory.getLogger(S3BlobStorageProvider::class.java)

    override val name: String = config.name

    private val bucketName: String = config.bucket

    private val s3Client: S3AsyncClient = buildClient()

    init {
        logger.info("Created S3BlobStorageProvider '{}' for bucket {} at {}", name, bucketName, config.url)
    }

    private fun buildClient(): S3AsyncClient {
        logger.info(
            "Building S3 client for '{}': bucket={}, region={}, disableChecksums={}, disableChunkedEncoding={}",
            name,
            config.bucket,
            config.region,
            config.disableChecksums,
            config.disableChunkedEncoding,
        )
        val creds = StaticCredentialsProvider.create(
            AwsBasicCredentials.create(config.accessKey, config.secretKey),
        )
        val s3Config = S3Configuration.builder()
            .pathStyleAccessEnabled(true)
            .chunkedEncodingEnabled(!config.disableChunkedEncoding)
            .build()

        val httpClientBuilder = NettyNioAsyncHttpClient.builder()
            .connectionTimeout(config.connectTimeout)
            .readTimeout(config.readWriteTimeout)
            .writeTimeout(config.readWriteTimeout)
            .connectionMaxIdleTime(config.maxIdleTime)

        val clientBuilder = S3AsyncClient.builder()
            .region(Region.of(config.region ?: "eu-west-1"))
            .endpointOverride(URI.create(config.url))
            .credentialsProvider(creds)
            .httpClientBuilder(httpClientBuilder)
            .serviceConfiguration(s3Config)

        if (config.disableChecksums) {
            clientBuilder
                .requestChecksumCalculation(RequestChecksumCalculation.WHEN_REQUIRED)
                .responseChecksumValidation(ResponseChecksumValidation.WHEN_REQUIRED)
        }
        return clientBuilder.build()
    }

    override fun uploadFile(objectName: String, content: ByteArray): Long {
        try {
            ensureBucketExists()

            logger.debug("Uploading {} to bucket {} (size: {} bytes)", objectName, bucketName, content.size)
            val putRequest = PutObjectRequest.builder()
                .bucket(bucketName)
                .key(objectName)
                .contentLength(content.size.toLong())
                .build()
            val response = s3Client.putObject(putRequest, AsyncRequestBody.fromBytes(content)).join()
            logger.info("Uploaded {}/{} (ETag: {})", bucketName, objectName, response.eTag())
            return content.size.toLong()
        } catch (e: Exception) {
            logger.error("Failed to upload {} to bucket {}: {}", objectName, bucketName, e.message, e)
            throw e
        }
    }

    override fun uploadFile(objectName: String, stream: InputStream, contentLength: Long): Long {
        try {
            ensureBucketExists()
            logger.debug("Streaming upload of {} to bucket {} ({} bytes)", objectName, bucketName, contentLength)
            val putRequest = PutObjectRequest.builder()
                .bucket(bucketName)
                .key(objectName)
                .contentLength(contentLength)
                .build()

            val body = AsyncRequestBody.forBlockingOutputStream(contentLength)
            val future = s3Client.putObject(putRequest, body)
            val os = body.outputStream()
            val actualBytes: Long
            try {
                actualBytes = stream.transferTo(os)
            } finally {
                os.close()
            }
            if (actualBytes != contentLength) {
                future.cancel(true)
                throw IllegalStateException(
                    "Upload size mismatch for $objectName: declared $contentLength bytes but streamed $actualBytes bytes",
                )
            }
            val response = future.join()

            logger.info(
                "Uploaded {}/{} via stream (ETag: {}, {} bytes)",
                bucketName,
                objectName,
                response.eTag(),
                actualBytes,
            )
            return actualBytes
        } catch (e: Exception) {
            logger.error("Failed to stream-upload {} to bucket {}: {}", objectName, bucketName, e.message, e)
            throw e
        }
    }

    override fun downloadFileTo(objectName: String, output: OutputStream): CompletableFuture<Void> {
        logger.debug("Streaming download of {} from bucket {}", objectName, bucketName)
        val getRequest = GetObjectRequest.builder()
            .bucket(bucketName)
            .key(objectName)
            .build()

        val result = CompletableFuture<Void>()

        s3Client
            .getObject(getRequest, AsyncResponseTransformer.toPublisher())
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

    override fun isHealthy(): Boolean = try {
        s3Client.headBucket(HeadBucketRequest.builder().bucket(bucketName).build()).join()
        true
    } catch (e: Exception) {
        logger.warn("S3 health check failed for '{}' (bucket: {}): {}", name, bucketName, e.message)
        false
    }

    override fun deleteFile(objectName: String) {
        val request = DeleteObjectRequest.builder()
            .bucket(bucketName)
            .key(objectName)
            .build()
        s3Client.deleteObject(request).join()
        logger.debug("Deleted {}/{}", bucketName, objectName)
    }

    /**
     * Deletes multiple objects in a single S3 `DeleteObjects` request.
     * Falls back to individual [deleteFile] calls when [objectNames] is empty.
     * S3 accepts at most 1 000 keys per request; larger batches are chunked automatically.
     */
    override fun deleteFiles(objectNames: List<String>) {
        if (objectNames.isEmpty()) return
        objectNames.chunked(1000).forEach { chunk ->
            val identifiers = chunk.map { ObjectIdentifier.builder().key(it).build() }
            val request = DeleteObjectsRequest.builder()
                .bucket(bucketName)
                .delete(Delete.builder().objects(identifiers).build())
                .build()
            val response = s3Client.deleteObjects(request).join()
            if (response.hasErrors()) {
                val errors = response.errors().joinToString { "${it.key()}: ${it.message()}" }
                logger.warn("S3 DeleteObjects returned errors for bucket {}: {}", bucketName, errors)
            }
            logger.debug("Deleted {} object(s) from {}", chunk.size, bucketName)
        }
    }

    private fun ensureBucketExists() {
        if (!s3Client.listBuckets().join().buckets().any { it.name() == bucketName }) {
            logger.info("Bucket {} does not exist, creating it", bucketName)
            s3Client.createBucket { it.bucket(bucketName) }.join()
        }
    }
}
