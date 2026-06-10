// SPDX-License-Identifier: EUPL-1.2
// Copyright (C) 2026 Gemeente Utrecht
package com.baseflow.shared.tooling

import org.flywaydb.core.Flyway
import java.io.File
import java.nio.file.Files
import java.sql.DriverManager
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FlywayUndoCommandTest {

    private fun setup(): Triple<Flyway, File, String> {
        val jdbcUrl = "jdbc:h2:mem:undo_test_${UUID.randomUUID()};DB_CLOSE_DELAY=-1"
        val flyway = Flyway.configure()
            .dataSource(jdbcUrl, "sa", "")
            .locations("classpath:db/undo-test-migration")
            .load()
        flyway.migrate()

        val migrationDir = Files.createTempDirectory("undo-test-migrations").toFile().also {
            it.resolve("U1__Create_items_table.sql").writeText("DROP TABLE undo_items;")
            it.resolve("U2__Add_items_name_column.sql").writeText("ALTER TABLE undo_items DROP COLUMN name;")
        }

        return Triple(flyway, migrationDir, jdbcUrl)
    }

    private fun appliedVersions(flyway: Flyway): List<String> = flyway.info().applied().mapNotNull { it.version?.version }

    private fun columnExists(jdbcUrl: String, table: String, column: String): Boolean =
        DriverManager.getConnection(jdbcUrl, "sa", "").use { conn ->
            conn.metaData.getColumns(null, null, table.uppercase(), column.uppercase()).next()
        }

    private fun tableExists(jdbcUrl: String, table: String): Boolean = DriverManager.getConnection(jdbcUrl, "sa", "").use { conn ->
        conn.metaData.getTables(null, null, table.uppercase(), null).next()
    }

    private fun input(text: String): () -> String? {
        val lines = text.lines().iterator()
        return { if (lines.hasNext()) lines.next() else null }
    }

    private fun conn(jdbcUrl: String) = DriverManager.getConnection(jdbcUrl, "sa", "")

    @Test
    fun `no applied migrations - reports nothing to undo`() {
        val jdbcUrl = "jdbc:h2:mem:undo_empty_${UUID.randomUUID()};DB_CLOSE_DELAY=-1"
        val flyway = Flyway.configure()
            .dataSource(jdbcUrl, "sa", "")
            .locations("classpath:db/undo-test-migration")
            .load()

        runUndoCommand(flyway, arrayOf("undo"), input(""), getConnection = { conn(jdbcUrl) })

        assertTrue(flyway.info().applied().isEmpty())
    }

    @Test
    fun `undoes last migration by default with force`() {
        val (flyway, migrationDir, jdbcUrl) = setup()

        assertTrue(columnExists(jdbcUrl, "undo_items", "name"))
        assertNotNull(appliedVersions(flyway).find { it == "2" })

        runUndoCommand(flyway, arrayOf("undo", "--force"), input(""), migrationDir) { conn(jdbcUrl) }

        assertFalse(columnExists(jdbcUrl, "undo_items", "name"))
        assertNull(appliedVersions(flyway).find { it == "2" })
        assertNotNull(appliedVersions(flyway).find { it == "1" })
    }

    @Test
    fun `undoes specific version by arg`() {
        val (flyway, migrationDir, jdbcUrl) = setup()

        runUndoCommand(flyway, arrayOf("undo", "2", "--force"), input(""), migrationDir) { conn(jdbcUrl) }

        assertFalse(columnExists(jdbcUrl, "undo_items", "name"))
        assertNull(appliedVersions(flyway).find { it == "2" })
        assertNotNull(appliedVersions(flyway).find { it == "1" })
    }

    @Test
    fun `interactive yes undoes migration`() {
        val (flyway, migrationDir, jdbcUrl) = setup()

        runUndoCommand(flyway, arrayOf("undo"), input("y"), migrationDir) { conn(jdbcUrl) }

        assertFalse(columnExists(jdbcUrl, "undo_items", "name"))
        assertNull(appliedVersions(flyway).find { it == "2" })
    }

    @Test
    fun `interactive no cancels undo`() {
        val (flyway, migrationDir, jdbcUrl) = setup()

        runUndoCommand(flyway, arrayOf("undo"), input("n"), migrationDir) { conn(jdbcUrl) }

        assertTrue(columnExists(jdbcUrl, "undo_items", "name"))
        assertNotNull(appliedVersions(flyway).find { it == "2" })
    }

    @Test
    fun `interactive empty input cancels undo`() {
        val (flyway, migrationDir, jdbcUrl) = setup()

        runUndoCommand(flyway, arrayOf("undo"), input(""), migrationDir) { conn(jdbcUrl) }

        assertTrue(columnExists(jdbcUrl, "undo_items", "name"))
        assertNotNull(appliedVersions(flyway).find { it == "2" })
    }

    @Test
    fun `unknown version arg reports error without modifying db`() {
        val (flyway, migrationDir, jdbcUrl) = setup()

        runUndoCommand(flyway, arrayOf("undo", "99"), input(""), migrationDir) { conn(jdbcUrl) }

        assertTrue(columnExists(jdbcUrl, "undo_items", "name"))
        assertNotNull(appliedVersions(flyway).find { it == "2" })
    }

    @Test
    fun `missing undo file reports error without modifying db`() {
        val (flyway, _, jdbcUrl) = setup()
        val emptyDir = Files.createTempDirectory("undo-no-files").toFile()

        runUndoCommand(flyway, arrayOf("undo", "--force"), input(""), emptyDir) { conn(jdbcUrl) }

        assertNotNull(appliedVersions(flyway).find { it == "2" })
        assertTrue(columnExists(jdbcUrl, "undo_items", "name"))
    }
}
