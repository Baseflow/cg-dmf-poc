// SPDX-License-Identifier: EUPL-1.2
// Copyright (C) 2026 Gemeente Utrecht
package com.baseflow.shared.entities.settings

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.datetime.CurrentDateTime
import org.jetbrains.exposed.v1.datetime.datetime

/**
 * Generic typed key/value settings table for runtime-configurable DMF settings.
 *
 * Only keys present in [KNOWN_SETTINGS] are accepted by the API.
 */
object DmfSettingsTable : Table("dmf_settings") {
    /** Maps every recognised setting key to its type ("string", "int", or "boolean"). */
    val KNOWN_SETTINGS: Map<String, String> = mapOf(
        "trigger_size_bytes" to "int",
        "chunk_size_bytes" to "int",
        "validation_enabled" to "boolean",
    )

    /** Per-key minimum value for "int" keys that must be strictly positive. */
    val KEY_MIN_VALUES: Map<String, Long> = mapOf(
        "trigger_size_bytes" to 1L,
        "chunk_size_bytes" to 1L,
    )

    val key = varchar("key", 100)
    val type = varchar("type", 20)
    val value = text("value")
    val updatedAt = datetime("updated_at").defaultExpression(CurrentDateTime)
    override val primaryKey = PrimaryKey(key)
}
