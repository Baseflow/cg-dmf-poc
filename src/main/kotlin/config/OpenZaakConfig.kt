// SPDX-License-Identifier: EUPL-1.2
// Copyright (C) 2026 Gemeente Utrecht
package com.baseflow.config

class OpenZaakConfig(
    val endpoint: String = envOrSystem("OPENZAAK_ENDPOINT", "https://openzaak.dev.baseflow.com"),
    val clientId: String = envOrSystem("OPENZAAK_CLIENT_ID", "cg-dmf"),
    val clientSecret: String = envOrSystem("OPENZAAK_CLIENT_SECRET", "baseflow"),
    val validationEnabled: Boolean = (
        envOrSystem(
            "OPENZAAK_VALIDATION_ENABLED",
            System.getProperty("OPENZAAK_VALIDATION_ENABLED", "true"),
        ).toBoolean()
        ),
) : Config() {
    override fun printConfig() {
        println("OpenZaakConfig:")
        println("  endpoint: $endpoint")
        println("  clientId: $clientId")
        println("  clientSecret: $clientSecret")
        println("  validationEnabled: $validationEnabled")
    }

    companion object {
        fun fromEnv(): OpenZaakConfig = OpenZaakConfig()
    }
}
