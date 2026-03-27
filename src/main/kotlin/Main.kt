// SPDX-License-Identifier: EUPL-1.2
// Copyright (C) 2025-2026 Gemeente Utrecht
package com.baseflow

import com.baseflow.api.documentenApiModule
import com.baseflow.api.healthModule
import com.baseflow.api.openApiModule
import com.baseflow.config.ApplicationConfig
import com.baseflow.config.BlobStorageConfig
import com.baseflow.config.DatabaseConfig
import com.baseflow.config.NotificationConfig
import com.baseflow.config.S3Config
import com.baseflow.config.appModule
import com.baseflow.config.authenticationModule
import com.baseflow.services.BlobStorageRegistrar
import com.baseflow.services.NotificationService
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.utils.io.ExperimentalKtorApi
import kotlinx.coroutines.runBlocking
import org.flywaydb.core.Flyway
import org.jetbrains.exposed.v1.jdbc.Database
import org.koin.ksp.generated.defaultModule
import org.koin.ktor.plugin.Koin

fun main() {
    ApplicationConfig.printConfig()
    DatabaseConfig.printConfig()

    // Only initialize/print S3 configuration when appropriate:
    // - If no BLOB_STORAGE_* env vars are present, we assume legacy S3-only mode and require S3.
    // - If S3_SECRET_KEY is present, S3 is explicitly configured (even in multi-repo setups).
    val env = System.getenv()
    val hasBlobStorageEnv = env.keys.any { it.startsWith("BLOB_STORAGE_") }
    val hasS3Secret = env["S3_SECRET_KEY"] != null
    if (!hasBlobStorageEnv || hasS3Secret) {
        S3Config.printConfig()
    } else {
        BlobStorageConfig.printConfig()
    }
    Database.connect(
        url = DatabaseConfig.url,
        driver = DatabaseConfig.driver,
        user = DatabaseConfig.user,
        password = DatabaseConfig.password,
    )

    // apply migrations
    Flyway.configure()
        .dataSource(DatabaseConfig.url, DatabaseConfig.user, DatabaseConfig.password)
        .load()
        .migrate()

    // Register blob storage repositories from env vars into database
    BlobStorageRegistrar.initialise()

    // Ensure notification kanaal exists
    NotificationConfig.printConfig()

    runBlocking {
        NotificationService.ensureKanaalExists()
    }

    embeddedServer(Netty, port = ApplicationConfig.port) {
        module()
    }.start(wait = true)
}

@OptIn(ExperimentalKtorApi::class)
fun Application.module() {
    // Install Koin for dependency injection
    install(Koin) {
        modules(appModule)
        modules(defaultModule)
    }

    authenticationModule()
    helloWorldModule() // Keep for basic health check at /
    healthModule() // Health endpoints at /health/liveness and /health/readiness
    documentenApiModule() // Documenten API at /documenten/api/v1
    openApiModule() // OpenAPI spec at /openapi.json and Swagger UI at /docs
}
