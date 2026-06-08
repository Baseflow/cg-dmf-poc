// SPDX-License-Identifier: EUPL-1.2
// Copyright (C) 2026 Gemeente Utrecht
package com.baseflow.settings.api.routes

import com.baseflow.settings.api.settingsModule
import com.baseflow.shared.api.apiJsonConfig
import com.baseflow.shared.entities.settings.DmfSettingEntity
import com.baseflow.shared.tooling.AllTables
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.*
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.exposed.v1.jdbc.Database
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
            if (DmfSettingEntity.findById(DmfSettingEntity.SINGLETON_ID) == null) {
                DmfSettingEntity.new(DmfSettingEntity.SINGLETON_ID) {
                    triggerSizeBytes = 4_294_967_296L
                    chunkSizeBytes = 3_221_225_472L
                    validationEnabled = true
                    updatedAt = Clock.System.now().toLocalDateTime(TimeZone.UTC)
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
