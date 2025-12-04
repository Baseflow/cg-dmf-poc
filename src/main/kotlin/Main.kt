// SPDX-License-Identifier: EUPL-1.2
// Copyright (C) 2025 Gemeente Utrecht
package com.baseflow

import com.baseflow.api.documentenApiModule
import com.baseflow.api.healthModule
import com.baseflow.services.StorageService
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import org.flywaydb.core.Flyway
import org.jetbrains.exposed.v1.jdbc.Database


fun main() {
    // Initialize Exposed database connection
    val dbUrl = System.getenv("DB_URL") ?: "jdbc:postgresql://localhost:5432/documenten"
    val dbUser = System.getenv("DB_USER") ?: "documenten"
    val dbPassword = System.getenv("DB_PASSWORD") ?: "documenten"
    val dbDriver = "org.postgresql.Driver"
    Database.connect(
        url = dbUrl,
        driver = dbDriver,
        user = dbUser,
        password = dbPassword
    )


    // apply migrations
    val flyway = Flyway.configure()
        .dataSource(dbUrl, dbUser, dbPassword)
        .sqlMigrationPrefix("V")
        .repeatableSqlMigrationPrefix("R")
        .sqlMigrationSeparator("__")
        .sqlMigrationSuffixes(".sql")
        .load()
    flyway.migrate()

    val storageService = StorageService()
    storageService.printConfig()

    embeddedServer(Netty, port = 8080) {
        helloWorldModule()      // Keep for basic health check at /
        healthModule()          // Health endpoints at /health/liveness and /health/readiness
        documentenApiModule()   // Documenten API at /documenten/api/v1
    }.start(wait = true)
}