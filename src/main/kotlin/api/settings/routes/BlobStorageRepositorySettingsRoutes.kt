// SPDX-License-Identifier: EUPL-1.2
// Copyright (C) 2026 Gemeente Utrecht
package com.baseflow.api.settings.routes

import com.baseflow.api.models.badRequest
import com.baseflow.api.models.conflict
import com.baseflow.api.models.notFound
import com.baseflow.api.models.respondProblem
import com.baseflow.api.models.settings.BlobStorageRepositorySettingsResponse
import com.baseflow.api.models.settings.CreateBlobStorageRepositorySettingsRequest
import com.baseflow.api.models.settings.UpdateBlobStorageRepositorySettingsRequest
import com.baseflow.entities.settings.BlobStorageRepositorySettingEntity
import com.baseflow.entities.settings.BlobStorageRepositorySettingsTable
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.slf4j.LoggerFactory
import java.util.UUID
import kotlin.time.Clock

/**
 * Setting routes for managing blob storage repositories.
 *
 * Mounted at `/settings/storage-repositories`.
 *
 * Endpoints:
 * - `GET    /`      — list all repositories
 * - `POST   /`      — create a repository
 * - `PUT    /{id}`  — update a repository
 * - `DELETE /{id}`  — delete a repository
 */
fun Route.blobStorageRepositorySettingsRoutes() {
    route("/storage-repositories") {
        get {
            val repos = transaction {
                BlobStorageRepositorySettingEntity.all().map { it.toResponse() }
            }
            call.respond(repos)
        }

        post {
            val body = runCatching { call.receive<CreateBlobStorageRepositorySettingsRequest>() }.getOrNull()
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
                val exists = BlobStorageRepositorySettingEntity.find {
                    BlobStorageRepositorySettingsTable.repoName eq body.name
                }.firstOrNull()
                if (exists != null) return@transaction null
                BlobStorageRepositorySettingEntity.new {
                    repoName = body.name
                    storageType = body.storageType
                    url = body.url
                    bucket = body.bucket ?: ""
                    isDefault = body.isDefault
                    enabled = body.enabled
                    accessKey = body.accessKey
                    secretKey = body.secretKey?.takeIf { it.isNotBlank() }
                    storageAccountName = body.storageAccountName?.takeIf { it.isNotBlank() }
                    updatedAt = Clock.System.now().toLocalDateTime(TimeZone.UTC)
                }
            } ?: return@post call.respondProblem(
                HttpStatusCode.Conflict,
                conflict("A repository with this name already exists.", call.request.path()),
            )

            call.respond(HttpStatusCode.Created, created.toResponse())
        }

        route("/{id}") {
            put {
                val id = call.parameters["id"]
                    ?.let { runCatching { UUID.fromString(it) }.getOrNull() }
                    ?: return@put call.respondProblem(
                        HttpStatusCode.BadRequest,
                        badRequest("Invalid UUID.", call.request.path()),
                    )
                val body = runCatching { call.receive<UpdateBlobStorageRepositorySettingsRequest>() }.getOrNull()
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
                if (body.storageType.isBlank()) {
                    return@put call.respondProblem(
                        HttpStatusCode.BadRequest,
                        badRequest("'storageType' must not be blank.", call.request.path()),
                    )
                }

                val updated = transaction {
                    val existing = BlobStorageRepositorySettingEntity.findById(id)
                        ?: return@transaction null
                    val nameConflict = existing.repoName != body.name &&
                        BlobStorageRepositorySettingEntity.find {
                            BlobStorageRepositorySettingsTable.repoName eq body.name
                        }.firstOrNull() != null
                    if (nameConflict) return@transaction "conflict"
                    existing.repoName = body.name
                    existing.storageType = body.storageType
                    existing.url = body.url
                    existing.bucket = body.bucket ?: ""
                    existing.isDefault = body.isDefault
                    existing.enabled = body.enabled
                    if (!body.accessKey.isNullOrBlank()) {
                        existing.accessKey = body.accessKey
                    }
                    if (!body.secretKey.isNullOrBlank()) {
                        existing.secretKey = body.secretKey
                    }
                    existing.storageAccountName = body.storageAccountName?.takeIf { it.isNotBlank() }
                    existing.updatedAt = Clock.System.now().toLocalDateTime(TimeZone.UTC)
                    existing
                }
                when (updated) {
                    null -> return@put call.respondProblem(
                        HttpStatusCode.NotFound,
                        notFound("Repository not found.", call.request.path()),
                    )
                    "conflict" -> return@put call.respondProblem(
                        HttpStatusCode.Conflict,
                        conflict("A repository with this name already exists.", call.request.path()),
                    )
                    else -> call.respond(HttpStatusCode.OK, (updated as BlobStorageRepositorySettingEntity).toResponse())
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
                    val existing = BlobStorageRepositorySettingEntity.findById(id) ?: return@transaction false
                    existing.delete()
                    true
                }

                if (!deleted) {
                    return@delete call.respondProblem(
                        HttpStatusCode.NotFound,
                        notFound("Repository not found.", call.request.path()),
                    )
                }

                call.respond(HttpStatusCode.NoContent)
            }
        }
    }
}

private val logger = LoggerFactory.getLogger("com.baseflow.api.settings.routes.BlobStorageRepositorySettingsRoutes")

private fun BlobStorageRepositorySettingEntity.toResponse(): BlobStorageRepositorySettingsResponse {
    val decryptedAccessKey = try {
        accessKey
    } catch (e: Exception) {
        logger.error(
            "CRITICAL: Failed to decrypt accessKey for repository '$repoName' (${id.value}). The encryption key or salt might have changed. " +
                "The key must be re-entered to restore functionality.",
        )
        null
    }

    val decryptedSecretKey = try {
        secretKey
    } catch (e: Exception) {
        logger.error(
            "CRITICAL: Failed to decrypt secretKey for repository '$repoName' (${id.value}). The encryption key or salt might have changed. " +
                "The secret must be re-entered to restore functionality.",
        )
        null
    }

    return BlobStorageRepositorySettingsResponse(
        id = id.value.toString(),
        name = repoName,
        storageType = storageType,
        url = url,
        bucket = bucket,
        isDefault = isDefault,
        enabled = enabled,
        accessKey = decryptedAccessKey,
        secretKey = decryptedSecretKey,
        storageAccountName = storageAccountName,
        updatedAt = updatedAt.toString(),
    )
}
