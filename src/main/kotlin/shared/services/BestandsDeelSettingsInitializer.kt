// SPDX-License-Identifier: EUPL-1.2
// Copyright (C) 2026 Gemeente Utrecht
package com.baseflow.shared.services

import com.baseflow.shared.config.BestandsDeelConfig
import com.baseflow.shared.entities.settings.DmfSettingsTable
import org.jetbrains.exposed.v1.exceptions.ExposedSQLException
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.upsert
import org.slf4j.LoggerFactory

/**
 * Seeds [DmfSettingsTable] with bestandsdeel defaults on startup.
 *
 * For keys whose env var is explicitly set (see [BestandsDeelConfig.envReadonlyKeys]), the env-var
 * value is upserted on every startup so it always wins over any previously stored value.
 *
 * For keys whose env var is not set, [insertIfAbsent] is used so that any value already in the DB
 * (e.g. seeded by Flyway or changed via the admin UI) is preserved.
 *
 * Call once after Flyway migration completes.
 */
object BestandsDeelSettingsInitializer {

    private val logger = LoggerFactory.getLogger(BestandsDeelSettingsInitializer::class.java)

    fun initialise(config: BestandsDeelConfig = BestandsDeelConfig.Default) {
        seed("trigger_size_bytes", "int", config.triggerSizeBytes.toString(), "trigger_size_bytes" in config.envReadonlyKeys)
        seed("chunk_size_bytes", "int", config.chunkSizeBytes.toString(), "chunk_size_bytes" in config.envReadonlyKeys)
        insertIfAbsent("validation_enabled", "boolean", "true")
        val effective = DmfSettingsService.loadBestandsDeelSettings()
        logger.info(
            "Effective bestandsdeel settings: trigger_size_bytes={}, chunk_size_bytes={}",
            effective.triggerSizeBytes,
            effective.chunkSizeBytes,
        )
    }

    private fun seed(key: String, type: String, value: String, readonly: Boolean) {
        if (readonly) {
            transaction {
                DmfSettingsTable.upsert {
                    it[DmfSettingsTable.key] = key
                    it[DmfSettingsTable.type] = type
                    it[DmfSettingsTable.value] = value
                }
            }
            logger.debug("Pinned dmf_settings['{}'] = {} (from env)", key, value)
        } else {
            insertIfAbsent(key, type, value)
        }
    }

    private fun insertIfAbsent(key: String, type: String, value: String) {
        val inserted = try {
            transaction {
                DmfSettingsTable.insert {
                    it[DmfSettingsTable.key] = key
                    it[DmfSettingsTable.type] = type
                    it[DmfSettingsTable.value] = value
                }
            }
            true
        } catch (e: ExposedSQLException) {
            var cause: Throwable? = e
            while (cause != null) {
                if (cause is java.sql.SQLException) {
                    var sqlEx: java.sql.SQLException? = cause
                    while (sqlEx != null) {
                        if (sqlEx.sqlState == "23505") return
                        sqlEx = sqlEx.nextException
                    }
                }
                cause = cause.cause
            }
            throw e
        }
        if (inserted) logger.info("Initialized dmf_settings['{}'] = {}", key, value)
    }
}
