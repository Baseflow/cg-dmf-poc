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
