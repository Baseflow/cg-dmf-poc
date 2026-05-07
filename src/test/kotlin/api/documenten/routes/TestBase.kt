// SPDX-License-Identifier: EUPL-1.2
// Copyright (C) 2026 Gemeente Utrecht
package com.baseflow.api.documenten.routes

import com.baseflow.api.admin.adminModule
import com.baseflow.api.apiJsonConfig
import com.baseflow.api.documenten.documentenApiModule
import com.baseflow.config.appModule
import com.baseflow.services.StorageService
import com.baseflow.tooling.AllTables
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.*
import io.mockk.every
import io.mockk.mockk
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.koin.dsl.module
import org.koin.ksp.generated.defaultModule
import org.koin.ktor.plugin.Koin
import java.util.UUID
import java.util.concurrent.CompletableFuture
import kotlin.test.BeforeTest

open class TestBase(dbNamePrefix: String) {
    val dbName = "${dbNamePrefix}_${UUID.randomUUID()}"

    /** Exposed so individual tests can assert on upload/download calls. */
    lateinit var mockStorageService: StorageService
        protected set

    fun connectDb() {
        Database.connect(
            "jdbc:h2:mem:$dbName;DB_CLOSE_DELAY=-1;",
            driver = "org.h2.Driver",
            user = "root",
            password = "",
        )
    }

    @BeforeTest
    open fun beforeTest() {
        connectDb()
        transaction {
            AllTables.createMissing()
        }
    }

    fun Application.setup() {
        connectDb()

        mockStorageService = mockk<StorageService>(relaxed = true).also {
            every { it.uploadFile(any<String>(), any<ByteArray>(), anyNullable()) } returns Unit
            every { it.uploadFile(any<String>(), any<java.io.InputStream>(), any<Long>(), anyNullable()) } returns Unit
            every { it.downloadFileTo(any(), any(), anyNullable()) } returns CompletableFuture.completedFuture(null)
        }

        install(Koin) {
            allowOverride(true)
            modules(appModule)
            modules(defaultModule)
            // Override the real StorageService (which requires S3) with a no-op mock
            // so route tests never attempt to connect to S3.
            modules(
                module {
                    single<StorageService> { mockStorageService }
                },
            )
        }
        install(ContentNegotiation) {
            json(apiJsonConfig())
        }
        documentenApiModule(useAuthentication = false)
        adminModule(useAuthentication = false)
    }
}
