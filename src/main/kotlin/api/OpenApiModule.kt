// SPDX-License-Identifier: EUPL-1.2
// Copyright (C) 2025-2026 Gemeente Utrecht
package com.baseflow.api

import io.ktor.openapi.OpenApiDoc
import io.ktor.openapi.OpenApiInfo
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStarted
import io.ktor.server.plugins.openapi.openAPI
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.openapi.OpenApiDocSource
import io.ktor.server.routing.openapi.hide
import io.ktor.server.routing.openapi.plus
import io.ktor.server.routing.routing
import io.ktor.server.routing.routingRoot
import io.ktor.utils.io.ExperimentalKtorApi
import java.util.concurrent.atomic.AtomicReference

private val apiInfo = OpenApiInfo("Documenten API", "1.5.0")

@OptIn(ExperimentalKtorApi::class)
fun Application.openApiModule() {
    // Build the OpenApiDoc once after all routes are registered, then cache it.
    val cachedDoc = AtomicReference<OpenApiDoc>()
    monitor.subscribe(ApplicationStarted) { app ->
        cachedDoc.set(OpenApiDoc(info = apiInfo) + app.routingRoot.descendants())
    }

    routing {
        get("/docs.json") {
            val doc = cachedDoc.get()
                ?: (OpenApiDoc(info = apiInfo) + call.application.routingRoot.descendants())
                    .also { cachedDoc.compareAndSet(null, it) }
            call.respond(doc)
        }.hide()

        openAPI(path = "openapi") {
            info = apiInfo
            source = OpenApiDocSource.Routing {
                routingRoot.descendants()
            }
        }
    }
}
