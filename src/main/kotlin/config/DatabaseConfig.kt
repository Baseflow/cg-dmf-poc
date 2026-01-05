// SPDX-License-Identifier: EUPL-1.2
// Copyright (C) 2025 Gemeente Utrecht
package com.baseflow.config

import org.slf4j.LoggerFactory

internal object DatabaseConfig : Config {
    private val logger = LoggerFactory.getLogger(DatabaseConfig::class.java)

    val url: String = System.getenv("DB_URL") ?: "jdbc:postgresql://localhost:5432/documenten"
    val user: String = System.getenv("DB_USER") ?: "documenten"
    val password: String = System.getenv("DB_PASSWORD") ?: "documenten"
    val driver: String = System.getenv("DB_DRIVER") ?: "org.postgresql.Driver"

    override fun printConfig() {
        logger.info("DatabaseConfig: url={}, user={}, driver={}", url, user, driver)
        logger.debug("DatabaseConfig: password is set: {}", password.isNotEmpty())
    }
}
