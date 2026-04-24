// SPDX-License-Identifier: EUPL-1.2
// Copyright (C) 2026 Gemeente Utrecht
package com.baseflow.api.admin.routes

import com.baseflow.api.models.BlobStorageRepositoryResponse
import com.baseflow.api.models.CreateStorageRepositoryRequest
import com.baseflow.api.models.SetDefaultRepositoryRequest
import com.baseflow.api.models.UpdateStorageRepositoryRequest
import com.baseflow.api.models.badRequest
import com.baseflow.api.models.conflict
import com.baseflow.api.models.notFound
import com.baseflow.api.models.respondProblem
import com.baseflow.config.SecretCrypto
import com.baseflow.entities.BlobStorageRepositories
import com.baseflow.entities.BlobStorageRepositoryEntity
import com.baseflow.services.BlobStorageRegistrar
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.util.UUID

/**
 * Admin routes for managing blob storage repositories.
 *
 * Mounted at `/admin/storage-repositories`.
 *
 * Endpoints:
 * - `GET    /`          — list all repositories
 * - `POST   /`          — create a repository
 * - `GET    /{id}`      — get a single repository by UUID
 * - `PUT    /{id}`      — update a repository
 * - `DELETE /{id}`      — delete a repository
 * - `GET    /default`   — get the currently active default repository
 * - `PUT    /default`   — set the default repository
 */
