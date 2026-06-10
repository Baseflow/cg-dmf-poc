// SPDX-License-Identifier: EUPL-1.2
// Copyright (C) 2026 Gemeente Utrecht
package com.baseflow.shared.tooling

import org.flywaydb.core.Flyway
import java.util.UUID
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FlywayRepairCommandTest {

    private lateinit var jdbcUrl: String
    private lateinit var flyway: Flyway

    @BeforeTest
    fun setUp() {
        jdbcUrl = "jdbc:h2:mem:repair_test_${UUID.randomUUID()};DB_CLOSE_DELAY=-1"
        flyway = Flyway.configure()
            .dataSource(jdbcUrl, "sa", "")
            .locations("classpath:db/repair-test-migration")
            .load()
        flyway.migrate()
    }

    private fun corruptChecksum() {
        val ds = flyway.configuration.dataSource
        ds.connection.use { conn ->
            conn.createStatement().execute(
                """UPDATE "flyway_schema_history" SET "checksum" = -1 WHERE "version" = '1'""",
            )
        }
    }

    private fun isChecksumValid(): Boolean = flyway.info().all().first { it.version?.version == "1" }.isChecksumMatching

    @Test
    fun `no failing migrations - reports nothing to repair`() {
        assertTrue(isChecksumValid())
        // Smoke-test: repair on a clean DB should report nothing and leave checksums intact
        runRepairCommand(flyway, arrayOf("repair"), input(""))
        assertTrue(isChecksumValid())
    }

    @Test
    fun `force flag repairs without prompting`() {
        corruptChecksum()
        assertFalse(isChecksumValid())

        runRepairCommand(flyway, arrayOf("repair", "--force"), input(""))

        assertTrue(isChecksumValid())
    }

    @Test
    fun `interactive yes repairs checksum`() {
        corruptChecksum()
        assertFalse(isChecksumValid())

        runRepairCommand(flyway, arrayOf("repair"), input("y"))

        assertTrue(isChecksumValid())
    }

    @Test
    fun `interactive no leaves checksum corrupted`() {
        corruptChecksum()
        assertFalse(isChecksumValid())

        runRepairCommand(flyway, arrayOf("repair"), input("n"))

        assertFalse(isChecksumValid())
    }

    @Test
    fun `interactive empty input leaves checksum corrupted`() {
        corruptChecksum()
        assertFalse(isChecksumValid())

        runRepairCommand(flyway, arrayOf("repair"), input(""))

        assertFalse(isChecksumValid())
    }

    private fun input(text: String): () -> String? {
        val lines = text.lines().iterator()
        return { if (lines.hasNext()) lines.next() else null }
    }
}

class FlywayCleanCommandTest {

    private lateinit var flyway: Flyway

    @BeforeTest
    fun setUp() {
        val jdbcUrl = "jdbc:h2:mem:clean_test_${UUID.randomUUID()};DB_CLOSE_DELAY=-1"
        flyway = Flyway.configure()
            .dataSource(jdbcUrl, "sa", "")
            .cleanDisabled(false)
            .locations("classpath:db/repair-test-migration")
            .load()
        flyway.migrate()
    }

    private fun tableExists(): Boolean {
        val ds = flyway.configuration.dataSource
        return ds.connection.use { conn ->
            val rs = conn.metaData.getTables(null, null, "REPAIR_TEST", null)
            rs.next()
        }
    }

    @Test
    fun `force flag cleans without prompting`() {
        assertTrue(tableExists())

        runCleanCommand(flyway, arrayOf("clean", "--force"), input(""))

        assertFalse(tableExists())
    }

    @Test
    fun `interactive yes cleans database`() {
        assertTrue(tableExists())

        runCleanCommand(flyway, arrayOf("clean"), input("y"))

        assertFalse(tableExists())
    }

    @Test
    fun `interactive no leaves database intact`() {
        assertTrue(tableExists())

        runCleanCommand(flyway, arrayOf("clean"), input("n"))

        assertTrue(tableExists())
    }

    @Test
    fun `interactive empty input leaves database intact`() {
        assertTrue(tableExists())

        runCleanCommand(flyway, arrayOf("clean"), input(""))

        assertTrue(tableExists())
    }

    private fun input(text: String): () -> String? {
        val lines = text.lines().iterator()
        return { if (lines.hasNext()) lines.next() else null }
    }
}
