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
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3AsyncClient
import software.amazon.awssdk.services.s3.S3Configuration
import software.amazon.awssdk.services.s3.model.HeadBucketRequest
import java.net.URI
import java.util.UUID

@Serializable
data class DependencyStatus(
    val status: String,
    val detail: String? = null,
)

@Serializable
data class HealthValidateResponse(
    val database: DependencyStatus,
    val storage: DependencyStatus,
) {
    val healthy: Boolean get() = database.status == "ok" && storage.status == "ok"
}

@Singleton
class HealthCheckService {

    private val logger = LoggerFactory.getLogger(HealthCheckService::class.java)

    fun checkDatabase(): DependencyStatus {
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

    fun checkStorage(): DependencyStatus {
        return try {
            val creds = StaticCredentialsProvider.create(
                AwsBasicCredentials.create(MinioConfig.accessKey, MinioConfig.secretKey),
            )
            val s3Config = S3Configuration.builder()
                .pathStyleAccessEnabled(true)
                .build()

            val s3Client = S3AsyncClient.builder()
                .region(Region.EU_WEST_1)
                .endpointOverride(URI.create(MinioConfig.endpoint))
                .credentialsProvider(creds)
                .httpClientBuilder(NettyNioAsyncHttpClient.builder())
                .serviceConfiguration(s3Config)
                .build()

            // Check read access: verify the bucket exists (HEAD bucket)
            val headRequest = HeadBucketRequest.builder()
                .bucket(MinioConfig.bucketName)
                .build()

            try {
                s3Client.headBucket(headRequest).join()
            } catch (_: Exception) {
                // Bucket may not exist yet; verify connectivity by listing buckets instead
                s3Client.listBuckets().join()
            }

            // Check write access: upload a small probe object and delete it
            val probeKey = ".healthcheck-probe-${UUID.randomUUID()}"
            val putRequest = software.amazon.awssdk.services.s3.model.PutObjectRequest.builder()
                .bucket(MinioConfig.bucketName)
                .key(probeKey)
                .build()

            // Ensure bucket exists before writing
            val bucketExists = s3Client.listBuckets().join().buckets()
                .any { it.name() == MinioConfig.bucketName }

            if (!bucketExists) {
                s3Client.createBucket { it.bucket(MinioConfig.bucketName) }.join()
            }

            s3Client.putObject(
                putRequest,
                software.amazon.awssdk.core.async.AsyncRequestBody.fromBytes(byteArrayOf()),
            ).join()

            s3Client.deleteObject { it.bucket(MinioConfig.bucketName).key(probeKey) }.join()

            s3Client.close()
            DependencyStatus(status = "ok")
        } catch (e: Exception) {
            logger.warn("Storage health check failed: {}", e.message)
            DependencyStatus(status = "error", detail = e.message)
        }
    }
}

