// SPDX-License-Identifier: EUPL-1.2
// Copyright (C) 2025-2026 Gemeente Utrecht
package com.baseflow.services

import kotlinx.serialization.Serializable
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.koin.core.annotation.Singleton
import org.slf4j.LoggerFactory

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
open class HealthCheckService(private val storageService: IStorageService) {

    private val logger = LoggerFactory.getLogger(HealthCheckService::class.java)

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

    open fun checkStorage(): StorageStatus = storageService.checkHealth()
}
