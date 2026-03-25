// SPDX-License-Identifier: EUPL-1.2
// Copyright (C) 2025-2026 Gemeente Utrecht
package com.baseflow.api

import com.baseflow.services.HealthCheckService
import com.baseflow.services.HealthValidateResponse
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.koin.ktor.ext.inject

/**
 * Health Check Endpoints Module
 *
 * Provides health and readiness endpoints for Kubernetes and monitoring systems.
 */
fun Application.healthModule() {
    routing {
        route("/health") {
            /**
             * Liveness probe - checks if the application is alive.
             *
             * This should return 200 if the application is running.
             * Used by Kubernetes to determine if the pod should be restarted.
             *
             * Responses:
             *   - 200 Application is alive.
             *
             * @tag Health
             */
            get("/liveness") {
                call.respond(HttpStatusCode.OK)
            }

            /**
             * Readiness probe - checks if the application is ready to serve traffic.
             *
             * Used by Kubernetes to determine if the pod should receive traffic.
             *
             * Responses:
             *   - 200 Application is ready.
             *
             * @tag Health
             */
            get("/readiness") {
                call.respond(HttpStatusCode.OK)
            }

            // Validate probe - checks connectivity to external dependencies (database & S3 storage)
            get("/validate") {
                val healthCheckService by inject<HealthCheckService>()

                val database = healthCheckService.checkDatabase()
                val storage = healthCheckService.checkStorage()

                val response = HealthValidateResponse(
                    status = if (database.status == "ok" && storage.status == "ok") "ok" else "error",
                    database = database,
                    storage = storage,
                )

                val statusCode = if (response.status == "ok") HttpStatusCode.OK else HttpStatusCode.ServiceUnavailable
                call.respond(statusCode, response)
            }
        }
    }
}
