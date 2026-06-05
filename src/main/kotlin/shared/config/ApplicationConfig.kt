// SPDX-License-Identifier: EUPL-1.2
// Copyright (C) 2025 Gemeente Utrecht
package com.baseflow.shared.config

import org.slf4j.LoggerFactory

object ApplicationConfig : Config() {
    private val logger = LoggerFactory.getLogger(ApplicationConfig::class.java)

    val port: Int = System.getenv("PORT")?.toIntOrNull() ?: 8080
    val baseUrl: String = envOrSystem("BASE_URL", "http://localhost:$port")

    override fun printConfig() {
        val effective = baseUrl()
        logger.info("ApplicationConfig: baseUrl={}, port={}", effective, port)
    }

    fun baseUrl(): String {
        val effective = this.baseUrl
        return effective.trim().removeSuffix("/")
    }
}
