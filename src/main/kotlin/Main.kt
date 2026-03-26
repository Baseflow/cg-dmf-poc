// SPDX-License-Identifier: EUPL-1.2
// Copyright (C) 2025-2026 Gemeente Utrecht
package com.baseflow

import com.baseflow.api.documentenApiModule
import com.baseflow.api.healthModule
import com.baseflow.config.ApplicationConfig
import com.baseflow.config.DatabaseConfig
import com.baseflow.config.MinioConfig
import com.baseflow.config.NotificationConfig
import com.baseflow.config.StorageConfig
import com.baseflow.config.appModule
import com.baseflow.config.authenticationModule
import com.baseflow.services.NotificationService
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import kotlinx.coroutines.runBlocking
import org.flywaydb.core.Flyway
import org.jetbrains.exposed.v1.jdbc.Database
import org.koin.ksp.generated.defaultModule
import org.koin.ktor.plugin.Koin

fun main() {
    ApplicationConfig.printConfig()
    DatabaseConfig.printConfig()
    StorageConfig.printConfig()
    if (StorageConfig.backend == StorageConfig.Backend.S3) {
        MinioConfig.printConfig()
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

    // Ensure notification kanaal exists
    NotificationConfig.printConfig()

    runBlocking {
        NotificationService.ensureKanaalExists()
    }

    embeddedServer(Netty, port = ApplicationConfig.port) {
        module()
    }.start(wait = true)
}

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
}
