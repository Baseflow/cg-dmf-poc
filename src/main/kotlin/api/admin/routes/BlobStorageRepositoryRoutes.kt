// SPDX-License-Identifier: EUPL-1.2
// Copyright (C) 2026 Gemeente Utrecht
package com.baseflow.api.admin.routes

import com.baseflow.api.models.BlobStorageRepositoryResponse
import com.baseflow.api.models.CreateBlobStorageRepositoryRequest
import com.baseflow.api.models.SetDefaultRepositoryRequest
import com.baseflow.api.models.UpdateBlobStorageRepositoryRequest
import com.baseflow.api.models.badRequest
import com.baseflow.api.models.conflict
import com.baseflow.api.models.notFound
import com.baseflow.api.models.respondProblem
import com.baseflow.config.BlobStorageRepoConfig
import com.baseflow.config.BlobStorageType
import com.baseflow.entities.BlobStorageRepositoryEntity
import com.baseflow.services.BlobStorageRegistrar
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.sql.SQLException
import java.util.UUID
import kotlin.time.Clock

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
        /**
         * Geeft een lijst van alle geconfigureerde blob storage repositories.
         *
         * Responses:
         *   - 200 Lijst van repositories.
         *
         * @tag Admin
         */
        // GET /admin/storage-repositories
        get {
            val repos = transaction {
                BlobStorageRepositoryEntity.all().map { it.toResponse() }
            }
            call.respond(repos)
        }

        /**
         * Geeft de huidige standaard blob storage repository.
         *
         * Responses:
         *   - 200 De standaard repository.
         *   - 404 Geen standaard repository geconfigureerd.
         *
         * @tag Admin
         */
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

        /**
         * Stelt de standaard blob storage repository in.
         *
         * Responses:
         *   - 200 De bijgewerkte standaard repository.
         *   - 400 Ongeldige aanvraag.
         *   - 404 Repository niet gevonden.
         *
         * @tag Admin
         */
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

        /**
         * Geeft een specifieke blob storage repository op basis van UUID.
         *
         * Responses:
         *   - 200 De gevraagde repository.
         *   - 400 Ongeldig UUID.
         *   - 404 Repository niet gevonden.
         *
         * @tag Admin
         */
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

        /**
         * Maakt een nieuwe blob storage repository aan.
         *
         * Responses:
         *   - 201 De aangemaakte repository.
         *   - 400 Ongeldige aanvraag of onbekend storageType.
         *
         * @tag Admin
         */
        // POST /admin/storage-repositories
        post {
            val body = runCatching { call.receive<CreateBlobStorageRepositoryRequest>() }.getOrNull()
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

            val storageType = runCatching { BlobStorageType.fromLabel(body.storageType) }.getOrElse {
                return@post call.respondProblem(
                    HttpStatusCode.BadRequest,
                    badRequest("Unknown storageType '${body.storageType}'.", call.request.path()),
                )
            }

            val created = runCatching {
                transaction {
                    // If this one should be default, clear existing defaults first
                    if (body.isDefault) {
                        BlobStorageRepositoryEntity.all().filter { it.isDefault }.forEach { it.isDefault = false }
                    }

                    BlobStorageRepositoryEntity.new {
                        repoName = body.name
                        this.storageType = storageType.label
                        url = body.url
                        accessKey = body.accessKey
                        secretKey = body.secretKey
                        bucket = body.bucket
                        region = body.region
                        disableChecksums = body.disableChecksums
                        disableChunkedEncoding = body.disableChunkedEncoding
                        extraProperties = encodeExtraProperties(body.extraProperties)
                        isDefault = body.isDefault
                        createdAt = Clock.System.now().toLocalDateTime(TimeZone.UTC)
                        updatedAt = Clock.System.now().toLocalDateTime(TimeZone.UTC)
                    }.toResponse()
                }
            }.getOrElse { ex ->
                if (ex.isUniqueNameViolation()) {
                    return@post call.respondProblem(
                        HttpStatusCode.Conflict,
                        conflict(
                            "A blob storage repository with name '${body.name}' already exists.",
                            call.request.path(),
                        ),
                    )
                }
                throw ex
            }

            val cfg = BlobStorageRepoConfig(
                index = -1,
                name = body.name,
                type = storageType,
                url = body.url,
                accessKey = body.accessKey,
                secretKey = body.secretKey,
                bucket = body.bucket,
                region = body.region,
                disableChecksums = body.disableChecksums,
                disableChunkedEncoding = body.disableChunkedEncoding,
                extraProperties = body.extraProperties,
                isDefault = body.isDefault,
            )
            BlobStorageRegistrar.registerProvider(cfg)

            call.respond(HttpStatusCode.Created, created)
        }

        /**
         * Wijzigt een bestaande blob storage repository op basis van UUID.
         *
         * Responses:
         *   - 200 De bijgewerkte repository.
         *   - 400 Ongeldig UUID of ongeldige aanvraag.
         *   - 404 Repository niet gevonden.
         *
         * @tag Admin
         */
        // PATCH /admin/storage-repositories/{id}
        patch("/{id}") {
            val id = call.parameters["id"]
                ?.let { runCatching { UUID.fromString(it) }.getOrNull() }
                ?: return@patch call.respondProblem(
                    HttpStatusCode.BadRequest,
                    badRequest("Invalid UUID.", call.request.path()),
                )

            val body = runCatching { call.receive<UpdateBlobStorageRepositoryRequest>() }.getOrNull()
                ?: return@patch call.respondProblem(
                    HttpStatusCode.BadRequest,
                    badRequest("Request body must be valid JSON.", call.request.path()),
                )

            val storageType = body.storageType?.let {
                runCatching { BlobStorageType.fromLabel(it) }.getOrElse {
                    return@patch call.respondProblem(
                        HttpStatusCode.BadRequest,
                        badRequest("Unknown storageType '$it'.", call.request.path()),
                    )
                }
            }

            val updated = runCatching {
                transaction {
                    val entity = BlobStorageRepositoryEntity.findById(id)
                        ?: return@transaction null

                    val oldName = entity.repoName

                    body.name?.let { entity.repoName = it }
                    storageType?.let { entity.storageType = it.label }
                    body.url?.let { entity.url = it }
                    body.accessKey?.let { entity.accessKey = it }
                    body.secretKey?.let { entity.secretKey = it }
                    body.bucket?.let { entity.bucket = it }
                    body.region?.let { entity.region = it }
                    body.disableChecksums?.let { entity.disableChecksums = it }
                    body.disableChunkedEncoding?.let { entity.disableChunkedEncoding = it }
                    body.extraProperties?.let { entity.extraProperties = encodeExtraProperties(it) }
                    body.isDefault?.let { makeDefault ->
                        if (makeDefault) {
                            BlobStorageRepositoryEntity.all()
                                .filter { it.id != entity.id && it.isDefault }
                                .forEach { it.isDefault = false }
                        }
                        entity.isDefault = makeDefault
                    }
                    entity.updatedAt = Clock.System.now().toLocalDateTime(TimeZone.UTC)

                    Pair(oldName, entity)
                }
            }.getOrElse { ex ->
                if (ex.isUniqueNameViolation()) {
                    return@patch call.respondProblem(
                        HttpStatusCode.Conflict,
                        conflict(
                            "A blob storage repository with name '${body.name}' already exists.",
                            call.request.path(),
                        ),
                    )
                }
                throw ex
            } ?: return@patch call.respondProblem(
                HttpStatusCode.NotFound,
                notFound("Blob storage repository with id '$id' not found.", call.request.path()),
            )

            val (oldName, entity) = updated
            val cfg = BlobStorageRepoConfig(
                index = -1,
                name = entity.repoName,
                type = BlobStorageType.fromLabel(entity.storageType),
                url = entity.url,
                accessKey = entity.accessKey,
                secretKey = entity.secretKey,
                bucket = entity.bucket,
                region = entity.region,
                disableChecksums = entity.disableChecksums,
                disableChunkedEncoding = entity.disableChunkedEncoding,
                extraProperties = decodeExtraProperties(entity.extraProperties),
                isDefault = entity.isDefault,
            )
            BlobStorageRegistrar.updateProvider(cfg, oldName = oldName)

            call.respond(HttpStatusCode.OK, entity.toResponse())
        }

        /**
         * Verwijdert een blob storage repository op basis van UUID.
         *
         * Responses:
         *   - 204 Repository verwijderd.
         *   - 400 Ongeldig UUID.
         *   - 404 Repository niet gevonden.
         *
         * @tag Admin
         */
        // DELETE /admin/storage-repositories/{id}
        delete("/{id}") {
            val id = call.parameters["id"]
                ?.let { runCatching { UUID.fromString(it) }.getOrNull() }
                ?: return@delete call.respondProblem(
                    HttpStatusCode.BadRequest,
                    badRequest("Invalid UUID.", call.request.path()),
                )

            val name = transaction {
                val entity = BlobStorageRepositoryEntity.findById(id) ?: return@transaction null
                val repoName = entity.repoName
                entity.delete()
                repoName
            } ?: return@delete call.respondProblem(
                HttpStatusCode.NotFound,
                notFound("Blob storage repository with id '$id' not found.", call.request.path()),
            )

            BlobStorageRegistrar.unregisterProvider(name)

            call.respond(HttpStatusCode.NoContent)
        }
    }
}

