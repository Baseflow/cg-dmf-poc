// SPDX-License-Identifier: EUPL-1.2
// Copyright (C) 2025-2026 Gemeente Utrecht
@file:OptIn(ExperimentalKtorApi::class)

package com.baseflow.api.infra

import com.baseflow.api.DOCUMENTEN_API_VERSION
import com.baseflow.config.ApplicationConfig
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator
import io.ktor.http.ContentType
import io.ktor.openapi.Components
import io.ktor.openapi.OpenApiDoc
import io.ktor.openapi.OpenApiInfo
import io.ktor.openapi.ReferenceOr
import io.ktor.openapi.SecurityRequirement
import io.ktor.openapi.SecurityScheme
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

private val apiInfo = OpenApiInfo(
    title = "Documenten API",
    version = DOCUMENTEN_API_VERSION,
    description = """
        Een API om een documentregistratiecomponent (DRC) te benaderen.

        In een documentregistratiecomponent worden INFORMATIEOBJECTen opgeslagen. Een
        INFORMATIEOBJECT is een digitaal document voorzien van meta-gegevens.
        INFORMATIEOBJECTen kunnen aan andere objecten zoals zaken en besluiten worden
        gerelateerd (maar dat hoeft niet) en kunnen GEBRUIKSRECHTen hebben.

        **Uploaden van bestanden**

        Bestanden kunnen groter zijn dan de minimum die door providers ondersteund moet worden.
        Voor kleine bestanden kan de inhoud base64-encoded meegestuurd worden in de JSON.
        Voor grote bestanden (>4GB) moet de chunked upload workflow gebruikt worden via BESTANDSDELen.

        **Afhankelijkheden**

        Deze API is afhankelijk van:
        * Catalogi API
        * Notificaties API
        * Autorisaties API *(optioneel)*
        * Zaken API *(optioneel)*

        **Autorisatie**

        Deze API vereist autorisatie via JWT tokens.
    """.trimIndent(),
    contact = OpenApiInfo.Contact(
        email = "standaarden.ondersteuning@vng.nl",
        url = "https://vng-realisatie.github.io/gemma-zaken",
    ),
    license = OpenApiInfo.License(
        name = "EUPL 1.2",
        url = "https://opensource.org/licenses/EUPL-1.2",
    ),
)

private val apiTags = listOf(
    Tag("enkelvoudiginformatieobjecten", "Beheer van document registraties, bestanden en hun metadata"),
    Tag("objectinformatieobjecten", "Koppelen van documenten aan objecten"),
    Tag("subjectinformatieobjecten", "Uitbreiding voor niet-Zaken objecten"),
    Tag("bestandsdelen", "Chunked upload voor grote bestanden"),
    Tag("audittrail", "Audit log regels per INFORMATIEOBJECT"),
    Tag("admin", "Interne beheerfuncties voor opslagconfiguratie (niet onderdeel van de publieke API)"),
)

/**
 * Build the security schemes for the OpenAPI spec.
 *
 * Schemes are registered in [com.baseflow.config.AuthenticationModule] via [registerSecurityScheme]
 * and discovered here through [findSecuritySchemes]:
 * - "auth-jwt" → OAuth2 Authorization Code + PKCE (full OIDC login via Keycloak)
 * - "auth-zgw" → HTTP Bearer (paste-in ZGW/GZAC token)
 *
 * Using the exact same names as the Ktor `authenticate(...)` provider names is required so that
 * the routing-openapi `+` operator can inject matching `security` requirements on each operation,
 * which tells Swagger UI to send the Authorization header when a token or session is active.
 */
private fun Application.buildSecuritySchemes(): Map<String, ReferenceOr<SecurityScheme>> = findSecuritySchemes(useCache = false)

fun Application.openApiModule() {
    // Build the OpenApiDoc once after all routes are registered, then cache it.
    val cachedDoc = AtomicReference<OpenApiDoc>()
    monitor.subscribe(ApplicationStarted) { app ->
        cachedDoc.set(app.buildOpenApiDoc())
    }

    routing {
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

            // OpenAPI spec as JSON — used by Swagger UI and other tooling
            get("/openapi/documenten-api.json") {
                call.respond(cachedDoc.get())
            }

            // OpenAPI spec as YAML — same content converted to YAML
            get("/openapi/documenten-api.yaml") {
                val json = openApiJson.encodeToString(cachedDoc.get())
                call.respondText(convertJsonToYaml(json), contentType = ContentType.parse("application/yaml"))
            }

            // Reference OpenAPI specs from docs/ — served as static files
            staticResources("/openapi", "static/openapi-specs")

            // Swagger UI — static assets served from classpath (copied from swagger-ui-dist by Gradle)
            staticResources("/swaggerui", "static/swagger-ui")

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
 * Definition for building the OpenAPI document.
 *
 * Collects route metadata via [plus], then layers on servers, security schemes and global security.
 */
private fun Application.buildOpenApiDoc(): OpenApiDoc {
    val baseUrl = ApplicationConfig.baseUrl()

    // Discover schemes registered in AuthenticationModule: auth-jwt (OAuth2 PKCE) + auth-zgw (Bearer)
    val securitySchemes = buildSecuritySchemes()

    // Global security: OIDC via Keycloak (auth-jwt) OR paste-in ZGW token (auth-zgw).
    // These names MUST match the keys in securitySchemes above so Swagger UI can resolve them.
    val globalSecurity: List<SecurityRequirement> = listOf(
        mapOf("auth-jwt" to listOf("openid", "profile", "email")),
        mapOf("auth-zgw" to emptyList()),
    )

    val routeDoc = OpenApiDoc(info = apiInfo, tags = apiTags) + routingRoot.descendants()
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
        // Apply global security so every operation shows the Authorize options.
        security = globalSecurity,
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
