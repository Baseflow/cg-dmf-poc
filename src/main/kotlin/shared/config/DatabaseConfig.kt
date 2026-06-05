// SPDX-License-Identifier: EUPL-1.2
// Copyright (C) 2025 Gemeente Utrecht
package com.baseflow.shared.config

import org.slf4j.LoggerFactory

internal object DatabaseConfig : Config() {
    private val logger = LoggerFactory.getLogger(DatabaseConfig::class.java)

    val url: String = envOrSystem("DB_URL", "jdbc:postgresql://localhost:5432/documenten")
    val user: String = envOrSystem("DB_USER", "documenten")
    val password: String = envOrSystem("DB_PASSWORD", "documenten")
    val driver: String = envOrSystem("DB_DRIVER", "org.postgresql.Driver")
    val poolSize: Int = envOrSystem("DB_POOL_SIZE", "10").toInt()

    override fun printConfig() {
        logger.info("DatabaseConfig: url={}, user={}, driver={}", url, user, driver)
        logger.debug("DatabaseConfig: password is set: {}", password.isNotEmpty())
    }
}
