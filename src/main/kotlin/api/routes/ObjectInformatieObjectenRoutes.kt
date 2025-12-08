// SPDX-License-Identifier: EUPL-1.2
// Copyright (C) 2025 Gemeente Utrecht

package com.baseflow.api.routes

import com.baseflow.api.models.ObjectInformatieObjectResponse
import com.baseflow.api.models.PaginatedResponse
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlin.math.log

/**
 * ObjectInformatieObject routes
 *
 * Handles relations between documents and other objects (extended beyond Zaken):
 * - POST / - Create relation
 * - GET / - List relations (with filtering)
 * - GET /{uuid} - Get single relation
 * - DELETE /{uuid} - Delete relation
 *
 * This PoC extends the standard to support additional object types beyond Zaken.
 */
fun Route.objectInformatieObjectenRoutes() {
    // List all document-object relations

    get {
        val response = PaginatedResponse(
            count = 0,
            next = null,
            previous = null,
            results = emptyList<ObjectInformatieObjectResponse>()
        )
        call.respond(response)
    }

    // Create new document-object relation
    post {
        call.respond(mapOf("message" to "Create ObjectInformatieObject - to be implemented"))
    }

    // Single relation operations
    route("/{uuid}") {
        // Get single relation
        get {
            val uuid = call.parameters["uuid"]
            call.respond(mapOf("message" to "Get ObjectInformatieObject $uuid - to be implemented"))
        }

        // Delete relation
        delete {
            val uuid = call.parameters["uuid"]
            call.respond(mapOf("message" to "Delete ObjectInformatieObject $uuid - to be implemented"))
        }
    }
}

