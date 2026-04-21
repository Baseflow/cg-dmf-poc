// SPDX-License-Identifier: EUPL-1.2
// Copyright (C) 2025 Gemeente Utrecht
package com.baseflow.config

import org.slf4j.LoggerFactory

object WopiConfig : Config() {
    private val logger = LoggerFactory.getLogger(WopiConfig::class.java)

    val wopiEnabled: Boolean = envOrSystem("WOPI_ENABLED", "false").toBoolean()

    override fun printConfig() {
        logger.info("WOPI_ENABLED={}", wopiEnabled)
    }

    fun isEnabled(): Boolean = wopiEnabled
}
