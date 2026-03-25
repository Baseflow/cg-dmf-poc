// SPDX-License-Identifier: EUPL-1.2
// Copyright (C) 2025-2026 Gemeente Utrecht
package com.baseflow.api

import io.ktor.http.ContentType
import io.ktor.openapi.OpenApiDoc
import io.ktor.openapi.OpenApiInfo
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStarted
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
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

        // Serve Swagger UI pointing to /docs.json which contains proper KDoc-based
        // summaries. The default openAPI() plugin generates operationIds from the
        // route path (e.g. "documentenApiV1EnkelvoudiginformatieobjectenGet") and
        // uses them as link names. By serving our own Swagger UI page, we ensure the
        // human-readable summaries from KDoc are displayed as link names instead.
        get("/openapi") {
            call.respondText(
                swaggerUiHtml("/docs.json", apiInfo.title),
                ContentType.Text.Html,
            )
        }.hide()
    }
}

private fun swaggerUiHtml(specUrl: String, title: String) = """
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <title>$title</title>
    <link rel="stylesheet" href="https://unpkg.com/swagger-ui-dist@5/swagger-ui.css">
</head>
<body>
    <div id="swagger-ui"></div>
    <script src="https://unpkg.com/swagger-ui-dist@5/swagger-ui-bundle.js"></script>
    <script>
        SwaggerUIBundle({
            url: "$specUrl",
            dom_id: '#swagger-ui',
            deepLinking: true,
            presets: [
                SwaggerUIBundle.presets.apis,
                SwaggerUIBundle.SwaggerUIStandalonePreset
            ],
            layout: "BaseLayout"
        });
    </script>
</body>
</html>
""".trimIndent()
