// SPDX-License-Identifier: EUPL-1.2
// Copyright (C) 2025-2026 Gemeente Utrecht
package com.baseflow.services

import com.baseflow.config.S3Config
import com.baseflow.config.S3ClientFactory
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.koin.core.annotation.Singleton
import org.slf4j.LoggerFactory
import software.amazon.awssdk.services.s3.model.HeadBucketRequest
import software.amazon.awssdk.services.s3.model.NoSuchBucketException
import software.amazon.awssdk.services.s3.model.S3Exception
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
open class HealthCheckService(s3ClientFactory: S3ClientFactory) {

    private val logger = LoggerFactory.getLogger(HealthCheckService::class.java)

    private val s3Client = s3ClientFactory.create()
    private val s3TimeoutSeconds = S3ClientFactory.S3_OPERATION_TIMEOUT.toSeconds()

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
                    .bucket(S3Config.bucketName)
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
                DependencyStatus(
                    status = "error",
                    detail = "Storage read timed out after ${s3TimeoutSeconds}s"
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
                    .bucket(S3Config.bucketName)
                    .key(probeKey)
                    .build()

                try {
                    s3Client.putObject(
                        putRequest,
                        software.amazon.awssdk.core.async.AsyncRequestBody.fromBytes(byteArrayOf()),
                    ).orTimeout(s3TimeoutSeconds, TimeUnit.SECONDS).join()
                } catch (e: CompletionException) {
                    // Unwrap and rethrow so the typed catch-blocks below can match
                    throw e.cause ?: e
                }

                try {
                    s3Client.deleteObject { it.bucket(S3Config.bucketName).key(probeKey) }
                        .orTimeout(s3TimeoutSeconds, TimeUnit.SECONDS)
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
                    detail = "Storage write timed out after ${s3TimeoutSeconds}s",
                )
            } catch (_: NoSuchBucketException) {
                logger.warn("Storage write health check failed – bucket not found: {}", S3Config.bucketName)
                DependencyStatus(
                    status = "error",
                    detail = "Bucket '${S3Config.bucketName}' does not exist",
                )
            } catch (e: S3Exception) {
                if (e.statusCode() == 403) {
                    logger.warn("Storage write health check failed – access denied: {}", e.message)
                    DependencyStatus(
                        status = "error",
                        detail = "Access denied for bucket '${S3Config.bucketName}': ${
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
