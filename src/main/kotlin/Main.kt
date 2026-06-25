// SPDX-License-Identifier: EUPL-1.2
// Copyright (C) 2025-2026 Gemeente Utrecht
package com.baseflow

import com.baseflow.documenten.api.documentenApiModule
import com.baseflow.infra.api.healthModule
import com.baseflow.infra.api.openApiModule
import com.baseflow.settings.api.settingsModule
import com.baseflow.shared.api.apiJsonConfig
import com.baseflow.shared.config.ApplicationConfig
import com.baseflow.shared.config.AuthenticationConfig
import com.baseflow.shared.config.BlobStorageConfig
import com.baseflow.shared.config.DatabaseConfig
import com.baseflow.shared.config.NotificationConfig
import com.baseflow.shared.config.WopiConfig
import com.baseflow.shared.config.authenticationModule
import com.baseflow.shared.config.dmfKoinModule
import com.baseflow.shared.services.ApplicationCredentialRegistrar
import com.baseflow.shared.services.BestandsDeelSettingsInitializer
import com.baseflow.shared.services.BlobStorageRegistrar
import com.baseflow.shared.services.NotificatiesMigrator
import com.baseflow.shared.services.NotificationService
import com.baseflow.shared.services.OpenZaakMigrator
import com.baseflow.wopi.api.wopiApiModule
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.utils.io.ExperimentalKtorApi
import kotlinx.coroutines.runBlocking
import org.flywaydb.core.Flyway
import org.flywaydb.core.api.MigrationState
import org.jetbrains.exposed.v1.jdbc.Database
import org.koin.ktor.ext.inject
import org.koin.ktor.plugin.Koin

fun main() {
    ApplicationConfig.printConfig()
    AuthenticationConfig.printConfig()
    DatabaseConfig.printConfig()

    BlobStorageConfig.printConfig()
    val dataSource = HikariDataSource(
        HikariConfig().apply {
            jdbcUrl = DatabaseConfig.url
            driverClassName = DatabaseConfig.driver
            username = DatabaseConfig.user
            password = DatabaseConfig.password
            maximumPoolSize = DatabaseConfig.poolSize
            connectionInitSql = "SET TIME ZONE 'UTC'"
        },
    )
    Database.connect(dataSource)

    // apply migrations
    val flyway = Flyway.configure()
        .dataSource(dataSource)
        .load()

    // Targeted repair: V7 was amended to remove the pgcrypto dependency.
    // Systems that already applied the original V7 will have a checksum
    // mismatch. We detect this specifically and repair only when needed.
    // TODO: Remove this block once all environments have been upgraded past V10.
    repairV7ChecksumIfNeeded(flyway)

    flyway.migrate()

    // Seed dmf_settings from BestandsDeelConfig defaults (insert-if-absent)
    BestandsDeelSettingsInitializer.initialise()

    // Register blob storage repositories from env vars into database
    BlobStorageRegistrar.initialise()

    // Upsert OPENZAAK_* env vars into api_connection_settings (ZTC + ZRC entries, idempotent)
    OpenZaakMigrator.migrateIfNeeded()
    // Upsert NOTIFICATION_API_* env vars into api_connection_settings (NRC entry, idempotent)
    NotificatiesMigrator.migrateIfNeeded()

    // Initialize ZGW client secrets cache from both env config and database
    ApplicationCredentialRegistrar.initialise()

    // Ensure notification kanaal exists
    NotificationConfig.printConfig()

    runBlocking {
        NotificationService.ensureKanaalExists()
    }

    // Raise Netty's header buffer to support large cookie/auth header values in enterprise/commercial SSO setups.
    embeddedServer(
        Netty,
        rootConfig = serverConfig {
            module(Application::module)
        },
        configure = {
            connector { port = ApplicationConfig.port }
            maxInitialLineLength = 8192
            maxHeaderSize = 32768
        },
    ).start(wait = true)
}

@OptIn(ExperimentalKtorApi::class)
fun Application.module() {
    // Install Koin for dependency injection
    install(Koin) {
        modules(dmfKoinModule)
    }

    // JSON serialization — available to all modules,
    install(ContentNegotiation) {
        json(apiJsonConfig())
    }

    // Retrieve WOPI configuration from the DI container.
    val wopiConfig: WopiConfig by inject<WopiConfig>()

    authenticationModule()
    helloWorldModule() // Keep for basic health check at /
    healthModule() // Health endpoints at /health/liveness and /health/readiness
    documentenApiModule() // Documenten API at /documenten/api/v1
    wopiApiModule(wopiConfig) // Wopi API at /wopi/api/v1
    settingsModule() // Settings API at /settings
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
 * We use [org.flywaydb.core.api.MigrationInfo.isChecksumMatching] to detect mismatches, because
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
