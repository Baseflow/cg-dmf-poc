// SPDX-License-Identifier: EUPL-1.2
// Copyright (C) 2026 Gemeente Utrecht
package com.baseflow.shared.services

import com.baseflow.shared.entities.settings.ApiConnectionSettingEntity
import com.baseflow.shared.entities.settings.ApiConnectionType
import com.baseflow.shared.tooling.AllTables
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.util.UUID
import kotlin.test.*
import kotlin.time.Clock

class OpenZaakMigratorTest {

    @BeforeTest
    fun setUp() {
        val dbName = "openzaak_migrator_${UUID.randomUUID()}"
        Database.connect(
            "jdbc:h2:mem:$dbName;DB_CLOSE_DELAY=-1;",
            driver = "org.h2.Driver",
            user = "root",
            password = "",
        )
        transaction { AllTables.createMissing() }
    }

    @Test
    fun `migrate inserts ZTC and ZRC entries when none exist`() {
        OpenZaakMigrator.migrate(
            endpoint = "https://openzaak.example.com",
            clientId = "test-client",
            clientSecret = "test-secret",
        )

        transaction {
            val all = ApiConnectionSettingEntity.all().toList()
            assertEquals(2, all.size)

            val ztc = all.first { it.apiType == ApiConnectionType.ZTC.value }
            assertEquals("openzaak-ztc", ztc.name)
            assertEquals("https://openzaak.example.com/catalogi/api/v1", ztc.baseUrl)
            assertEquals("test-client", ztc.clientId)
            assertEquals("test-secret", ztc.clientSecret)
            assertTrue(ztc.enabled)
            assertTrue(ztc.readonly)

            val zrc = all.first { it.apiType == ApiConnectionType.ZRC.value }
            assertEquals("openzaak-zrc", zrc.name)
            assertEquals("https://openzaak.example.com/zaken/api/v1", zrc.baseUrl)
        }
    }

    @Test
    fun `migrate updates existing entries matched by baseUrl and apiType`() {
        transaction {
            ApiConnectionSettingEntity.new {
                name = "existing-ztc"
                baseUrl = "https://openzaak.example.com/catalogi/api/v1"
                clientId = "old-client"
                clientSecret = "old-secret"
                apiType = ApiConnectionType.ZTC.value
                validationEnabled = true
                enabled = true
                readonly = false
                updatedAt = Clock.System.now()
            }
        }

        OpenZaakMigrator.migrate(
            endpoint = "https://openzaak.example.com",
            clientId = "new-client",
            clientSecret = "new-secret",
        )

        transaction {
            val ztcEntries = ApiConnectionSettingEntity.all().filter { it.apiType == ApiConnectionType.ZTC.value }
            assertEquals(1, ztcEntries.size)
            val ztc = ztcEntries.first()
            assertEquals("existing-ztc", ztc.name)
            assertEquals("new-client", ztc.clientId)
            assertEquals("new-secret", ztc.clientSecret)
            assertTrue(ztc.readonly)
        }
    }

    @Test
    fun `migrate does not create duplicate when called twice for same endpoint`() {
        repeat(2) {
            OpenZaakMigrator.migrate(
                endpoint = "https://openzaak.example.com",
                clientId = "test-client",
                clientSecret = "test-secret",
            )
        }

        transaction {
            val ztcEntries = ApiConnectionSettingEntity.all().filter { it.apiType == ApiConnectionType.ZTC.value }
            assertEquals(1, ztcEntries.size)
        }
    }

    @Test
    fun `migrate trims trailing slash from endpoint`() {
        OpenZaakMigrator.migrate(
            endpoint = "https://openzaak.example.com/",
            clientId = "test-client",
            clientSecret = "test-secret",
        )

        transaction {
            val ztc = ApiConnectionSettingEntity.all().first { it.apiType == ApiConnectionType.ZTC.value }
            assertEquals("https://openzaak.example.com/catalogi/api/v1", ztc.baseUrl)
        }
    }
}
