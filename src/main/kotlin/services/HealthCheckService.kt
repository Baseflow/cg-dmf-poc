// SPDX-License-Identifier: EUPL-1.2
// Copyright (C) 2025-2026 Gemeente Utrecht
package com.baseflow.services

import com.baseflow.config.BlobStorageRepoConfig
import com.baseflow.config.BlobStorageType
import com.baseflow.config.S3ClientFactory
import com.baseflow.config.S3Config
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.koin.core.annotation.Singleton
import org.slf4j.LoggerFactory
import java.io.ByteArrayOutputStream
import java.util.UUID
import java.util.concurrent.TimeoutException

@Serializable
data class DependencyStatus(val status: String, val detail: String? = null)

@Serializable
data class StorageStatus(val status: String, val read: DependencyStatus, val write: DependencyStatus)

@Serializable
data class HealthValidateResponse(val status: String, val database: DependencyStatus, val storage: StorageStatus)

@Singleton
open class HealthCheckService {

    private val logger = LoggerFactory.getLogger(HealthCheckService::class.java)

    open fun checkDatabase(): DependencyStatus = try {
        transaction {
            exec("SELECT 1")
        }
        DependencyStatus(status = "ok")
    } catch (e: Exception) {
        logger.warn("Database health check failed: {}", e.message)
        DependencyStatus(status = "error", detail = e.message)
    }

    /**
     * Probes S3 storage (configured via [S3Config]) for read and write availability.
     *
     * - **Read**: calls [S3BlobStorageProvider.isHealthy] (HEAD bucket).
     * - **Write**: uploads a tiny probe object, downloads it back to confirm round-trip
     *   availability, then deletes it (best-effort).
     *
     * Returns an error status immediately when S3 is not configured
     * (i.e. the required `S3_SECRET_KEY` / `MINIO_SECRET_KEY` env var is absent).
     */
    open fun checkStorage(): StorageStatus {
        val provider = BlobStorageRegistrar.defaultProvider()
            ?: legacyProvider()
            ?: return StorageStatus(
                status = "error",
                read = DependencyStatus(status = "error", detail = "S3 storage is not configured"),
                write = DependencyStatus(status = "error", detail = "S3 storage is not configured"),
            )

        val readStatus = try {
            if (provider.isHealthy()) {
                DependencyStatus(status = "ok")
            } else {
                DependencyStatus(status = "error", detail = "S3 bucket '${S3Config.bucketName}' is not reachable")
            }
        } catch (e: Exception) {
            logger.warn("Storage read health check failed: {}", e.message)
            DependencyStatus(status = "error", detail = e.message)
        }

        val writeStatus = try {
            val probeKey = ".healthcheck-probe-${UUID.randomUUID()}"

            // Write
            provider.uploadFile(probeKey, byteArrayOf())

            // Read back
            try {
                provider.downloadFileTo(probeKey, ByteArrayOutputStream())
                    .orTimeout(S3ClientFactory.S3_OPERATION_TIMEOUT.toSeconds(), java.util.concurrent.TimeUnit.SECONDS)
                    .join()
            } catch (_: TimeoutException) {
                throw TimeoutException("Download probe timed out after ${S3ClientFactory.S3_OPERATION_TIMEOUT.toSeconds()}s")
            }

            // Best-effort cleanup
            try {
                provider.deleteFile(probeKey)
            } catch (_: Exception) {
                logger.warn("Storage write health check: probe cleanup failed (key={})", probeKey)
            }

            DependencyStatus(status = "ok")
        } catch (e: TimeoutException) {
            logger.warn("Storage write health check timed out: {}", e.message)
            DependencyStatus(status = "error", detail = "Storage write timed out: ${e.message}")
        } catch (e: Exception) {
            logger.warn("Storage write health check failed: {}", e.message)
            DependencyStatus(status = "error", detail = e.message)
        }

        val storageOk = readStatus.status == "ok" && writeStatus.status == "ok"
        return StorageStatus(
            status = if (storageOk) "ok" else "error",
            read = readStatus,
            write = writeStatus,
        )
    }

    private fun legacyProvider(): S3BlobStorageProvider? {
        if (!S3Config.isComplete()) return null
        val cfg = BlobStorageRepoConfig(
            index = 0,
            name = "s3",
            type = BlobStorageType.S3,
            url = S3Config.endpoint,
            accessKey = S3Config.accessKey,
            secretKey = S3Config.secretKey,
            bucket = S3Config.bucketName,
            region = S3Config.region.id(),
            disableChecksums = S3Config.disableChecksums,
            disableChunkedEncoding = S3Config.disableChunkedEncoding,
        )
        return S3BlobStorageProvider(cfg)
    }
}
