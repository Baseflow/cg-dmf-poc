// SPDX-License-Identifier: EUPL-1.2
// Copyright (C) 2025-2026 Gemeente Utrecht
@file:OptIn(ExperimentalKtorApi::class)

package com.baseflow.infra.api

import com.baseflow.infra.api.models.OpenApiSpecification
import com.baseflow.infra.api.models.openApiSpecifications
import com.baseflow.shared.api.DOCUMENTEN_API_BASE_PATH
import com.baseflow.shared.config.ApplicationConfig
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator
import io.ktor.http.ContentType
import io.ktor.openapi.Components
import io.ktor.openapi.OpenApiDoc
import io.ktor.openapi.ReferenceOr
import io.ktor.openapi.SecurityScheme
import io.ktor.openapi.Server
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStarted
import io.ktor.server.http.content.staticResources
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.openapi.findSecuritySchemes
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

/**
 * Build the security schemes for the OpenAPI spec.
 *
 * Schemes are registered in [com.baseflow.shared.config.AuthenticationModule] via [registerSecurityScheme]
 * and discovered here through [findSecuritySchemes]:
 * - "auth-jwt" → OAuth2 Authorization Code + PKCE (full OIDC login via Keycloak) — temporarily disabled for Documenten/WOPI (CGDMF-177)
 * - "auth-zgw" → HTTP Bearer (paste-in ZGW/GZAC token)
 *
 * Using the exact same names as the Ktor `authenticate(...)` provider names is required so that
 * the routing-openapi `+` operator can inject matching `security` requirements on each operation,
 * which tells Swagger UI to send the Authorization header when a token or session is active.
 */
private fun Application.buildSecuritySchemes(): Map<String, ReferenceOr<SecurityScheme>> = findSecuritySchemes(useCache = false)

fun Application.openApiModule() {
    // Build the OpenApiDoc once after all routes are registered, then cache it.
    val cache = HashMap<String, AtomicReference<OpenApiDoc>>()
    monitor.subscribe(ApplicationStarted) { app ->
        openApiSpecifications.forEach {
            val cachedDoc = AtomicReference<OpenApiDoc>()
            cachedDoc.set(app.buildOpenApiDoc(it))
            cache[it.name] = cachedDoc
        }
    }

    routing {
        // The VNG-Realisatie standard requires every API component to serve its OAS schema at
        // {API root URL}/openapi.json (and optionally /openapi.yaml).
        // See: https://vng-realisatie.github.io/gemma-zaken/standaard/#beschikbaar-stellen-van-de-oas
        route(DOCUMENTEN_API_BASE_PATH) {
            get("/openapi.json") {
                call.respond(cache["Documenten"]!!.get())
            }
            get("/openapi.yaml") {
                val json = openApiJson.encodeToString(cache["Documenten"]?.get())
                call.respondText(convertJsonToYaml(json), contentType = ContentType.parse("application/yaml"))
            }
        }.hide()

        // Hide all /docs routes from the generated OpenAPI spec (the library checks the full
        // route lineage, so hiding the parent is enough to hide every child route).
        route("/docs") {
            // Index page with links to all documentation endpoints
            get {
                val html = checkNotNull(
                    javaClass.classLoader.getResource("docs-index.html"),
                ) { "docs-index.html not found on classpath" }.readText()
                call.respondText(html, contentType = ContentType.Text.Html)
            }

            openApiSpecifications.forEach {
                get("/openapi/${it.name.lowercase()}.json") {
                    call.respond(cache[it.name]!!.get())
                }
                get("/openapi/${it.name.lowercase()}.yaml") {
                    val json = openApiJson.encodeToString(cache[it.name]?.get())
                    call.respondText(convertJsonToYaml(json), contentType = ContentType.parse("application/yaml"))
                }
            }

            // Reference OpenAPI specs from docs/ — served as static files
            staticResources("/openapi", "static/openapi-specs")

            // Swagger UI — static assets served from classpath (copied from swagger-ui-dist by Gradle)
            staticResources("/swaggerui", "static/swagger-ui")

            // Docsify viewer assets and markdown files — registered last so the more specific
            // /openapi and /swaggerui routes above take priority.
            staticResources("/", "static/docs-viewer")
        }.hide()
    }
}

/**
 * Definition for building the OpenAPI document.
 *
 * Collects route metadata via [plus], then layers on servers, security schemes and global security.
 */
private fun Application.buildOpenApiDoc(openApiSpec: OpenApiSpecification): OpenApiDoc {
    val baseUrl = ApplicationConfig.baseUrl()

    // Discover schemes registered in AuthenticationModule: auth-jwt (OAuth2 PKCE) + auth-zgw (Bearer).
    // Only schemes declared in openApiSpec.security are included in the components block so that
    // Swagger UI's Authorize dialog only shows the schemes each API actually supports.
    // auth-jwt is temporarily not supported for the Documenten and WOPI APIs (CGDMF-177).
    val allowedSchemes = openApiSpec.security.flatMap { it.keys }.toSet()
    val securitySchemes = buildSecuritySchemes().filterKeys { it in allowedSchemes }

    val apiRoutes = routingRoot.descendants().filter { route ->
        route.toString().contains(openApiSpec.basePath)
    }

    val routeDoc = OpenApiDoc(info = openApiSpec.apiInfo, tags = openApiSpec.tags) + apiRoutes
    return routeDoc.copy(
        // Expose the current server URL so Swagger UI points at the right host.
        // Users can also type a custom URL in the Swagger UI "Servers" dropdown.
        servers = listOf(
            Server(url = baseUrl, description = "Dit systeem ($baseUrl)"),
            Server(url = "https://cg-dmf.dev.baseflow.com", description = "Baseflow dev"),
            Server(url = "https://gzac-dmf.commonground.test.utrecht.nl", description = "Utrecht test"),
        ),
        // Register the named schemes so Swagger UI can resolve them.
        components = (routeDoc.components ?: Components()).copy(
            securitySchemes = securitySchemes,
        ),
        // Apply per-spec security requirements so Swagger UI shows the correct Authorize options.
        // Each OpenApiSpecification declares which schemes it accepts (e.g. Documenten: ZGW only).
        security = openApiSpec.security,
    )
}

/**
 * Converts an openAPI JSON string to YAML
 */
internal fun convertJsonToYaml(json: String): String {
    val tree = ObjectMapper().readTree(json)
    val yamlFactory =
        YAMLFactory.builder()
            .enable(YAMLGenerator.Feature.MINIMIZE_QUOTES)
            .disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER)
            .build()
    return ObjectMapper(yamlFactory).writeValueAsString(tree)
}
