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
import org.flywaydb.core.api.MigrationState
import org.jetbrains.exposed.v1.jdbc.Database
import org.koin.ksp.generated.defaultModule
import org.koin.ktor.plugin.Koin

fun main() {
    ApplicationConfig.printConfig()
    DatabaseConfig.printConfig()

    // Decide which storage configuration to initialize/print based on the merged config layer:
    // - If BlobStorageConfig has repositories configured, we are in blob storage mode.
    // - Otherwise, we assume legacy S3-only mode and require S3 configuration.
    if (BlobStorageConfig.repositories.isNotEmpty()) {
        BlobStorageConfig.printConfig()
    } else {
        S3Config.printConfig()
    }
    Database.connect(
        url = DatabaseConfig.url,
        driver = DatabaseConfig.driver,
        user = DatabaseConfig.user,
        password = DatabaseConfig.password,
    )

    // apply migrations
    val flyway = Flyway.configure()
        .dataSource(DatabaseConfig.url, DatabaseConfig.user, DatabaseConfig.password)
        .load()

    // Targeted repair: V7 was amended to remove the pgcrypto dependency.
    // Systems that already applied the original V7 will have a checksum
    // mismatch. We detect this specifically and repair only when needed.
    // TODO: Remove this block once all environments have been upgraded past V10.
    repairV7ChecksumIfNeeded(flyway)

    flyway.migrate()

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

    // JSON serialization — available to all modules,
    install(ContentNegotiation) {
        json(apiJsonConfig())
    }

    authenticationModule()
    helloWorldModule() // Keep for basic health check at /
    healthModule() // Health endpoints at /health/liveness and /health/readiness
    documentenApiModule() // Documenten API at /documenten/api/v1
    adminModule() // Admin API at /admin
    wopiApiModule() // Wopi API at /wopi/api/v1
    openApiModule() // OpenAPI spec at /openapi.json and Swagger UI at /docs
}

/**
 * Targeted checksum repair for V7 (BlobStorageRepositories).
 *
 * V7 was amended to remove the pgcrypto extension dependency so that systems
 * still on V6 can upgrade without requiring pgcrypto. Systems that already
 * applied the original V7 will have a stale checksum in flyway_schema_history.
 *
 * This function detects if V7 specifically has a checksum mismatch and calls
 * [Flyway.repair] only in that case. Repair updates **all** checksums, but
 * since V7 is the only amended migration, no other checksums will change.
 *
 * We use [MigrationInfo.isChecksumMatching] to detect mismatches, because
 * the [MigrationState] enum does not expose a dedicated "checksum mismatch"
 * state for versioned migrations — the mismatch is only surfaced as a
 * validation failure during [Flyway.migrate].
 *
 * This is a no-op when checksums already match and is safe to leave in place
 * until all environments have been upgraded.
 *
 * TODO: Remove once all environments have been upgraded past V10.
 */
private fun repairV7ChecksumIfNeeded(flyway: Flyway) {
    val v7Info = flyway.info().all().firstOrNull { it.version?.version == "7" }
        ?: return // V7 not yet applied — nothing to repair

    if (!v7Info.isChecksumMatching) {
        println("V7 checksum mismatch detected (pgcrypto removal). Repairing...")
        println("  Applied checksum : ${v7Info.appliedChecksum}")
        println("  Resolved checksum: ${v7Info.resolvedChecksum}")
        flyway.repair()
        println("V7 checksum repaired.")
    }
}
