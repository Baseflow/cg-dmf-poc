// SPDX-License-Identifier: EUPL-1.2
// Copyright (C) 2025 Gemeente Utrecht
@file:OptIn(ExperimentalDatabaseMigrationApi::class)

package com.baseflow.tooling

import com.baseflow.config.DatabaseConfig
import org.jetbrains.exposed.v1.core.ExperimentalDatabaseMigrationApi
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.migration.jdbc.MigrationUtils

/**
 * Helper tool to generate Flyway migration scripts from Exposed Table definitions.
 *
 * Usage:
 *   1. Update your Exposed Table definition with new columns/constraints
 *   2. Run: ./gradlew generateMigration --args="V2__Add_title_column"
 *   3. Review the generated SQL in src/main/resources/db/migration/
 *   4. Create a matching undo script (U2__*.sql)
 *   5. Apply with: ./gradlew flywayMigrate
 */
fun main(args: Array<String>) {
    if (args.isEmpty()) {
        println("Usage: ./gradlew generateMigration --args='<script_name> [table_name]'")
        println("Example: ./gradlew generateMigration --args='V2__Add_title_column'")
        println("Example: ./gradlew generateMigration --args='V3__Create_bestandsdelen bestandsdelen'")
        return
    }

    val scriptName = args[0]
    val filterTableName = if (args.size > 1) args[1] else null

    // Connect to the database
    val database = Database.connect(
        url = DatabaseConfig.url,
        user = DatabaseConfig.user,
        password = DatabaseConfig.password,
        driver = DatabaseConfig.driver,
    )

    if (filterTableName != null) {
        println("Generating migration script: $scriptName (filtered for table: $filterTableName)")
    } else {
        println("Generating migration script: $scriptName")
    }
    println("Comparing with database: ${DatabaseConfig.url}")
    println()

    transaction(database) {
        // Generate migration for filtered tables or all tables
        // MigrationUtils will compare current DB state with Table definitions
        val allTables = AllTables.tables
        val tables = if (filterTableName != null) {
            val filtered = allTables.filter { it.tableName == filterTableName }.toTypedArray()
            if (filtered.isEmpty()) {
                println(
                    "✗ Error: Table '$filterTableName' not found in AllTables. Available tables: ${allTables.joinToString {
                        it.tableName
                    }}",
                )
                return@transaction
            }
            filtered
        } else {
            allTables
        }

        try {
            MigrationUtils.generateMigrationScript(
                *tables,
                scriptDirectory = "src/main/resources/db/migration",
                scriptName = scriptName,
            )
            println("✓ Generated migration script $scriptName.sql")
        } catch (e: Exception) {
            println("✗ Error generating migration: ${e.message}")
        }
    }

    println()
    println("Migration script generated!")
    println("Next steps:")
    println("  1. Review: src/main/resources/db/migration/$scriptName.sql")
    println("  2. Create undo script: src/main/resources/db/migration/${scriptName.replace("V", "U")}.sql")
    println("  3. Apply: ./gradlew flywayMigrate")
}
