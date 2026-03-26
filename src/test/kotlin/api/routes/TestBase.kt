// SPDX-License-Identifier: EUPL-1.2
// Copyright (C) 2026 Gemeente Utrecht
package com.baseflow.api.routes

import com.baseflow.api.documentenApiModule
import com.baseflow.config.OpenZaakConfig
import com.baseflow.config.appModule
import com.baseflow.services.IStorageService
import com.baseflow.tooling.AllTables
import io.mockk.every
import io.mockk.mockk
import io.ktor.server.application.Application
import io.ktor.server.application.install
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

    fun connectDb() {
        Database.connect(
            "jdbc:h2:mem:$dbName;DB_CLOSE_DELAY=-1;",
            driver = "org.h2.Driver",
            user = "root",
            password = "",
        )
    }

    @BeforeTest
    fun beforeTest() {
        connectDb()
        transaction {
            AllTables.createMissing()
        }
    }

    /**
     * Returns a relaxed [IStorageService] mock that silently accepts uploads
     * and returns an immediately-completed future for downloads.
     */
    private fun noOpStorageService(): IStorageService = mockk<IStorageService>(relaxed = true).also {
        every { it.uploadFile(any(), any()) } returns Unit
        every { it.downloadFileTo(any(), any()) } returns CompletableFuture.completedFuture(null)
    }

    fun Application.setup() {
        connectDb()

        install(Koin) {
            allowOverride(true)
            // Register appModule + defaultModule first, then the mock module last so it wins.
            modules(appModule)
            modules(defaultModule)
            modules(module { single<IStorageService> { noOpStorageService() } })
        }

        val openZaakConfig = OpenZaakConfig(validationEnabled = false)
        documentenApiModule(useAuthentication = false, openZaakConfig = openZaakConfig)
    }
}
