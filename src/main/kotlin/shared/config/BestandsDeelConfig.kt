// SPDX-License-Identifier: EUPL-1.2
// Copyright (C) 2026 Gemeente Utrecht
package com.baseflow.shared.config

import org.slf4j.LoggerFactory

/**
 * Configuration for chunked file upload (bestandsdelen) workflow.
 *
 * When a file is larger than [triggerSizeBytes], the EIO create response will contain
 * a `bestandsdelen` array describing the expected upload chunks.  Each chunk has a
 * target size of [chunkSizeBytes].
 *
 * Environment variables:
 *   - BESTANDSDELEN_TRIGGER_SIZE  – file size in bytes above which chunking is used
 *                                   (default: 300 MB)
 *   - BESTANDSDELEN_CHUNK_SIZE    – target size in bytes of each individual chunk
 *                                   (default: 100 MB)
 */
open class BestandsDeelConfig : Config() {
    private val logger = LoggerFactory.getLogger(BestandsDeelConfig::class.java)

    /** Minimum file size (exclusive) that triggers the bestandsdelen workflow. Default: 300 MB. */
    open val triggerSizeBytes: Long =
        envOrSystem("BESTANDSDELEN_TRIGGER_SIZE", (300L * 1024 * 1024).toString()).toLong()

    /** Target size of each individual bestandsdeel chunk. Default: 100 MB. */
    open val chunkSizeBytes: Long =
        envOrSystem("BESTANDSDELEN_CHUNK_SIZE", (100L * 1024 * 1024).toString()).toLong()

    /**
     * Keys in [DmfSettingsTable][com.baseflow.shared.entities.settings.DmfSettingsTable] whose values
     * are pinned by an explicit environment variable.  The initializer upserts these on every startup
     * so the env var wins over any previously stored value; the settings API refuses to mutate them.
     */
    open val envReadonlyKeys: Set<String> = buildSet {
        if (isEnvPresent("BESTANDSDELEN_TRIGGER_SIZE")) add("trigger_size_bytes")
        if (isEnvPresent("BESTANDSDELEN_CHUNK_SIZE")) add("chunk_size_bytes")
    }

    override fun printConfig() {
        logger.info(
            "BestandsDeelConfig (seed defaults from env): triggerSizeBytes={}, chunkSizeBytes={}",
            triggerSizeBytes,
            chunkSizeBytes,
        )
    }

    companion object Default : BestandsDeelConfig()
}
