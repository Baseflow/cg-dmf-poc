// SPDX-License-Identifier: EUPL-1.2
// Copyright (C) 2025-2026 Gemeente Utrecht
package com.baseflow.api.documenten

import com.baseflow.api.DOCUMENTEN_API_BASE_PATH
import com.baseflow.api.DOCUMENTEN_API_VERSION
import com.baseflow.api.documenten.routes.*
import com.baseflow.api.middleware.*
import com.baseflow.api.models.ResourceSegments
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.plugins.conditionalheaders.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.routing.openapi.hide
import io.ktor.utils.io.ExperimentalKtorApi

/**
 * Documenten API Routing Module
 *
 * Implements the VNG Documenten API 1.5.0 specification for document management.
 *
 * Main endpoints implemented:
 * - /enkelvoudiginformatieobjecten - Document (record) objects
 * - /objectinformatieobjecten - Relations between documents and other objects
 * - /bestandsdelen - File parts for large file uploads
 *
 * Endpoints NOT implemented (out of scope):
 * - /audittrail - Audit logging (not in PoC scope)
 * - /gebruiksrechten - Usage rights (not in PoC scope)
 * - /verzendingen - Shipments (not in PoC scope)
 *
 * Note: This implementation extends the standard Documenten API to support
 * relations with objects beyond just Zaken (cases).
 */
@OptIn(ExperimentalKtorApi::class)
fun Route.documentenApiRoutes() {
    // API root - provides version info and available endpoints
    route(DOCUMENTEN_API_BASE_PATH) {
        install(NotificationPlugin)
        install(AuditTrailPlugin)
        install(ApiVersionHeader) { version = DOCUMENTEN_API_VERSION }
        install(ConditionalHeaders) {
            version(ApiConditionalHeadersProvider)
        }

        /**
         * Documenten API root.
         *
         * Geeft versie-informatie en beschikbare endpoints van de Documenten API.
         *
         * Responses:
         *   - 200 Service info.
         *
         * @tag DocumentenAPI
         */
        get("/") {
            call.respond(
                mapOf(
                    "service" to "Documenten API",
                    "version" to DOCUMENTEN_API_VERSION,
                    "status" to "operational",
                ),
            )
        }
            .hide()

        // EnkelvoudigInformatieObject endpoints
        // These handle the core document CRUD operations
        route("/${ResourceSegments.ENKELVOUDIG_INFORMATIE_OBJECTEN}") {
            enkelvoudigInformatieObjectenRoutes()
            auditTrailRoutes()
        }

        // ObjectInformatieObject endpoints
        // These handle relations between documents and other objects (Zaken, etc.)
        route("/${ResourceSegments.OBJECT_INFORMATIE_OBJECTEN}") {
            objectInformatieObjectenRoutes()
        }

        // EXPERIMENTAL: SubjectInformatieObject endpoints
        // These handle relations between documents and subject objects
        route("/${ResourceSegments.SUBJECT_INFORMATIE_OBJECTEN}") {
            subjectInformatieObjectenRoutes()
        }

        // BestandsDeel endpoints
        // These handle uploads for large files (>4GB support)
        route("/bestandsdelen") {
            bestandsDelenRoutes()
        }
    }
}

fun Application.documentenApiModule(useAuthentication: Boolean = true) {
    // Configure StatusPages for global exception handling
    configureStatusPages()

    routing {
        if (useAuthentication) {
            authenticate("auth-jwt", "auth-zgw", strategy = AuthenticationStrategy.FirstSuccessful) {
                documentenApiRoutes()
            }
        } else {
            documentenApiRoutes()
        }
    }
}
