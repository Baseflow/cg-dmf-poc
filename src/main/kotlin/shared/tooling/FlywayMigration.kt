// SPDX-License-Identifier: EUPL-1.2
// Copyright (C) 2025-2026 Gemeente Utrecht
package com.baseflow.shared.tooling

import com.baseflow.shared.config.DatabaseConfig
import org.flywaydb.core.Flyway

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
 * ## Important Limitations
 *
 * **Flyway Community Edition does NOT support automatic undo operations.**
 *
 * - The `undo` command in this tool only shows instructions
 * - To revert a migration, you must:
 *   1. Manually execute the corresponding U*.sql file
 *   2. Remove the migration record from flyway_schema_history
 *   3. Use the helper script: `./flyway-undo.sh <version>`
 *
 * See docs/DATABASE.md for detailed migration workflows and undo procedures.
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
            // Targeted repair for V7 pgcrypto removal (see Main.kt for details)
            // TODO: Remove once all environments have been upgraded past V10.
            val v7Info = flyway.info().all().firstOrNull { it.version?.version == "7" }
            if (v7Info != null && !v7Info.isChecksumMatching) {
                println("V7 checksum mismatch detected (pgcrypto removal). Repairing...")
                flyway.repair()
            }
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

        "undo" -> {
            println("Undoing last migration...")
            println("Note: Undo is not available in Flyway Community Edition.")
            println("You need to manually execute the undo script or use Flyway Teams/Enterprise.")
            println("\nTo manually undo, connect to the database and run:")
            println(
                "  psql -h localhost -U documenten -d documenten -f src/main/resources/db/migration/U1__Drop_EIO_tables.sql",
            )
        }

        "clean" -> {
            println("Cleaning database...")
            flyway.clean()
            println("Database cleaned successfully")
        }

        "validate" -> {
            println("Validating migrations...")
            flyway.validate()
            println("Migrations are valid")
        }

        else -> {
            println("Usage: gradle run --args='<command>'")
            println("Commands:")
            println("  migrate  - Apply pending migrations")
            println("  info     - Show migration status")
            println("  undo     - Undo last migration")
            println("  clean    - Clean database (remove all objects)")
            println("  validate - Validate applied migrations")
        }
    }
}
