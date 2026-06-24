// SPDX-License-Identifier: EUPL-1.2
// Copyright (C) 2026 Gemeente Utrecht
package com.baseflow.shared.services

import com.baseflow.shared.entities.settings.BlobStorageRepositorySettingEntity
import com.baseflow.shared.tooling.AllTables
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.util.UUID
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Clock

class BlobStorageRegistrarTest {

    @BeforeTest
    fun setUp() {
        Database.connect(
            "jdbc:h2:mem:registrar_test_${UUID.randomUUID()};DB_CLOSE_DELAY=-1;",
            driver = "org.h2.Driver",
            user = "root",
            password = "",
        )
        transaction { AllTables.createMissing() }
    }

    @AfterTest
    fun tearDown() {
        BlobStorageRegistrar.resetForTesting()
    }

    private fun insertEntity(
        name: String = "test-repo",
        extraProperties: String = "{}",
        isDefault: Boolean = false,
        enabled: Boolean = true,
    ) = transaction {
        BlobStorageRepositorySettingEntity.new {
            repoName = name
            storageType = "S3"
            url = "http://localhost:9000"
            accessKey = "access-key"
            secretKey = "secret-key"
            bucket = "test-bucket"
            region = null
            this.extraProperties = extraProperties
            this.isDefault = isDefault
            this.enabled = enabled
            storageAccountName = null
            createdAt = Clock.System.now()
            updatedAt = Clock.System.now()
        }
    }

    // -----------------------------------------------------------------------
    // loadConfigsFromDatabase — DB startup path
    // -----------------------------------------------------------------------

    @Test
    fun `loadConfigsFromDatabase returns empty list when no entities`() {
        val configs = transaction { BlobStorageRegistrar.loadConfigsFromDatabase() }
        assertTrue(configs.isEmpty())
    }

    @Test
    fun `loadConfigsFromDatabase sets disableChecksums false when not in extraProperties`() {
        insertEntity(extraProperties = "{}")
        val configs = transaction { BlobStorageRegistrar.loadConfigsFromDatabase() }
        assertEquals(1, configs.size)
        assertFalse(configs[0].disableChecksums)
    }

    @Test
    fun `loadConfigsFromDatabase sets disableChunkedEncoding false when not in extraProperties`() {
        insertEntity(extraProperties = "{}")
        val configs = transaction { BlobStorageRegistrar.loadConfigsFromDatabase() }
        assertEquals(1, configs.size)
        assertFalse(configs[0].disableChunkedEncoding)
    }

    @Test
    fun `loadConfigsFromDatabase picks up DISABLE_CHECKSUMS true from extraProperties`() {
        insertEntity(extraProperties = """{"DISABLE_CHECKSUMS":"true"}""")
        val configs = transaction { BlobStorageRegistrar.loadConfigsFromDatabase() }
        assertEquals(1, configs.size)
        assertTrue(configs[0].disableChecksums)
    }

    @Test
    fun `loadConfigsFromDatabase picks up DISABLE_CHUNKED_ENCODING true from extraProperties`() {
        insertEntity(extraProperties = """{"DISABLE_CHUNKED_ENCODING":"true"}""")
        val configs = transaction { BlobStorageRegistrar.loadConfigsFromDatabase() }
        assertEquals(1, configs.size)
        assertTrue(configs[0].disableChunkedEncoding)
    }

    @Test
    fun `loadConfigsFromDatabase picks up both flags when both are true`() {
        insertEntity(extraProperties = """{"DISABLE_CHECKSUMS":"true","DISABLE_CHUNKED_ENCODING":"true"}""")
        val configs = transaction { BlobStorageRegistrar.loadConfigsFromDatabase() }
        assertEquals(1, configs.size)
        assertTrue(configs[0].disableChecksums)
        assertTrue(configs[0].disableChunkedEncoding)
    }

    @Test
    fun `loadConfigsFromDatabase treats DISABLE_CHECKSUMS false explicitly`() {
        insertEntity(extraProperties = """{"DISABLE_CHECKSUMS":"false","DISABLE_CHUNKED_ENCODING":"true"}""")
        val configs = transaction { BlobStorageRegistrar.loadConfigsFromDatabase() }
        assertEquals(1, configs.size)
        assertFalse(configs[0].disableChecksums)
        assertTrue(configs[0].disableChunkedEncoding)
    }

    @Test
    fun `loadConfigsFromDatabase skips entity with null accessKey`() {
        transaction {
            BlobStorageRepositorySettingEntity.new {
                repoName = "no-key-repo"
                storageType = "S3"
                url = "http://localhost:9000"
                accessKey = null
                secretKey = null
                bucket = "bucket"
                region = null
                extraProperties = "{}"
                isDefault = false
                enabled = true
                storageAccountName = null
                createdAt = Clock.System.now()
                updatedAt = Clock.System.now()
            }
        }
        val configs = transaction { BlobStorageRegistrar.loadConfigsFromDatabase() }
        assertTrue(configs.isEmpty())
    }

    @Test
    fun `loadConfigsFromDatabase handles malformed extraProperties JSON gracefully`() {
        insertEntity(extraProperties = "not-valid-json")
        val configs = transaction { BlobStorageRegistrar.loadConfigsFromDatabase() }
        assertEquals(1, configs.size)
        assertFalse(configs[0].disableChecksums)
        assertFalse(configs[0].disableChunkedEncoding)
    }

    // -----------------------------------------------------------------------
    // initialise — end-to-end DB load and provider registration
    // -----------------------------------------------------------------------

    @Test
    fun `initialise registers provider from DB when no env config`() {
        insertEntity(
            extraProperties = """{"DISABLE_CHECKSUMS":"true","DISABLE_CHUNKED_ENCODING":"true"}""",
            isDefault = true,
        )
        BlobStorageRegistrar.initialise()
        assertTrue(BlobStorageRegistrar.defaultProvider() != null)
        assertEquals("test-repo", BlobStorageRegistrar.defaultProvider()?.name)
    }

    @Test
    fun `initialise with DISABLE_CHUNKED_ENCODING true builds provider without throwing`() {
        insertEntity(
            name = "chunked-disabled-repo",
            extraProperties = """{"DISABLE_CHUNKED_ENCODING":"true"}""",
            isDefault = true,
        )
        BlobStorageRegistrar.initialise()
        assertFalse(BlobStorageRegistrar.defaultProvider() == null)
    }

    @Test
    fun `initialise with both flags true builds provider without throwing`() {
        insertEntity(
            name = "both-disabled-repo",
            extraProperties = """{"DISABLE_CHECKSUMS":"true","DISABLE_CHUNKED_ENCODING":"true"}""",
            isDefault = true,
        )
        BlobStorageRegistrar.initialise()
        assertFalse(BlobStorageRegistrar.defaultProvider() == null)
    }
}
