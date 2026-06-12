// SPDX-License-Identifier: EUPL-1.2
// Copyright (C) 2026 Gemeente Utrecht
package com.baseflow.shared.services

import com.baseflow.shared.entities.settings.ApplicationSettingEntity
import com.baseflow.shared.entities.settings.ApplicationSettingsTable
import com.baseflow.shared.tooling.AllTables
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.util.UUID
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock

class ApplicationCredentialRegistrarTest {

    @BeforeTest
    fun setup() {
        Database.connect(
            "jdbc:h2:mem:registrar_${UUID.randomUUID()};DB_CLOSE_DELAY=-1;",
            driver = "org.h2.Driver",
            user = "root",
            password = "",
        )
        transaction { AllTables.createMissing() }
        ApplicationCredentialRegistrar.resetForTesting()
    }

    @AfterTest
    fun teardown() {
        transaction { SchemaUtils.drop(*AllTables.tables.reversedArray()) }
        ApplicationCredentialRegistrar.resetForTesting()
    }

    // ── cache population ──────────────────────────────────────────────────────

    @Test
    fun `initialise populates cache from env credentials`() {
        ApplicationCredentialRegistrar.initialise(mapOf("my-client" to "my-secret"))

        assertEquals("my-secret", ApplicationCredentialRegistrar.getSecret("my-client"))
    }

    @Test
    fun `initialise populates cache from existing DB entries`() {
        transaction {
            ApplicationSettingEntity.new {
                name = "db-app"
                clientId = "db-client"
                clientSecret = "db-secret"
                updatedAt = Clock.System.now().toLocalDateTime(TimeZone.UTC)
            }
        }

        ApplicationCredentialRegistrar.initialise(emptyMap())

        assertEquals("db-secret", ApplicationCredentialRegistrar.getSecret("db-client"))
    }

    @Test
    fun `DB secret takes precedence over env secret for the same clientId`() {
        transaction {
            ApplicationSettingEntity.new {
                name = "overlap-app"
                clientId = "shared-client"
                clientSecret = "db-secret"
                updatedAt = Clock.System.now().toLocalDateTime(TimeZone.UTC)
            }
        }

        ApplicationCredentialRegistrar.initialise(mapOf("shared-client" to "env-secret"))

        assertEquals("db-secret", ApplicationCredentialRegistrar.getSecret("shared-client"))
    }

    // ── env → DB import ───────────────────────────────────────────────────────

    @Test
    fun `initialise imports env credential into DB with readonly=true`() {
        ApplicationCredentialRegistrar.initialise(mapOf("new-client" to "new-secret"))

        val entity = transaction {
            ApplicationSettingEntity.find {
                ApplicationSettingsTable.clientId eq "new-client"
            }.firstOrNull()
        }

        assertNotNull(entity, "Expected a DB row to be created for new-client")
        assertEquals("new-client", entity.clientId)
        assertEquals("new-secret", entity.clientSecret)
        assertTrue(entity.readonly, "Imported env credentials must be readonly")
    }

    @Test
    fun `initialise sets name equal to clientId when no collision`() {
        ApplicationCredentialRegistrar.initialise(mapOf("clean-client" to "secret"))

        val entity = transaction {
            ApplicationSettingEntity.find {
                ApplicationSettingsTable.clientId eq "clean-client"
            }.firstOrNull()
        }

        assertNotNull(entity)
        assertEquals("clean-client", entity.name)
    }

    @Test
    fun `initialise skips import when clientId already exists in DB`() {
        transaction {
            ApplicationSettingEntity.new {
                name = "existing-app"
                clientId = "existing-client"
                clientSecret = null
                updatedAt = Clock.System.now().toLocalDateTime(TimeZone.UTC)
            }
        }

        ApplicationCredentialRegistrar.initialise(mapOf("existing-client" to "env-secret"))

        val count = transaction {
            ApplicationSettingEntity.find {
                ApplicationSettingsTable.clientId eq "existing-client"
            }.count()
        }

        assertEquals(1L, count, "Should not create a duplicate row for an existing clientId")
    }

    @Test
    fun `initialise generates suffixed name when clientId collides with existing name`() {
        // Insert a row whose *name* equals the incoming clientId (different clientId).
        transaction {
            ApplicationSettingEntity.new {
                name = "collision-client"
                clientId = "some-other-client"
                clientSecret = null
                updatedAt = Clock.System.now().toLocalDateTime(TimeZone.UTC)
            }
        }

        ApplicationCredentialRegistrar.initialise(mapOf("collision-client" to "secret"))

        val entity = transaction {
            ApplicationSettingEntity.find {
                ApplicationSettingsTable.clientId eq "collision-client"
            }.firstOrNull()
        }

        assertNotNull(entity, "Should still import the credential")
        assertTrue(
            entity.name != "collision-client",
            "Name must differ from the colliding existing name, got: ${entity.name}",
        )
        assertTrue(
            entity.name.startsWith("collision-client-"),
            "Suffixed name should start with the original clientId, got: ${entity.name}",
        )
    }

    @Test
    fun `initialise truncates clientId longer than 100 chars to fit name column`() {
        val longClientId = "a".repeat(150)

        ApplicationCredentialRegistrar.initialise(mapOf(longClientId to "secret"))

        val entity = transaction {
            ApplicationSettingEntity.find {
                ApplicationSettingsTable.clientId eq longClientId
            }.firstOrNull()
        }

        assertNotNull(entity, "Should import a credential with a long clientId")
        assertTrue(entity.name.length <= 100, "name must be at most 100 chars, was ${entity.name.length}")
    }

    @Test
    fun `initialise does not import same credential twice across multiple calls`() {
        ApplicationCredentialRegistrar.initialise(mapOf("idempotent-client" to "secret"))
        ApplicationCredentialRegistrar.initialise(mapOf("idempotent-client" to "secret"))

        val count = transaction {
            ApplicationSettingEntity.find {
                ApplicationSettingsTable.clientId eq "idempotent-client"
            }.count()
        }

        assertEquals(1L, count, "Second initialise call should not create a duplicate row")
    }

    @Test
    fun `initialise with empty credentials neither imports nor caches anything`() {
        ApplicationCredentialRegistrar.initialise(emptyMap())

        assertNull(ApplicationCredentialRegistrar.getSecret("nonexistent"))
        val count = transaction { ApplicationSettingEntity.all().count() }
        assertEquals(0L, count)
    }
}
