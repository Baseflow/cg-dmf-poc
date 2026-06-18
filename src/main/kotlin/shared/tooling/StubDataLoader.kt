// SPDX-License-Identifier: EUPL-1.2
// Copyright (C) 2025-2026 Gemeente Utrecht
package com.baseflow.shared.tooling

import com.baseflow.shared.config.DatabaseConfig
import com.baseflow.shared.entities.EIORecordEntity
import com.baseflow.shared.entities.EIOVersionEntity
import com.baseflow.shared.entities.EIOVersionTrefwoordEntity
import com.baseflow.shared.entities.TrefwoordEntity
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/**
 * Stub Data Loader
 *
 * This tool provides a way to populate the database with stub/seed data for:
 * - Local development and testing
 * - Demo environments
 * - Integration tests
 *
 * ## Usage
 *
 * ### Via Gradle (recommended):
 * ```
 * ./gradlew addStubData
 * ```
 *
 * ### Direct execution:
 * ```
 * java -cp ... com.baseflow.shared.tooling.StubDataLoaderKt
 * ```
 *
 * ### In Docker:
 * ```
 * docker exec dmf-app java -cp /app/app.jar com.baseflow.shared.tooling.StubDataLoaderKt
 * ```
 *
 * ## Environment Variables
 *
 * - `DB_URL` (default: jdbc:postgresql://localhost:5432/documenten)
 * - `DB_USER` (default: documenten)
 * - `DB_PASSWORD` (default: documenten)
 *
 * ## Customization
 *
 * Modify the `loadStubData()` function to add your own stub data based on
 * your Exposed table definitions.
 */
fun main() {
    println("Connecting to database: ${DatabaseConfig.url}")

    Database.connect(
        url = DatabaseConfig.url,
        driver = DatabaseConfig.driver,
        user = DatabaseConfig.user,
        password = DatabaseConfig.password,
    )

    try {
        loadStubData()
        println("✓ Stub data loaded successfully!")
    } catch (e: Exception) {
        println("✗ Error loading stub data: ${e.message}")
        e.printStackTrace()
        throw e
    }
}

/**
 * Load stub data into the database.
 *
 * Modify this function to insert your own test/development data.
 * Use Exposed's transaction DSL to insert data into your tables.
 *
 * Example:
 * ```
 * transaction {
 *     // Insert stub users
 *     Users.insert {
 *         it[name] = "Test User"
 *         it[email] = "test@example.com"
 *     }
 *
 *     // Insert stub records
 *     EIORecords.insert {
 *         it[title] = "Sample Record"
 *         it[createdAt] = Clock.System.now()
 *     }
 * }
 * ```
 */
@OptIn(ExperimentalTime::class)
private fun loadStubData() {
    transaction {
        println("Loading stub data...")

        val record = EIORecordEntity.new {
            lockToken = null
        }
        val now = Clock.System.now()
        val nowLocal = now.toLocalDateTime(TimeZone.UTC)
        val eioVersion = EIOVersionEntity.new {
            recordId = record // Pass the entity, not UUID
            versie = 1
            taal = "dut"
            bestandsnaam = "test.pdf"
            formaat = "application/pdf"
            bestandsomvang = 123456789L
            link = "https://example.com/test.pdf"
            integriteitAlgoritme = "sha_256"
            integriteitWaarde = "sha256:abcdef1234567890"
            integriteitsDatum = nowLocal.date
            beginRegistratie = now
            verschijningsVorm = "Inlevering"
            bronOrganisatie = "012345678"
            creatieDatum = nowLocal.date
            titel = "Test document"
            vertrouwlijkheidsAanduiding = "Niet vertrouwelijk"
            auteur = "Test auteur"
            status = "IN_BEWERKING"
            beschrijving = "Test beschrijving"
            indicatieGebruiksrecht = false
            identificatie = "test-document"
        }
        listOf("test", "example").forEach { woord ->
            EIOVersionTrefwoordEntity.new {
                versionId = eioVersion
                trefwoordId = TrefwoordEntity.findOrCreate(woord)
            }
        }

        println("Stub data loaded successfully")
    }
}
