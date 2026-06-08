// SPDX-License-Identifier: EUPL-1.2
// Copyright (C) 2025-2026 Gemeente Utrecht
package com.baseflow.shared.services

import kotlinx.serialization.Serializable
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.slf4j.LoggerFactory
import java.io.ByteArrayOutputStream
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

@Serializable
data class DependencyStatus(val status: String, val detail: String? = null)

@Serializable
data class StorageStatus(val status: String, val read: DependencyStatus, val write: DependencyStatus)

@Serializable
data class HealthValidateResponse(val status: String, val database: DependencyStatus, val storage: StorageStatus)

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

    open fun checkStorage(): StorageStatus {
        val provider = BlobStorageRegistrar.defaultProvider()
            ?: return StorageStatus(
                status = "error",
                read = DependencyStatus(status = "error", detail = "Blob storage is not configured"),
                write = DependencyStatus(status = "error", detail = "Blob storage is not configured"),
            )

        val readStatus = try {
            if (provider.isHealthy()) {
                DependencyStatus(status = "ok")
            } else {
                DependencyStatus(status = "error", detail = "Blob storage '${provider.name}' is not reachable")
            }
        } catch (e: Exception) {
            logger.warn("Storage read health check failed: {}", e.message)
            DependencyStatus(status = "error", detail = e.message)
        }

        val writeStatus = try {
            val probeKey = ".healthcheck-probe-${UUID.randomUUID()}"

            // Write
            provider.uploadFile(probeKey, byteArrayOf())

            // Read back — wrap in runAsync so synchronous providers (e.g. Azure) are also bounded
            CompletableFuture.runAsync {
                provider.downloadFileTo(probeKey, ByteArrayOutputStream()).get()
            }.get(PROBE_TIMEOUT_SECONDS, TimeUnit.SECONDS)

            // Best-effort cleanup
            try {
                provider.deleteFile(probeKey)
            } catch (_: Exception) {
                logger.warn("Storage write health check: probe cleanup failed (key={})", probeKey)
            }

            DependencyStatus(status = "ok")
        } catch (_: TimeoutException) {
            logger.warn("Storage write health check timed out after {}s", PROBE_TIMEOUT_SECONDS)
            DependencyStatus(status = "error", detail = "Storage write timed out after ${PROBE_TIMEOUT_SECONDS}s")
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

    companion object {
        private const val PROBE_TIMEOUT_SECONDS = 10L
    }
}
