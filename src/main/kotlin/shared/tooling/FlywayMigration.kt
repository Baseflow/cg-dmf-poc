// SPDX-License-Identifier: EUPL-1.2
// Copyright (C) 2025-2026 Gemeente Utrecht
package com.baseflow.shared.tooling

import com.baseflow.shared.config.DatabaseConfig
import org.flywaydb.core.Flyway
import java.io.File
import java.sql.Connection
import java.sql.DriverManager

/**
 * Flyway Migration Runner
 *
 * This tool provides a programmatic interface to Flyway migrations, designed for:
 * - Running migrations in Docker containers (where Gradle may not be available)
 * - Local development via Gradle tasks (./gradlew flywayMigrate, etc.)
 * - CI/CD pipelines that need direct Java execution
 *
 * ## Usage
 *
 * ### Via Gradle (recommended for local development):
 * ```
 * ./gradlew flywayMigrate   # Apply pending migrations
 * ./gradlew flywayInfo      # Show migration status
 * ./gradlew flywayValidate  # Validate migrations
 * ./gradlew flywayUndo      # Undo last migration (or -Pargs=<version>)
 * ```
 *
 * ### Direct execution:
 * ```
 * java -cp ... com.baseflow.shared.tooling.FlywayMigrationKt migrate
 * ```
 *
 * ### In Docker:
 * ```
 * docker exec dmf-app java -cp /app/app.jar com.baseflow.shared.tooling.FlywayMigrationKt migrate
 * ```
 *
 * ## Environment Variables
 *
 * - `DB_URL` (default: jdbc:postgresql://localhost:5432/documenten)
 * - `DB_USER` (default: documenten)
 * - `DB_PASSWORD` (default: documenten)
 *
 * @see main/kotlin/tooling/MigrationGenerator.kt for generating migrations from Exposed models
 */
fun main(args: Array<String>) {
    val flyway = Flyway.configure()
        .dataSource(DatabaseConfig.url, DatabaseConfig.user, DatabaseConfig.password)
        .mixed(true)
        .locations("classpath:db/migration")
        .load()

    when (args.firstOrNull()) {
        "migrate" -> {
            println("Running migrations...")
            val result = flyway.migrate()
            println("Successfully applied ${result.migrationsExecuted} migration(s)")
            println("Current version: ${result.targetSchemaVersion ?: "empty"}")
        }

        "info" -> {
            println("Migration info:")
            val info = flyway.info()
            info.all().forEach { migration ->
                println("  ${migration.version ?: "baseline"} - ${migration.description} [${migration.state}]")
            }
        }

        "undo" -> runUndoCommand(flyway, args)

        "clean" -> runCleanCommand(flyway, args)

        "validate" -> {
            println("Validating migrations...")
            flyway.validate()
            println("Migrations are valid")
        }

        "repair" -> runRepairCommand(flyway, args)

        else -> {
            println("Usage: ./gradlew <task> [-Pargs='--force']")
            println("Tasks:")
            println("  flywayMigrate  - Apply pending migrations")
            println("  flywayInfo     - Show migration status")
            println("  flywayUndo     - Undo last migration (or -Pargs=<version>)")
            println("  flywayValidate - Validate applied migrations")
            println("  flywayRepair   - Repair migration checksums in schema history")
            println("  flywayClean    - Drop all objects in the database (DESTRUCTIVE)")
            println()
            println("Pass --force to skip interactive confirmation:")
            println("  ./gradlew flywayUndo   -Pargs='--force'")
            println("  ./gradlew flywayRepair -Pargs='--force'")
            println("  ./gradlew flywayClean  -Pargs='--force'")
        }
    }
}

internal fun runUndoCommand(
    flyway: Flyway,
    args: Array<String>,
    readLine: () -> String? = { System.console()?.readLine() },
    migrationDir: File = File("src/main/resources/db/migration"),
    getConnection: () -> Connection = {
        DriverManager.getConnection(DatabaseConfig.url, DatabaseConfig.user, DatabaseConfig.password)
    },
) {
    val force = args.contains("--force")
    val versionArg = args.drop(1).filterNot { it == "--force" }.firstOrNull()

    val allApplied = flyway.info().applied().filter { it.version != null }
    if (allApplied.isEmpty()) {
        println("No applied migrations found. Nothing to undo.")
        return
    }

    val target = if (versionArg != null) {
        allApplied.find { it.version!!.version == versionArg }
            ?: run {
                println("Error: Version $versionArg not found in applied migrations.")
                return
            }
    } else {
        allApplied.last()
    }

    val version = target.version!!.version

    val undoFiles = migrationDir.listFiles { f ->
        f.name.matches(Regex("U${Regex.escape(version)}__.*\\.sql"))
    } ?: emptyArray()

    if (undoFiles.isEmpty()) {
        println("Error: No undo file found for version $version")
        println("Expected: U${version}__*.sql in ${migrationDir.path}")
        return
    }

    val undoFile = undoFiles.first()
    println("Found undo file: ${undoFile.name}")

    val confirmed = force ||
        run {
            print("Undo V$version - ${target.description}? [y/N] ")
            readLine()?.trim()?.lowercase() == "y"
        }

    if (!confirmed) {
        println("Undo cancelled.")
        return
    }

    val sql = undoFile.readText()
    getConnection().use { conn ->
        conn.createStatement().use { stmt ->
            stmt.execute(sql)
        }
        conn.prepareStatement("""DELETE FROM "flyway_schema_history" WHERE "version" = ?""").use { stmt ->
            stmt.setString(1, version)
            stmt.executeUpdate()
        }
    }

    println("Successfully undone V$version - ${target.description}")
}

internal fun runCleanCommand(flyway: Flyway, args: Array<String>, readLine: () -> String? = { System.console()?.readLine() }) {
    val force = args.contains("--force")

    val confirmed = force ||
        run {
            print("This will drop ALL objects in the database and cannot be undone. Proceed? [y/N] ")
            readLine()?.trim()?.lowercase() == "y"
        }

    if (confirmed) {
        println("Cleaning database...")
        flyway.clean()
        println("Database cleaned successfully")
    } else {
        println("Clean cancelled.")
    }
}

internal fun runRepairCommand(flyway: Flyway, args: Array<String>, readLine: () -> String? = { System.console()?.readLine() }) {
    val force = args.contains("--force")

    // Also include MISSING_SUCCESS/MISSING_FAILED: orphaned history rows with no matching file on disk.
    // isChecksumMatching returns true for those (resolvedChecksum is null → short-circuits), so they
    // would escape both conditions without the explicit applied-but-unresolved check.
    val failing = flyway.info().all().filter {
        !it.isChecksumMatching || it.state.isFailed || (it.state.isApplied && !it.state.isResolved)
    }
    if (failing.isEmpty()) {
        println("No failing migrations detected. Nothing to repair.")
        return
    }

    println("Failing migrations:")
    failing.forEach { m ->
        val reason = when {
            !m.isChecksumMatching -> "checksum mismatch (applied: ${m.appliedChecksum}, resolved: ${m.resolvedChecksum})"
            m.state.isApplied && !m.state.isResolved -> "orphaned (no migration file found)"
            else -> "state: ${m.state}"
        }
        println("  ${m.version ?: "baseline"} - ${m.description} [$reason]")
    }
    println()

    val confirmed = force ||
        run {
            print("Repair will update the schema history table. Proceed? [y/N] ")
            readLine()?.trim()?.lowercase() == "y"
        }

    if (confirmed) {
        println("Repairing migration checksums...")
        flyway.repair()
        println("Repair complete.")
    } else {
        println("Repair cancelled.")
    }
}
