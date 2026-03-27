// SPDX-License-Identifier: EUPL-1.2
// Copyright (C) 2026 Gemeente Utrecht
package com.baseflow.api.routes

import com.baseflow.api.documentenApiModule
import com.baseflow.config.OpenZaakConfig
import com.baseflow.config.appModule
import com.baseflow.tooling.AllTables
import io.ktor.server.application.Application
import io.ktor.server.application.install
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.koin.ksp.generated.defaultModule
import org.koin.ktor.plugin.Koin
import java.util.UUID
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
    open fun beforeTest() {
        connectDb()
        transaction {
            AllTables.createMissing()
        }
    }

    fun Application.setup() {
        connectDb()

        install(Koin) {
            modules(appModule)
            modules(defaultModule)
        }

        val openZaakConfig = OpenZaakConfig(validationEnabled = false)
        documentenApiModule(useAuthentication = false)
    }
}
