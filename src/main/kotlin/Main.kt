// SPDX-License-Identifier: EUPL-1.2
// Copyright (C) 2025 Gemeente Utrecht
package com.baseflow

import com.baseflow.api.documentenApiModule
import com.baseflow.api.healthModule
import com.baseflow.services.StorageService
import io.ktor.server.engine.*
import io.ktor.server.netty.*

fun main() {
    val storageService = StorageService()
    storageService.printConfig()

    embeddedServer(Netty, port = 8080) {
        helloWorldModule()      // Keep for basic health check at /
        healthModule()          // Health endpoints at /health/liveness and /health/readiness
        documentenApiModule()   // Documenten API at /documenten/api/v1
    }.start(wait = true)
}