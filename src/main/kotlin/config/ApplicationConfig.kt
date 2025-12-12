// SPDX-License-Identifier: EUPL-1.2
// Copyright (C) 2025 Gemeente Utrecht
package com.baseflow.config

import io.ktor.server.application.ApplicationCall

internal object ApplicationConfig : Config {
    val baseUrl: String? = System.getenv("BASE_URL")
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
    val port: Int = System.getenv("PORT")?.toIntOrNull() ?: 8080

    override fun printConfig() {
        val effective = baseUrl ?: "http://localhost:8080"
        println("ApplicationConfig:")
        println("  baseUrl: $effective")
        println("  port: $port")
    }
}

fun ApplicationCall.baseUrl(): String {
    return ApplicationConfig.baseUrl ?: "http://localhost:8080"
}