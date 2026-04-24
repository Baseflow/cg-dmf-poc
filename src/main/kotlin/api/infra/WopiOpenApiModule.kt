// SPDX-License-Identifier: EUPL-1.2
// Copyright (C) 2026 Gemeente Utrecht
package com.baseflow.api.infra

import com.baseflow.api.DOCUMENTEN_API_VERSION
import com.baseflow.api.WOPI_API_BASE_PATH
import com.baseflow.config.WopiConfig
import io.ktor.http.ContentType
import io.ktor.openapi.OpenApiDoc
import io.ktor.openapi.OpenApiInfo
import io.ktor.openapi.Server
import io.ktor.openapi.Tag
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStarted
import io.ktor.server.http.content.staticResources
import io.ktor.server.plugins.openapi.openAPI
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.openapi.OpenApiDocSource
import io.ktor.server.routing.openapi.hide
import io.ktor.server.routing.openapi.plus
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import io.ktor.server.routing.routingRoot
import io.ktor.utils.io.ExperimentalKtorApi
import kotlinx.serialization.json.Json
import java.util.concurrent.atomic.AtomicReference

private val openApiJson = Json {
    explicitNulls = false
    encodeDefaults = false
}

private val wopiApiInfo = OpenApiInfo(
    title = "WOPI Integration API",
    version = DOCUMENTEN_API_VERSION,
    description = """
        Een API om een WOPI-compatible client te integreren met de Documenten API.
    """.trimIndent(),
    license = OpenApiInfo.License(
        name = "EUPL 1.2",
        url = "https://opensource.org/licenses/EUPL-1.2",
    ),
)

private val wopiApiTags = listOf(
    Tag("wopi", "WOPI (Web Application Open Platform Interface) host endpoints"),
)

@OptIn(ExperimentalKtorApi::class)
fun Application.wopiOpenApiModule() {
    if (!WopiConfig.isEnabled()) {
        return
    }

    // Build the OpenApiDoc once after all routes are registered, then cache it.
    val cachedDoc = AtomicReference<OpenApiDoc>()
    monitor.subscribe(ApplicationStarted) { app ->
        cachedDoc.set(app.buildWopiOpenApiDoc())
    }

    routing {
        route("/wopi/docs") {
            // OpenAPI spec as JSON — used by Swagger UI and other tooling
            get("/wopi-api.json") {
                call.respond(cachedDoc.get())
            }

            // OpenAPI spec as YAML — same content converted to YAML
            get("/wopi-api.yaml") {
                val json = openApiJson.encodeToString(cachedDoc.get())
                call.respondText(convertJsonToYaml(json), contentType = ContentType.parse("application/yaml"))
            }

            // Swagger UI — WOPI-specific index.html served from its own directory so it points at
            // /wopi/docs/wopi-api.json; JS/CSS assets are reused from the main swagger-ui-dist copy.
            staticResources("/swaggerui", "static/swagger-ui-wopi")

            // Ktor built-in OpenAPI UI — generated directly from routing annotations
            // Output is redirected to a system temp dir so it works in both local and containerised
            // environments (a relative "build/tmp" path does not exist in Docker/Kubernetes).
            openAPI("ktor-openapi") {
                outputPath = System.getProperty("java.io.tmpdir") + "/swagger-codegen"
                source = OpenApiDocSource.Routing {
                    routingRoot.descendants()
                }
            }
        }.hide()
    }
}

/**
 * Builds an [OpenApiDoc] scoped to the WOPI routes only.
 *
 * They are not included in the main API docs but are available if WOPI_ENABLED is set to true.
 */
fun Application.buildWopiOpenApiDoc(): OpenApiDoc {
    val wopiRoutes = routingRoot.descendants().filter { route ->
        route.toString().startsWith(WOPI_API_BASE_PATH)
    }

    val routeDoc = OpenApiDoc(info = wopiApiInfo, tags = wopiApiTags) + wopiRoutes
    return routeDoc.copy(
        servers = listOf(
            Server(url = "http://localhost:8080", description = "Local development"),
        ),
    )
}
