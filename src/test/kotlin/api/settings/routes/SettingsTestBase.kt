// SPDX-License-Identifier: EUPL-1.2
// Copyright (C) 2026 Gemeente Utrecht
package com.baseflow.api.settings.routes

import com.baseflow.api.apiJsonConfig
import com.baseflow.api.settings.settingsModule
import com.baseflow.config.appModule
import com.baseflow.entities.settings.DmfSettingEntity
import com.baseflow.services.StorageService
import com.baseflow.tooling.AllTables
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.*
import io.mockk.mockk
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.koin.dsl.module
import org.koin.ksp.generated.defaultModule
import org.koin.ktor.plugin.Koin
import java.util.UUID
import kotlin.test.BeforeTest

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
                    triggerSizeBytes  = 4_294_967_296L
                    chunkSizeBytes    = 3_221_225_472L
                    validationEnabled = true
                    updatedAt         = Clock.System.now().toLocalDateTime(TimeZone.UTC)
                }
            }
        }
    }

    fun Application.setup() {
        connectDb()
        install(Koin) {
            allowOverride(true)
            modules(appModule)
            modules(defaultModule)
            modules(module { single<StorageService> { mockk(relaxed = true) } })
        }
        install(ContentNegotiation) {
            json(apiJsonConfig())
        }
        settingsModule(useAuthentication = false)
    }
}
