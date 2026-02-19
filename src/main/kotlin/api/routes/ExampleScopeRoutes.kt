// SPDX-License-Identifier: EUPL-1.2
// Copyright (C) 2026 Gemeente Utrecht

package com.baseflow.api.routes

import com.baseflow.api.middleware.RequireScope
import com.baseflow.api.middleware.withScopes
import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

/**
 * Example routes demonstrating different scope authorization patterns.
 *
 * This file shows various ways to use the withScopes() function to protect routes.
 */
fun Route.exampleScopeRoutes() {

    // Example 1: Simple scope requirement
    // All routes in this block require "documenten.read" scope
    withScopes {
        @RequireScope("documenten.read")
        get("/documents") {
            call.respond(mapOf("message" to "List of documents"))
        }

        @RequireScope("documenten.read")
        get("/documents/{id}") {
            val id = call.parameters["id"]
            call.respond(mapOf("message" to "Document $id"))
        }
    }

    // Example 2: Different scopes for different operations
    route("/documents") {
        // Read operations require "documenten.read"
        withScopes {
            @RequireScope("documenten.read")
            get {
                call.respond(mapOf("message" to "List of documents"))
            }

            @RequireScope("documenten.read")
            get("/{id}") {
                call.respond(mapOf("message" to "Get document"))
            }
        }

        // Write operations require "documenten.write"
        withScopes {
            @RequireScope("documenten.write")
            post {
                call.respond(HttpStatusCode.Created, mapOf("message" to "Document created"))
            }

            @RequireScope("documenten.write")
            patch("/{id}") {
                call.respond(mapOf("message" to "Document updated"))
            }
        }

        // Delete requires multiple scopes (AND logic)
        withScopes {
            @RequireScope("documenten.write", "documenten.admin")
            delete("/{id}") {
                call.respond(HttpStatusCode.NoContent)
            }
        }
    }

    // Example 3: Nested scope requirements
    withScopes {
        @RequireScope("documenten.read")
        route("/admin") {
            // This route requires BOTH "documenten.read" AND "documenten.admin"
            @RequireScope("documenten.admin")
            get("/stats") {
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
    // If user has "documenten.*", they can access all documenten routes
    withScopes {
        @RequireScope("documenten.bulk-import")
        post("/documents/bulk") {
            call.respond(HttpStatusCode.Accepted, mapOf("message" to "Bulk import started"))
        }
    }
}
