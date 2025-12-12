// SPDX-License-Identifier: EUPL-1.2
// Copyright (C) 2025 Gemeente Utrecht
package com.baseflow.config

internal object DatabaseConfig : Config {
    val url: String = System.getenv("DB_URL") ?: "jdbc:postgresql://localhost:5432/documenten"
    val user: String = System.getenv("DB_USER") ?: "documenten"
    val password: String = System.getenv("DB_PASSWORD") ?: "documenten"
    val driver: String = System.getenv("DB_DRIVER") ?: "org.postgresql.Driver"

    override fun printConfig() {
        println("DatabaseConfig:")
        println("  url: $url")
        println("  user: $user")
        println("  password: $password")
        println("  driver: $driver")
    }
}
