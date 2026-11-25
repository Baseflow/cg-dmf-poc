// SPDX-License-Identifier: EUPL-1.2
// Copyright (C) 2025 Gemeente Utrecht
package com.baseflow.shared.config

import org.slf4j.LoggerFactory

object ApplicationConfig : Config() {
    private val logger = LoggerFactory.getLogger(ApplicationConfig::class.java)

    val port: Int = envOrSystem("PORT", "8080").toInt()
    val host: String = envOrSystem("HOST", "localhost")
    val baseUrl: String = envOrSystem("BASE_URL", "http://$host:$port")

    /**
     * Maximum total size (bytes) of the HTTP request headers Netty accepts. SSO access tokens with many
     * roles or groups can exceed the Netty default of 8 KiB; the admin portal forwards
     * such a token as `Authorization: Bearer ...` on every backend call. Override with MAX_HEADER_SIZE.
     */
    val maxHeaderSize: Int = envOrSystem("MAX_HEADER_SIZE", "131072").toInt()

    /** Maximum length (bytes) of the HTTP request line (method + URI + version). Override with MAX_INITIAL_LINE_LENGTH. */
    val maxInitialLineLength: Int = envOrSystem("MAX_INITIAL_LINE_LENGTH", "16384").toInt()

    override fun printConfig() {
        val effective = baseUrl()
        logger.info(
            "ApplicationConfig: host={}, port={}, baseUrl={}, maxHeaderSize={}, maxInitialLineLength={}",
            host,
            port,
            effective,
            maxHeaderSize,
            maxInitialLineLength,
        )
    }

    fun baseUrl(): String {
        val effective = this.baseUrl
        return effective.trim().removeSuffix("/")
    }
}
