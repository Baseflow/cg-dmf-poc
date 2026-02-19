// SPDX-License-Identifier: EUPL-1.2
// Copyright (C) 2026 Gemeente Utrecht

package com.baseflow.api.routes

import com.baseflow.api.middleware.requiredScope
import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

/**
 * Example routes demonstrating different scope authorization patterns.
 *
 * This file shows various ways to use requiredScope() to protect routes.
 */
fun Route.exampleScopeRoutes() {

    // Example 1: Simple scope requirement
    route("/documents") {
        requiredScope("documenten.read")
        get {
            call.respond(mapOf("message" to "List of documents"))
        }
        get("/{id}") {
            val id = call.parameters["id"]
            call.respond(mapOf("message" to "Document $id"))
        }
    }

    // Example 2: Different scopes for different operations
    route("/documents-v2") {
        // Read operations require "documenten.read"
        route("/list") {
            requiredScope("documenten.read")
            get {
                call.respond(mapOf("message" to "List of documents"))
            }
        }

        route("/{id}") {
            requiredScope("documenten.read")
            get {
                call.respond(mapOf("message" to "Get document"))
            }
        }

        // Write operations require "documenten.write"
        route("/create") {
            requiredScope("documenten.write")
            post {
                call.respond(HttpStatusCode.Created, mapOf("message" to "Document created"))
            }
        }

        route("/{id}/update") {
            requiredScope("documenten.write")
            patch {
                call.respond(mapOf("message" to "Document updated"))
            }
        }

        // Delete requires multiple scopes (AND logic)
        route("/{id}/delete") {
            requiredScope("documenten.write", "documenten.admin")
            delete {
                call.respond(HttpStatusCode.NoContent)
            }
        }
    }

    // Example 3: Nested scope requirements
    route("/admin") {
        requiredScope("documenten.read")
        route("/stats") {
            requiredScope("documenten.admin")
            get {
                call.respond(mapOf("message" to "Admin statistics"))
            }
        }
    }

    // Example 4: Public endpoint (no scope required, but still authenticated)
    get("/public/info") {
        call.respond(mapOf(
            "message" to "This endpoint is accessible to any authenticated user"
        ))
    }

    // Example 5: Wildcard scope matching
    // If user has "documenten:*", they can access all documenten routes
    route("/documents/bulk") {
        requiredScope("documenten.bulk-import")
        post {
            call.respond(HttpStatusCode.Accepted, mapOf("message" to "Bulk import started"))
        }
    }
}
