// SPDX-License-Identifier: EUPL-1.2
// Copyright (C) 2025 Gemeente Utrecht

package com.baseflow.api

import com.baseflow.api.routes.bestandsDelenRoutes
import com.baseflow.api.routes.enkelvoudigInformatieObjectenRoutes
import com.baseflow.api.routes.objectInformatieObjectenRoutes
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

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
fun Application.documentenApiModule() {
    // Configure JSON serialization
    install(ContentNegotiation) {
        json()
    }

    routing {
        // API root - provides version info and available endpoints
        route("/documenten/api/v1") {

            // Health check endpoint
            get("/") {
                call.respond(
                    mapOf(
                        "service" to "Documenten API",
                        "version" to "1.5.0",
                        "status" to "operational"
                    )
                )
            }

            // EnkelvoudigInformatieObject endpoints
            // These handle the core document CRUD operations
            route("/enkelvoudiginformatieobjecten") {
                enkelvoudigInformatieObjectenRoutes()
            }

            // ObjectInformatieObject endpoints
            // These handle relations between documents and other objects (Zaken, etc.)
            route("/objectinformatieobjecten") {
                objectInformatieObjectenRoutes()
            }

            // BestandsDeel endpoints
            // These handle uploads for large files (>4GB support)
            route("/bestandsdelen") {
                bestandsDelenRoutes()
            }
        }
    }
}

