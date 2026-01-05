// SPDX-License-Identifier: EUPL-1.2
// Copyright (C) 2025 Gemeente Utrecht
package com.baseflow.services

import com.baseflow.config.MinioConfig
import org.slf4j.LoggerFactory
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.core.async.AsyncRequestBody
import software.amazon.awssdk.core.async.AsyncResponseTransformer
import software.amazon.awssdk.http.nio.netty.NettyNioAsyncHttpClient
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3AsyncClient
import software.amazon.awssdk.services.s3.S3Configuration
import software.amazon.awssdk.services.s3.model.GetObjectRequest
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import software.amazon.awssdk.services.s3.presigner.S3Presigner
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest
import java.net.URI
import java.io.OutputStream
import java.nio.ByteBuffer
import java.util.concurrent.CompletableFuture
import org.reactivestreams.Subscriber
import org.reactivestreams.Subscription

/**
 * StorageService interacts with the MinIO storage backend using the configuration
 * provided by MinioConfigProvider.
 */
class StorageService {

    private val logger = LoggerFactory.getLogger(StorageService::class.java)

    private val bucketName = MinioConfig.bucketName

    private val creds = StaticCredentialsProvider.create(
        AwsBasicCredentials.create(MinioConfig.accessKey, MinioConfig.secretKey)
    )

    private val s3Config = S3Configuration.builder()
        .pathStyleAccessEnabled(true)
        .build()

    private val s3Client: S3AsyncClient = S3AsyncClient.builder()
        .region(Region.EU_WEST_1)
        .endpointOverride(URI.create(MinioConfig.endpoint))
        .credentialsProvider(creds)
        .httpClientBuilder(NettyNioAsyncHttpClient.builder())
        .serviceConfiguration(s3Config)
        .build()

    private val presigner: S3Presigner = S3Presigner.builder()
        .region(Region.EU_WEST_1)
        .endpointOverride(URI.create(MinioConfig.endpoint))
        .credentialsProvider(creds)
        .serviceConfiguration(s3Config)
        .build()

    init {
        logger.info("Created S3 client for bucket {}", bucketName)
    }

    fun uploadFile(objectName: String, content: ByteArray) {
        if (!s3Client.listBuckets().join().buckets().any { it.name() == bucketName }) {
            logger.info("Bucket {} does not exist, creating it", bucketName)
            val createBucketResponse = s3Client.createBucket { it.bucket(bucketName) }.join()
            logger.debug("Bucket created: {}", createBucketResponse)
        }

        logger.debug("Uploading file {} to bucket {} (size: {} bytes)", objectName, bucketName, content.size)
        try {

            val putObjectRequest = PutObjectRequest.builder()
                .bucket(bucketName)
                .key(objectName).build()
            val requestBody = AsyncRequestBody.fromBytes(content)
            val putObjectResponse = s3Client.putObject(putObjectRequest, requestBody).join()
            logger.info("Successfully uploaded data to {}/{} (ETag: {})", bucketName, objectName, putObjectResponse.eTag())
        }
        catch (e: Exception) {
            logger.error("Error uploading file {} to bucket {}", objectName, bucketName, e)
        }
    }

    fun downloadFile(objectName: String): ByteArray {
        logger.debug("Downloading file {} from bucket {}", objectName, bucketName)
        // return byte array of file content
        val getObjectRequest = GetObjectRequest.builder().bucket(bucketName).key(objectName).build()
        val response = s3Client
            .getObject(getObjectRequest, AsyncResponseTransformer.toBytes())
            .join()

        return response.asByteArray()
    }

    /**
     * Streams an object directly to the provided OutputStream without loading it fully into memory.
     */
    fun downloadFileTo(objectName: String, output: OutputStream): CompletableFuture<Void> {
        logger.debug("Streaming download of {} from bucket {}", objectName, bucketName)
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
                            // Optionally flush to push data downstream promptly
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
                        } catch (_: Exception) { }
                        result.complete(null)
                    }
                })
            }

        return result
    }

    fun getDownloadUrl(objectName: String): String {
        // get token url
        val getReq = GetObjectRequest.builder()
            .bucket(bucketName)
            .key(objectName)
            .build()

        val presignReq = GetObjectPresignRequest.builder()
            .signatureDuration(MinioConfig.urlExpiry)
            .getObjectRequest(getReq)
            .build()

        return presigner.presignGetObject(presignReq).url().toString()
    }
}
