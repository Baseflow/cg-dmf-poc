// SPDX-License-Identifier: EUPL-1.2
// Copyright (C) 2026 Gemeente Utrecht
package com.baseflow.shared.services

import com.baseflow.shared.config.BestandsDeelConfig
import com.baseflow.shared.entities.settings.DmfSettingsTable
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.slf4j.LoggerFactory
import java.util.concurrent.atomic.AtomicReference
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

@OptIn(ExperimentalTime::class)
object DmfSettingsService {

    data class BestandsDeelSettings(val triggerSizeBytes: Long, val chunkSizeBytes: Long)

    private val logger = LoggerFactory.getLogger(DmfSettingsService::class.java)

    private val cacheTtl = 30.seconds

    @OptIn(ExperimentalTime::class)
    private val cache = AtomicReference<Pair<BestandsDeelSettings, Instant>?>(null)

    fun loadBestandsDeelSettings(): BestandsDeelSettings {
        val now = Clock.System.now()
        cache.get()?.let { (settings, expiresAt) -> if (expiresAt > now) return settings }
        val rows = transaction {
            DmfSettingsTable.selectAll()
                .where { DmfSettingsTable.key inList listOf("trigger_size_bytes", "chunk_size_bytes") }
                .associate { it[DmfSettingsTable.key] to it[DmfSettingsTable.value] }
        }
        val fresh = BestandsDeelSettings(
            triggerSizeBytes = rows["trigger_size_bytes"]?.toLongOrNull() ?: BestandsDeelConfig.Default.triggerSizeBytes,
            chunkSizeBytes = rows["chunk_size_bytes"]?.toLongOrNull() ?: BestandsDeelConfig.Default.chunkSizeBytes,
        )
        cache.set(Pair(fresh, now + cacheTtl))
        logger.debug("Loaded bestandsdeel settings from DB: triggerSize={}, chunkSize={}", fresh.triggerSizeBytes, fresh.chunkSizeBytes)
        return fresh
    }

    fun invalidateCache() {
        cache.set(null)
    }
}
