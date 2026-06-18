// SPDX-License-Identifier: EUPL-1.2
// Copyright (C) 2026 Gemeente Utrecht
package com.baseflow.settings.api.routes

import com.baseflow.settings.api.settingsModule
import com.baseflow.shared.api.apiJsonConfig
import com.baseflow.shared.entities.settings.DmfSettingsTable
import com.baseflow.shared.tooling.AllTables
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.*
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.util.UUID
import kotlin.test.BeforeTest
import kotlin.time.Clock

open class SettingsTestBase(dbNamePrefix: String) {
    private val dbName = "${dbNamePrefix}_${UUID.randomUUID()}"

    private fun connectDb() {
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
        transaction {
            val existingKeys = DmfSettingsTable.selectAll().map { it[DmfSettingsTable.key] }.toSet()
            val seeds = mapOf(
                "trigger_size_bytes" to ("int" to "4294967296"),
                "chunk_size_bytes" to ("int" to "3221225472"),
                "validation_enabled" to ("boolean" to "true"),
            )
            val now = Clock.System.now().toLocalDateTime(TimeZone.UTC)
            for ((k, tv) in seeds) {
                if (k !in existingKeys) {
                    DmfSettingsTable.insert {
                        it[key] = k
                        it[type] = tv.first
                        it[value] = tv.second
                        it[updatedAt] = now
                    }
                }
            }
        }
    }

    fun Application.setup() {
        connectDb()
        install(ContentNegotiation) {
            json(apiJsonConfig())
        }
        settingsModule(useAuthentication = false)
    }
}
