// SPDX-License-Identifier: EUPL-1.2
// Copyright (C) 2026 Gemeente Utrecht
package com.baseflow.api.routes

import com.baseflow.api.models.BlobStorageRepositoryResponse
import com.baseflow.api.models.SetDefaultRepositoryRequest
import com.baseflow.api.models.badRequest
import com.baseflow.api.models.notFound
import com.baseflow.api.models.respondProblem
import com.baseflow.entities.BlobStorageRepositoryEntity
import com.baseflow.services.BlobStorageRegistrar
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.util.UUID

/**
 * Admin routes for managing blob storage repositories.
 *
 * Mounted at `/admin/storage-repositories`.
 *
 * Endpoints:
 * - `GET  /`          — list all repositories
 * - `GET  /{id}`      — get a single repository by UUID
 * - `GET  /default`   — get the currently active default repository
 * - `PUT  /default`   — set the default repository (`{"name":"<repo-name>"}`)
 */
fun Route.blobStorageRepositoryRoutes() {
    route("/storage-repositories") {
        // GET /admin/storage-repositories
        get {
            val repos = transaction {
                BlobStorageRepositoryEntity.all().map { it.toResponse() }
            }
            call.respond(repos)
        }

        // GET /admin/storage-repositories/default
        get("/default") {
            val provider = BlobStorageRegistrar.defaultProvider()
                ?: return@get call.respondProblem(
                    HttpStatusCode.NotFound,
                    notFound("No default blob storage repository configured.", call.request.path()),
                )

            val repo = transaction {
                BlobStorageRepositoryEntity.all()
                    .firstOrNull { it.repoName == provider.name }
                    ?.toResponse()
            } ?: return@get call.respondProblem(
                HttpStatusCode.NotFound,
                notFound("Default repository '${provider.name}' not found in database.", call.request.path()),
            )

            call.respond(repo)
        }

        // PUT /admin/storage-repositories/default
        put("/default") {
            val body = runCatching { call.receive<SetDefaultRepositoryRequest>() }.getOrNull()
                ?: return@put call.respondProblem(
                    HttpStatusCode.BadRequest,
                    badRequest("Request body must be JSON with a 'name' field.", call.request.path()),
                )

            if (body.name.isBlank()) {
                return@put call.respondProblem(
                    HttpStatusCode.BadRequest,
                    badRequest("'name' must not be blank.", call.request.path()),
                )
            }

            val result = runCatching { BlobStorageRegistrar.setDefaultProvider(body.name) }
            if (result.isFailure) {
                val msg = result.exceptionOrNull()?.message ?: "Unknown error"
                return@put call.respondProblem(
                    HttpStatusCode.BadRequest,
                    badRequest(msg, call.request.path()),
                )
            }

            val updated = transaction {
                BlobStorageRepositoryEntity.all()
                    .firstOrNull { it.repoName == body.name }
                    ?.toResponse()
            } ?: return@put call.respondProblem(
                HttpStatusCode.NotFound,
                notFound("Blob storage repository with name '${body.name}' not found.", call.request.path()),
            )
            call.respond(HttpStatusCode.OK, updated)
        }

        // GET /admin/storage-repositories/{id}
        get("/{id}") {
            val id = call.parameters["id"]
                ?.let { runCatching { UUID.fromString(it) }.getOrNull() }
                ?: return@get call.respondProblem(
                    HttpStatusCode.BadRequest,
                    badRequest("Invalid UUID.", call.request.path()),
                )

            val repo = transaction {
                BlobStorageRepositoryEntity.findById(id)?.toResponse()
            } ?: return@get call.respondProblem(
                HttpStatusCode.NotFound,
                notFound("Blob storage repository with id '$id' not found.", call.request.path()),
            )

            call.respond(repo)
        }
    }
}

private fun String.maskHash(): String = if (length <= 8) "****" else "${take(4)}${"*".repeat(length - 8)}${takeLast(4)}"

private fun BlobStorageRepositoryEntity.toResponse() = BlobStorageRepositoryResponse(
    id = id.value.toString(),
    name = repoName,
    storageType = storageType,
    url = url,
    accessKeyHash = accessKeyHash.maskHash(),
    secretKeyHash = secretKeyHash.maskHash(),
    bucket = bucket,
    region = region,
    disableChecksums = disableChecksums,
    disableChunkedEncoding = disableChunkedEncoding,
    extraProperties = extraProperties,
    isDefault = isDefault,
    createdAt = createdAt.toString(),
    updatedAt = updatedAt.toString(),
)
