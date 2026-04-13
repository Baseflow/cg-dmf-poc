// SPDX-License-Identifier: EUPL-1.2
// Copyright (C) 2025-2026 Gemeente Utrecht
package com.baseflow.services

import com.baseflow.config.S3ClientFactory
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
open class HealthCheckService(
    @Suppress("unused") s3ClientFactory: S3ClientFactory, // kept for Koin graph compatibility
) {

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
     * Probes the **active default blob storage repository** for read and write
     * availability via [BlobStorageRegistrar] / [BlobStorageProvider.isHealthy].
     *
     * - **Read**: delegates to [BlobStorageProvider.isHealthy].
     * - **Write**: uploads a tiny probe object directly via the active [BlobStorageProvider]
     *   (`provider.uploadFile(...)`), then downloads it back to confirm round-trip availability,
     *   and finally deletes it (best-effort).
     *
     * When no provider is configured the check returns an error status immediately.
     */
    open fun checkStorage(): StorageStatus {
        val provider = BlobStorageRegistrar.defaultProvider()
            ?: return StorageStatus(
                status = "error",
                read = DependencyStatus(status = "error", detail = "No blob storage repository configured"),
                write = DependencyStatus(status = "error", detail = "No blob storage repository configured"),
            )

        val repoName = provider.name

        val readStatus = try {
            if (provider.isHealthy()) {
                DependencyStatus(status = "ok")
            } else {
                DependencyStatus(status = "error", detail = "Repository '$repoName' is not reachable")
            }
        } catch (e: Exception) {
            logger.warn("Storage read health check failed for '{}': {}", repoName, e.message)
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

            // Best-effort cleanup — deleteFile() is a no-op on providers that don't support it.
            try {
                provider.deleteFile(probeKey)
            } catch (_: Exception) {
                logger.warn("Storage write health check: probe cleanup failed (key={})", probeKey)
            }

            DependencyStatus(status = "ok")
        } catch (e: TimeoutException) {
            logger.warn("Storage write health check timed out for '{}': {}", repoName, e.message)
            DependencyStatus(status = "error", detail = "Storage write timed out: ${e.message}")
        } catch (e: Exception) {
            logger.warn("Storage write health check failed for '{}': {}", repoName, e.message)
            DependencyStatus(status = "error", detail = e.message)
        }

        val storageOk = readStatus.status == "ok" && writeStatus.status == "ok"
        return StorageStatus(
            status = if (storageOk) "ok" else "error",
            read = readStatus,
            write = writeStatus,
        )
    }
}
