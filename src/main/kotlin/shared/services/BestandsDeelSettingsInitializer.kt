// SPDX-License-Identifier: EUPL-1.2
// Copyright (C) 2026 Gemeente Utrecht
package com.baseflow.shared.services

import com.baseflow.shared.config.BestandsDeelConfig
import com.baseflow.shared.entities.settings.DmfSettingsTable
import org.jetbrains.exposed.v1.exceptions.ExposedSQLException
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.slf4j.LoggerFactory
import java.sql.SQLException

/**
 * Seeds [DmfSettingsTable] with defaults derived from [BestandsDeelConfig] on startup.
 *
 * Only inserts a key when it is not yet present, so any value previously
 * persisted via the admin UI is left untouched.
 *
 * Call once after Flyway migration completes.
 */
object BestandsDeelSettingsInitializer {

    private val logger = LoggerFactory.getLogger(BestandsDeelSettingsInitializer::class.java)

    fun initialise(config: BestandsDeelConfig = BestandsDeelConfig.Default) {
        insertIfAbsent("trigger_size_bytes", "int", config.triggerSizeBytes.toString())
        insertIfAbsent("chunk_size_bytes", "int", config.chunkSizeBytes.toString())
        insertIfAbsent("validation_enabled", "boolean", "true")
        logger.info(
            "BestandsDeelSettingsInitializer: trigger_size_bytes={}, chunk_size_bytes={}",
            config.triggerSizeBytes,
            config.chunkSizeBytes,
        )
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
            if (isUniqueViolation(e)) {
                false
            } else {
                throw e
            }
        }

        if (inserted) {
            logger.info("Initialized dmf_settings['{}'] = {}", key, value)
        } else {
            logger.debug("dmf_settings['{}'] already present – skipping", key)
        }
    }

    private fun isUniqueViolation(e: ExposedSQLException): Boolean {
        var cause: Throwable? = e
        while (cause != null) {
            val sqlState = (cause as? SQLException)?.sqlState
            if (sqlState == "23505") return true
            cause = cause.cause
        }
        return false
    }
}
