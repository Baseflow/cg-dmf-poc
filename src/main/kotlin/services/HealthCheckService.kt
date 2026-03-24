// SPDX-License-Identifier: EUPL-1.2
// Copyright (C) 2025-2026 Gemeente Utrecht
package com.baseflow.services

import com.baseflow.config.MinioConfig
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.koin.core.annotation.Singleton
import org.slf4j.LoggerFactory
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.http.nio.netty.NettyNioAsyncHttpClient
import software.amazon.awssdk.services.s3.S3AsyncClient
import software.amazon.awssdk.services.s3.S3Configuration
import software.amazon.awssdk.services.s3.model.HeadBucketRequest
import software.amazon.awssdk.services.s3.model.NoSuchBucketException
import software.amazon.awssdk.services.s3.model.S3Exception
import java.net.URI
import java.time.Duration
import java.util.UUID
import java.util.concurrent.CompletionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

@Serializable
data class DependencyStatus(
    val status: String,
    val detail: String? = null,
)

@Serializable
data class StorageStatus(
    val status: String,
    val read: DependencyStatus,
    val write: DependencyStatus,
)

@Serializable
data class HealthValidateResponse(
    val status: String,
    val database: DependencyStatus,
    val storage: StorageStatus,
)

@Singleton
open class HealthCheckService {

    private val logger = LoggerFactory.getLogger(HealthCheckService::class.java)

    companion object {
        /** Maximum time to wait for any single S3 operation during a health check. */
        private val S3_HEALTH_TIMEOUT: Duration = Duration.ofSeconds(5)
        private val S3_HEALTH_TIMEOUT_SECONDS = S3_HEALTH_TIMEOUT.toSeconds()
    }

    private val s3Client: S3AsyncClient by lazy {
        val creds = StaticCredentialsProvider.create(
            AwsBasicCredentials.create(MinioConfig.accessKey, MinioConfig.secretKey),
        )
        val s3Config = S3Configuration.builder()
            .pathStyleAccessEnabled(true)
            .build()

        // Configure Netty HTTP client with explicit connection and read timeouts so
        // that the underlying TCP layer does not block longer than S3_HEALTH_TIMEOUT.
        val httpClientBuilder = NettyNioAsyncHttpClient.builder()
            .connectionTimeout(S3_HEALTH_TIMEOUT)
            .readTimeout(S3_HEALTH_TIMEOUT)

        S3AsyncClient.builder()
            .region(MinioConfig.region)
            .endpointOverride(URI.create(MinioConfig.endpoint))
            .credentialsProvider(creds)
            .httpClientBuilder(httpClientBuilder)
            .serviceConfiguration(s3Config)
            .build()
    }

    open fun checkDatabase(): DependencyStatus {
        return try {
            transaction {
                exec("SELECT 1")
            }
            DependencyStatus(status = "ok")
        } catch (e: Exception) {
            logger.warn("Database health check failed: {}", e.message)
            DependencyStatus(status = "error", detail = e.message)
        }
    }

    open fun checkStorage(): StorageStatus {
        return try {

            // Check read access: list buckets / head bucket
            val readStatus = try {
                val headRequest = HeadBucketRequest.builder()
                    .bucket(MinioConfig.bucketName)
                    .build()
                try {
                    s3Client.headBucket(headRequest)
                        .orTimeout(S3_HEALTH_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                        .join()
                } catch (_: TimeoutException) {
                    throw TimeoutException("headBucket timed out after ${S3_HEALTH_TIMEOUT_SECONDS}s")
                } catch (_: Exception) {
                    s3Client.listBuckets()
                        .orTimeout(S3_HEALTH_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                        .join()
                }
                DependencyStatus(status = "ok")
            } catch (e: TimeoutException) {
                logger.warn("Storage read health check timed out: {}", e.message)
                DependencyStatus(
                    status = "error",
                    detail = "Storage read timed out after ${S3_HEALTH_TIMEOUT_SECONDS}s"
                )
            } catch (e: Exception) {
                logger.warn("Storage read health check failed: {}", e.message)
                DependencyStatus(status = "error", detail = e.message)
            }

            // Check write access: attempt a probe PutObject directly without first
            // calling listBuckets(), which would require the ListAllMyBuckets permission
            // and could produce false-negative results when only PutObject/DeleteObject
            // permissions are granted.  NoSuchBucket and AccessDenied are handled
            // explicitly so the caller gets a meaningful error detail.
            val writeStatus = try {
                val probeKey = ".healthcheck-probe-${UUID.randomUUID()}"
                val putRequest = software.amazon.awssdk.services.s3.model.PutObjectRequest.builder()
                    .bucket(MinioConfig.bucketName)
                    .key(probeKey)
                    .build()

                try {
                    s3Client.putObject(
                        putRequest,
                        software.amazon.awssdk.core.async.AsyncRequestBody.fromBytes(byteArrayOf()),
                    ).orTimeout(S3_HEALTH_TIMEOUT_SECONDS, TimeUnit.SECONDS).join()
                } catch (e: CompletionException) {
                    // Unwrap and rethrow so the typed catch-blocks below can match
                    throw e.cause ?: e
                }

                try {
                    s3Client.deleteObject { it.bucket(MinioConfig.bucketName).key(probeKey) }
                        .orTimeout(S3_HEALTH_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                        .join()
                } catch (_: Exception) {
                    // Best-effort cleanup; a delete failure does not invalidate the write check.
                    logger.warn("Storage write health check: probe object cleanup failed (key={})", probeKey)
                }

                DependencyStatus(status = "ok")
            } catch (e: TimeoutException) {
                logger.warn("Storage write health check timed out: {}", e.message)
                DependencyStatus(
                    status = "error",
                    detail = "Storage write timed out after ${S3_HEALTH_TIMEOUT_SECONDS}s",
                )
            } catch (_: NoSuchBucketException) {
                logger.warn("Storage write health check failed – bucket not found: {}", MinioConfig.bucketName)
                DependencyStatus(
                    status = "error",
                    detail = "Bucket '${MinioConfig.bucketName}' does not exist",
                )
            } catch (e: S3Exception) {
                if (e.statusCode() == 403) {
                    logger.warn("Storage write health check failed – access denied: {}", e.message)
                    DependencyStatus(
                        status = "error",
                        detail = "Access denied for bucket '${MinioConfig.bucketName}': ${
                            e.awsErrorDetails()?.errorMessage() ?: e.message
                        }",
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
