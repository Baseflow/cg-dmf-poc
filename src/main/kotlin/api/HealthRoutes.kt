// SPDX-License-Identifier: EUPL-1.2
// Copyright (C) 2025 Gemeente Utrecht

package com.baseflow.api

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

/**
 * Health Check Endpoints Module
 *
 * Provides health and readiness endpoints for Kubernetes and monitoring systems.
 */
fun Application.healthModule() {
    routing {
        route("/health") {
            // Liveness probe - checks if the application is alive
            // This should return 200 if the application is running
            get("/liveness") {
                call.respond(HttpStatusCode.OK)
            }

            // Readiness probe - checks if the application is ready to serve traffic
            get("/readiness") {
                call.respond( HttpStatusCode.OK)
            }
        }
    }
}

