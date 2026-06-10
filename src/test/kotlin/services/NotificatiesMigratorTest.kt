// SPDX-License-Identifier: EUPL-1.2
// Copyright (C) 2026 Gemeente Utrecht
package com.baseflow.shared.services

import com.baseflow.shared.entities.settings.ApiConnectionSettingEntity
import com.baseflow.shared.entities.settings.ApiConnectionType
import com.baseflow.shared.tooling.AllTables
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.util.UUID
import kotlin.test.*
import kotlin.time.Clock

class NotificatiesMigratorTest {

    @BeforeTest
    fun setUp() {
        val dbName = "notificaties_migrator_${UUID.randomUUID()}"
        Database.connect(
            "jdbc:h2:mem:$dbName;DB_CLOSE_DELAY=-1;",
            driver = "org.h2.Driver",
            user = "root",
            password = "",
        )
        transaction { AllTables.createMissing() }
    }

    @Test
    fun `migrate inserts NRC entry when none exists`() {
        NotificatiesMigrator.migrate(
            url = "https://notificaties.example.com",
            clientId = "test-client",
            clientSecret = "test-secret",
        )

        transaction {
            val all = ApiConnectionSettingEntity.all().toList()
            assertEquals(1, all.size)
            val nrc = all.first()
            assertEquals("open-notificaties", nrc.name)
            assertEquals("https://notificaties.example.com", nrc.baseUrl)
            assertEquals("test-client", nrc.clientId)
            assertEquals("test-secret", nrc.clientSecret)
            assertEquals(ApiConnectionType.NRC.value, nrc.apiType)
            assertTrue(nrc.enabled)
            assertTrue(nrc.readonly)
            assertFalse(nrc.validationEnabled)
        }
    }

    @Test
    fun `migrate updates existing NRC entry matched by baseUrl`() {
        transaction {
            ApiConnectionSettingEntity.new {
                name = "existing-nrc"
                baseUrl = "https://notificaties.example.com"
                clientId = "old-client"
                clientSecret = "old-secret"
                apiType = ApiConnectionType.NRC.value
                validationEnabled = false
                enabled = true
                readonly = false
                updatedAt = Clock.System.now().toLocalDateTime(TimeZone.UTC)
            }
        }

        NotificatiesMigrator.migrate(
            url = "https://notificaties.example.com",
            clientId = "new-client",
            clientSecret = "new-secret",
        )

        transaction {
            val all = ApiConnectionSettingEntity.all().toList()
            assertEquals(1, all.size)
            val nrc = all.first()
            assertEquals("existing-nrc", nrc.name)
            assertEquals("new-client", nrc.clientId)
            assertEquals("new-secret", nrc.clientSecret)
            assertTrue(nrc.readonly)
        }
    }

    @Test
    fun `migrate does not create duplicate when called twice for same url`() {
        repeat(2) {
            NotificatiesMigrator.migrate(
                url = "https://notificaties.example.com",
                clientId = "test-client",
                clientSecret = "test-secret",
            )
        }

        transaction {
            assertEquals(1, ApiConnectionSettingEntity.all().count())
        }
    }

    @Test
    fun `migrate trims trailing slash from url`() {
        NotificatiesMigrator.migrate(
            url = "https://notificaties.example.com/",
            clientId = "test-client",
            clientSecret = "test-secret",
        )

        transaction {
            val nrc = ApiConnectionSettingEntity.all().first()
            assertEquals("https://notificaties.example.com", nrc.baseUrl)
        }
    }

    @Test
    fun `migrate matches by url regardless of trailing slash`() {
        transaction {
            ApiConnectionSettingEntity.new {
                name = "existing-nrc"
                baseUrl = "https://notificaties.example.com/"
                clientId = "old-client"
                clientSecret = "old-secret"
                apiType = ApiConnectionType.NRC.value
                validationEnabled = false
                enabled = true
                readonly = false
                updatedAt = Clock.System.now().toLocalDateTime(TimeZone.UTC)
            }
        }

        NotificatiesMigrator.migrate(
            url = "https://notificaties.example.com",
            clientId = "new-client",
            clientSecret = "new-secret",
        )

        transaction {
            val all = ApiConnectionSettingEntity.all().toList()
            assertEquals(1, all.size)
            assertEquals("new-client", all.first().clientId)
        }
    }
}
