// SPDX-License-Identifier: EUPL-1.2
// Copyright (C) 2025 Gemeente Utrecht
package com.baseflow.config

import org.slf4j.LoggerFactory

object ApplicationConfig : Config {
    private val logger = LoggerFactory.getLogger(ApplicationConfig::class.java)

    val baseUrl: String? = System.getenv("BASE_URL")
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
    val port: Int = System.getenv("PORT")?.toIntOrNull() ?: 8080

    override fun printConfig() {
        val effective = baseUrl()
        logger.info("ApplicationConfig: baseUrl={}, port={}", effective, port)
    }

    fun baseUrl(): String {
        val effective = this.baseUrl ?: "http://localhost:$port"
        return effective.trim().removeSuffix("/")
    }
}