fun Route.blobStorageRepositoryRoutes() {
    route("/storage-repositories") {
        get {
            val repos = transaction {
                BlobStorageRepositoryEntity.all().map { it.toResponse() }
            }
            call.respond(repos)
        }

        post {
            val body = runCatching { call.receive<CreateStorageRepositoryRequest>() }.getOrNull()
                ?: return@post call.respondProblem(
                    HttpStatusCode.BadRequest,
                    badRequest("Request body must be valid JSON.", call.request.path()),
                )

            if (body.name.isBlank()) {
                return@post call.respondProblem(
                    HttpStatusCode.BadRequest,
                    badRequest("'name' must not be blank.", call.request.path()),
                )
            }
            if (body.storageType.isBlank()) {
                return@post call.respondProblem(
                    HttpStatusCode.BadRequest,
                    badRequest("'storageType' must not be blank.", call.request.path()),
                )
            }
            if (body.accessKey.isBlank()) {
                return@post call.respondProblem(
                    HttpStatusCode.BadRequest,
                    badRequest("'accessKey' must not be blank.", call.request.path()),
                )
            }

            val created = transaction {
                val exists = BlobStorageRepositoryEntity.find {
                    BlobStorageRepositories.repoName eq body.name
                }.firstOrNull()
                if (exists != null) return@transaction null

                if (body.isDefault) {
                    BlobStorageRepositoryEntity.all()
                        .filter { it.isDefault }
                        .forEach { it.isDefault = false }
                }

                BlobStorageRepositoryEntity.new {
                    repoName = body.name
                    storageType = body.storageType
                    url = body.url
                    accessKeyHash = BlobStorageRegistrar.sha256(body.accessKey)
                    secretKeyHash = BlobStorageRegistrar.sha256(body.secretKey ?: "")
                    bucket = body.bucket ?: ""
                    region = null
                    disableChecksums = false
                    disableChunkedEncoding = false
                    extraProperties = "{}"
                    isDefault = body.isDefault
                    enabled = body.enabled
                    accessKeyEncrypted = SecretCrypto.encrypt(body.accessKey)
                    secretKeyEncrypted = body.secretKey?.takeIf { it.isNotBlank() }?.let { SecretCrypto.encrypt(it) }
                    storageAccountName = body.storageAccountName?.takeIf { it.isNotBlank() }
                    updatedAt = Clock.System.now().toLocalDateTime(TimeZone.UTC)
                }
            } ?: return@post call.respondProblem(
                HttpStatusCode.Conflict,
                conflict("A repository with this name already exists.", call.request.path()),
            )

            call.respond(HttpStatusCode.Created, created.toResponse())
        }

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

        route("/{id}") {
            get {
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

            put {
                val id = call.parameters["id"]
                    ?.let { runCatching { UUID.fromString(it) }.getOrNull() }
                    ?: return@put call.respondProblem(
                        HttpStatusCode.BadRequest,
                        badRequest("Invalid UUID.", call.request.path()),
                    )

                val body = runCatching { call.receive<UpdateStorageRepositoryRequest>() }.getOrNull()
                    ?: return@put call.respondProblem(
                        HttpStatusCode.BadRequest,
                        badRequest("Request body must be valid JSON.", call.request.path()),
                    )

                if (body.name.isBlank()) {
                    return@put call.respondProblem(
                        HttpStatusCode.BadRequest,
                        badRequest("'name' must not be blank.", call.request.path()),
                    )
                }

                val updated = transaction {
                    val existing = BlobStorageRepositoryEntity.findById(id)
                        ?: return@transaction null

                    val nameConflict = existing.repoName != body.name &&
                        BlobStorageRepositoryEntity.find { BlobStorageRepositories.repoName eq body.name }.firstOrNull() != null
                    if (nameConflict) return@transaction "conflict"

                    existing.repoName = body.name
                    existing.storageType = body.storageType
                    existing.url = body.url
                    existing.bucket = body.bucket ?: existing.bucket
                    existing.isDefault = body.isDefault
                    existing.enabled = body.enabled
                    existing.storageAccountName = body.storageAccountName?.takeIf { it.isNotBlank() }
                        ?: existing.storageAccountName

                    if (!body.accessKey.isNullOrBlank()) {
                        existing.accessKeyHash = BlobStorageRegistrar.sha256(body.accessKey)
                        existing.accessKeyEncrypted = SecretCrypto.encrypt(body.accessKey)
                    }
                    if (!body.secretKey.isNullOrBlank()) {
                        existing.secretKeyHash = BlobStorageRegistrar.sha256(body.secretKey)
                        existing.secretKeyEncrypted = SecretCrypto.encrypt(body.secretKey)
                    }

                    if (body.isDefault) {
                        BlobStorageRepositoryEntity.all()
                            .filter { it.id != existing.id && it.isDefault }
                            .forEach { it.isDefault = false }
                    }

                    existing.updatedAt = Clock.System.now().toLocalDateTime(TimeZone.UTC)
                    existing
                }

                when (updated) {
                    null -> return@put call.respondProblem(
                        HttpStatusCode.NotFound,
                        notFound("Blob storage repository not found.", call.request.path()),
                    )
                    "conflict" -> return@put call.respondProblem(
                        HttpStatusCode.Conflict,
                        conflict("A repository with this name already exists.", call.request.path()),
                    )
                    else -> call.respond(HttpStatusCode.OK, (updated as BlobStorageRepositoryEntity).toResponse())
                }
            }

            delete {
                val id = call.parameters["id"]
                    ?.let { runCatching { UUID.fromString(it) }.getOrNull() }
                    ?: return@delete call.respondProblem(
                        HttpStatusCode.BadRequest,
                        badRequest("Invalid UUID.", call.request.path()),
                    )

                val deleted = transaction {
                    val existing = BlobStorageRepositoryEntity.findById(id) ?: return@transaction false
                    existing.delete()
                    true
                }

                if (!deleted) {
                    return@delete call.respondProblem(
                        HttpStatusCode.NotFound,
                        notFound("Blob storage repository not found.", call.request.path()),
                    )
                }

                call.respond(HttpStatusCode.NoContent)
            }
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
    enabled = enabled,
    createdAt = createdAt.toString(),
    updatedAt = updatedAt.toString(),
    accessKey = accessKeyEncrypted?.let { SecretCrypto.decrypt(it) },
    secretKey = secretKeyEncrypted?.let { SecretCrypto.decrypt(it) },
    storageAccountName = storageAccountName,
)
