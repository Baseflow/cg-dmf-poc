// SPDX-License-Identifier: EUPL-1.2
// Copyright (C) 2025 Gemeente Utrecht
package com.baseflow.shared.config

import org.slf4j.LoggerFactory

object ApplicationConfig : Config() {
    private val logger = LoggerFactory.getLogger(ApplicationConfig::class.java)

    val port: Int = envOrSystem("PORT", "8080").toInt()
    val host: String = envOrSystem("HOST", "localhost")
    val baseUrl: String = envOrSystem("BASE_URL", "http://$host:$port")

    override fun printConfig() {
        val effective = baseUrl()
        logger.info("ApplicationConfig: host={}, port={}, baseUrl={}", host, port, effective)
    }

    fun baseUrl(): String {
        val effective = this.baseUrl
        return effective.trim().removeSuffix("/")
    }
}