private fun String.maskSecret(): String = if (length <= 8) "****" else "${take(4)}${"*".repeat(length - 8)}${takeLast(4)}"

/**
 * Returns true if this exception (or any exception in its cause chain, including
 * [java.sql.SQLException.getNextException] siblings) indicates a unique-constraint
 * violation (SQL state 23505).
 *
 * PostgreSQL wraps the actual constraint error inside a [java.sql.BatchUpdateException];
 * the real cause is accessible via [java.sql.SQLException.getNextException].
 */
private fun Throwable.isUniqueNameViolation(): Boolean {
    var t: Throwable? = this
    while (t != null) {
        if (t is SQLException) {
            var sqlEx: SQLException? = t
            while (sqlEx != null) {
                if (sqlEx.sqlState == "23505") return true
                sqlEx = sqlEx.nextException
            }
        }
        t = t.cause
    }
    return false
}

private fun encodeExtraProperties(map: Map<String, String>): String =
    Json.encodeToString(JsonObject.serializer(), JsonObject(map.mapValues { JsonPrimitive(it.value) }))

private fun decodeExtraProperties(json: String): Map<String, String> = runCatching {
    Json.parseToJsonElement(json)
        .let { it as? JsonObject }
        ?.mapValues { (_, v) -> v.jsonPrimitive.content }
        ?: emptyMap()
}.getOrDefault(emptyMap())

private fun BlobStorageRepositoryEntity.toResponse() = BlobStorageRepositoryResponse(
    id = id.value.toString(),
    name = repoName,
    storageType = storageType,
    url = url,
    accessKeyMasked = accessKey.maskSecret(),
    secretKeyMasked = secretKey.maskSecret(),
    bucket = bucket,
    region = region,
    disableChecksums = disableChecksums,
    disableChunkedEncoding = disableChunkedEncoding,
    extraProperties = decodeExtraProperties(extraProperties),
    isDefault = isDefault,
    createdAt = createdAt.toString(),
    updatedAt = updatedAt.toString(),
)
