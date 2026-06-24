// SPDX-License-Identifier: EUPL-1.2
// Copyright (C) 2026 Gemeente Utrecht
package com.baseflow.settings.api.routes

import com.baseflow.shared.api.models.badRequest
import com.baseflow.shared.api.models.conflict
import com.baseflow.shared.api.models.notFound
import com.baseflow.shared.api.models.respondProblem
import com.baseflow.shared.api.models.settings.BlobStorageRepositorySettingsResponse
import com.baseflow.shared.api.models.settings.CreateBlobStorageRepositorySettingsRequest
import com.baseflow.shared.api.models.settings.PatchBlobStorageRepositorySettingsRequest
import com.baseflow.shared.api.models.settings.SetDefaultRepositorySettingsRequest
import com.baseflow.shared.api.models.settings.UpdateBlobStorageRepositorySettingsRequest
import com.baseflow.shared.config.BlobStorageRepoConfig
import com.baseflow.shared.config.BlobStorageType
import com.baseflow.shared.entities.settings.BlobStorageRepositorySettingEntity
import com.baseflow.shared.entities.settings.BlobStorageRepositorySettingsTable
import com.baseflow.shared.services.BlobStorageRegistrar
import io.ktor.http.*
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.slf4j.LoggerFactory
import java.sql.SQLException
import java.util.UUID
import kotlin.time.Clock

fun Route.blobStorageRepositorySettingsRoutes() {
    route("/storage-repositories") {
        get {
            val repos = transaction {
                BlobStorageRepositorySettingEntity.all().map { it.toResponse() }
            }
            call.respond(repos)
        }

        get("/default") {
            val provider = BlobStorageRegistrar.defaultProvider()
                ?: return@get call.respondProblem(
                    HttpStatusCode.NotFound,
                    notFound("No default blob storage repository configured.", call.request.path()),
                )

            val repo = transaction {
                BlobStorageRepositorySettingEntity.all()
                    .firstOrNull { it.repoName == provider.name }
                    ?.toResponse()
            } ?: return@get call.respondProblem(
                HttpStatusCode.NotFound,
                notFound("Default repository '${provider.name}' not found in database.", call.request.path()),
            )

            call.respond(repo)
        }

        put("/default") {
            val body = runCatching { call.receive<SetDefaultRepositorySettingsRequest>() }.getOrNull()
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
                return@put call.respondProblem(HttpStatusCode.BadRequest, badRequest(msg, call.request.path()))
            }

            val updated = transaction {
                BlobStorageRepositorySettingEntity.all()
                    .firstOrNull { it.repoName == body.name }
                    ?.toResponse()
            } ?: return@put call.respondProblem(
                HttpStatusCode.NotFound,
                notFound("Repository '${body.name}' not found.", call.request.path()),
            )
            call.respond(HttpStatusCode.OK, updated)
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
            if (body.accessKey.isBlank()) {
                return@post call.respondProblem(
                    HttpStatusCode.BadRequest,
                    badRequest("'accessKey' must not be blank.", call.request.path()),
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
                    if (body.isDefault) {
                        BlobStorageRepositorySettingEntity.all().filter { it.isDefault }.forEach { it.isDefault = false }
                    }
                    BlobStorageRepositorySettingEntity.new {
                        repoName = body.name
                        this.storageType = storageType.label
                        url = body.url
                        bucket = body.bucket ?: ""
                        region = body.region
                        extraProperties = encodeExtraProperties(
                            body.extraProperties +
                                mapOf(
                                    "DISABLE_CHECKSUMS" to body.disableChecksums.toString(),
                                    "DISABLE_CHUNKED_ENCODING" to body.disableChunkedEncoding.toString(),
                                ),
                        )
                        isDefault = body.isDefault
                        enabled = body.enabled
                        accessKey = body.accessKey
                        secretKey = body.secretKey?.takeIf { it.isNotBlank() }
                        storageAccountName = body.storageAccountName?.takeIf { it.isNotBlank() }
                        createdAt = Clock.System.now()
                        updatedAt = Clock.System.now()
                    }.toResponse()
                }
            }.getOrElse { ex ->
                if (ex.isUniqueNameViolation()) {
                    return@post call.respondProblem(
                        HttpStatusCode.Conflict,
                        conflict("A repository with this name already exists.", call.request.path()),
                    )
                }
                throw ex
            }

            if (body.enabled) {
                val mergedExtra = body.extraProperties +
                    mapOf(
                        "DISABLE_CHECKSUMS" to body.disableChecksums.toString(),
                        "DISABLE_CHUNKED_ENCODING" to body.disableChunkedEncoding.toString(),
                    )
                val cfg = BlobStorageRepoConfig(
                    index = -1,
                    name = body.name,
                    type = storageType,
                    url = body.url,
                    accessKey = body.accessKey,
                    secretKey = body.secretKey ?: "",
                    bucket = body.bucket ?: "",
                    region = body.region,
                    disableChecksums = body.disableChecksums,
                    disableChunkedEncoding = body.disableChunkedEncoding,
                    extraProperties = mergedExtra,
                    isDefault = body.isDefault,
                )
                runCatching { BlobStorageRegistrar.registerProvider(cfg) }.onFailure { ex ->
                    logger.warn(
                        "Repository '{}' saved but could not be activated as a provider: {}",
                        body.name,
                        ex.message,
                    )
                }
            }

            call.respond(HttpStatusCode.Created, created)
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
                    BlobStorageRepositorySettingEntity.findById(id)?.toResponse()
                } ?: return@get call.respondProblem(
                    HttpStatusCode.NotFound,
                    notFound("Repository not found.", call.request.path()),
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
                val storageType = runCatching { BlobStorageType.fromLabel(body.storageType) }.getOrElse {
                    return@put call.respondProblem(
                        HttpStatusCode.BadRequest,
                        badRequest("Unknown storageType '${body.storageType}'.", call.request.path()),
                    )
                }

                val result = runCatching {
                    transaction {
                        val existing = BlobStorageRepositorySettingEntity.findById(id)
                            ?: return@transaction null
                        val nameConflict = existing.repoName != body.name &&
                            BlobStorageRepositorySettingEntity.find {
                                BlobStorageRepositorySettingsTable.repoName eq body.name
                            }.firstOrNull() != null
                        if (nameConflict) return@transaction "conflict"

                        val oldName = existing.repoName
                        if (body.isDefault && !existing.isDefault) {
                            BlobStorageRepositorySettingEntity.all()
                                .filter { it.id != existing.id && it.isDefault }
                                .forEach { it.isDefault = false }
                        }
                        existing.repoName = body.name
                        existing.storageType = storageType.label
                        existing.url = body.url
                        existing.bucket = body.bucket ?: ""
                        existing.region = body.region
                        existing.extraProperties = encodeExtraProperties(
                            body.extraProperties +
                                mapOf(
                                    "DISABLE_CHECKSUMS" to body.disableChecksums.toString(),
                                    "DISABLE_CHUNKED_ENCODING" to body.disableChunkedEncoding.toString(),
                                ),
                        )
                        existing.isDefault = body.isDefault
                        existing.enabled = body.enabled
                        if (!body.accessKey.isNullOrBlank()) existing.accessKey = body.accessKey
                        if (!body.secretKey.isNullOrBlank()) existing.secretKey = body.secretKey
                        existing.storageAccountName = body.storageAccountName?.takeIf { it.isNotBlank() }
                        existing.updatedAt = Clock.System.now()
                        Pair(oldName, existing)
                    }
                }.getOrElse { ex ->
                    if (ex.isUniqueNameViolation()) {
                        return@put call.respondProblem(
                            HttpStatusCode.Conflict,
                            conflict("A repository with this name already exists.", call.request.path()),
                        )
                    }
                    throw ex
                }

                call.respondWithUpdateResult(result, call.request.path())
            }

            patch {
                val id = call.parameters["id"]
                    ?.let { runCatching { UUID.fromString(it) }.getOrNull() }
                    ?: return@patch call.respondProblem(
                        HttpStatusCode.BadRequest,
                        badRequest("Invalid UUID.", call.request.path()),
                    )
                val body = runCatching { call.receive<PatchBlobStorageRepositorySettingsRequest>() }.getOrNull()
                    ?: return@patch call.respondProblem(
                        HttpStatusCode.BadRequest,
                        badRequest("Request body must be valid JSON.", call.request.path()),
                    )
                if (body.name?.isBlank() == true) {
                    return@patch call.respondProblem(
                        HttpStatusCode.BadRequest,
                        badRequest("'name' must not be blank.", call.request.path()),
                    )
                }
                val storageType = body.storageType?.let {
                    runCatching { BlobStorageType.fromLabel(it) }.getOrElse {
                        return@patch call.respondProblem(
                            HttpStatusCode.BadRequest,
                            badRequest("Unknown storageType '$it'.", call.request.path()),
                        )
                    }
                }

                val result = runCatching {
                    transaction {
                        val existing = BlobStorageRepositorySettingEntity.findById(id)
                            ?: return@transaction null
                        val newName = body.name ?: existing.repoName
                        val nameConflict = newName != existing.repoName &&
                            BlobStorageRepositorySettingEntity.find {
                                BlobStorageRepositorySettingsTable.repoName eq newName
                            }.firstOrNull() != null
                        if (nameConflict) return@transaction "conflict"

                        val oldName = existing.repoName
                        body.isDefault?.let { makeDefault ->
                            if (makeDefault && !existing.isDefault) {
                                BlobStorageRepositorySettingEntity.all()
                                    .filter { it.id != existing.id && it.isDefault }
                                    .forEach { it.isDefault = false }
                            }
                            existing.isDefault = makeDefault
                        }
                        body.name?.let { existing.repoName = it }
                        storageType?.let { existing.storageType = it.label }
                        body.url?.let { existing.url = it }
                        body.bucket?.let { existing.bucket = it }
                        body.region?.let { existing.region = it }
                        val patchedExtra = decodeExtraProperties(existing.extraProperties).toMutableMap()
                        body.extraProperties?.forEach { (k, v) -> patchedExtra[k] = v }
                        body.disableChecksums?.let { patchedExtra["DISABLE_CHECKSUMS"] = it.toString() }
                        body.disableChunkedEncoding?.let { patchedExtra["DISABLE_CHUNKED_ENCODING"] = it.toString() }
                        existing.extraProperties = encodeExtraProperties(patchedExtra)
                        body.enabled?.let { existing.enabled = it }
                        if (!body.accessKey.isNullOrBlank()) existing.accessKey = body.accessKey
                        if (!body.secretKey.isNullOrBlank()) existing.secretKey = body.secretKey
                        body.storageAccountName?.let { existing.storageAccountName = it.takeIf { s -> s.isNotBlank() } }
                        existing.updatedAt = Clock.System.now()
                        Pair(oldName, existing)
                    }
                }.getOrElse { ex ->
                    if (ex.isUniqueNameViolation()) {
                        return@patch call.respondProblem(
                            HttpStatusCode.Conflict,
                            conflict("A repository with this name already exists.", call.request.path()),
                        )
                    }
                    throw ex
                }

                call.respondWithUpdateResult(result, call.request.path())
            }

            delete {
                val id = call.parameters["id"]
                    ?.let { runCatching { UUID.fromString(it) }.getOrNull() }
                    ?: return@delete call.respondProblem(
                        HttpStatusCode.BadRequest,
                        badRequest("Invalid UUID.", call.request.path()),
                    )

                val name = transaction {
                    val existing = BlobStorageRepositorySettingEntity.findById(id) ?: return@transaction null
                    val repoName = existing.repoName
                    existing.delete()
                    repoName
                } ?: return@delete call.respondProblem(
                    HttpStatusCode.NotFound,
                    notFound("Repository not found.", call.request.path()),
                )

                BlobStorageRegistrar.unregisterProvider(name)

                call.respond(HttpStatusCode.NoContent)
            }
        }
    }
}

private val logger = LoggerFactory.getLogger("com.baseflow.settings.api.routes.BlobStorageRepositorySettingsRoutes")

private suspend fun ApplicationCall.respondWithUpdateResult(result: Any?, path: String) {
    when (result) {
        null -> respondProblem(HttpStatusCode.NotFound, notFound("Repository not found.", path))
        "conflict" -> respondProblem(HttpStatusCode.Conflict, conflict("A repository with this name already exists.", path))
        else -> {
            val (oldName, updatedEntity) =
                @Suppress("UNCHECKED_CAST")
                (result as Pair<String, BlobStorageRepositorySettingEntity>)
            val response = transaction {
                syncRegistrarForEntity(updatedEntity, oldName)
                updatedEntity.toResponse()
            }
            respond(HttpStatusCode.OK, response)
        }
    }
}

private fun syncRegistrarForEntity(entity: BlobStorageRepositorySettingEntity, oldName: String) {
    if (entity.enabled) {
        val extra = decodeExtraProperties(entity.extraProperties)
        val cfg = BlobStorageRepoConfig(
            index = -1,
            name = entity.repoName,
            type = BlobStorageType.fromLabel(entity.storageType),
            url = entity.url,
            accessKey = entity.accessKey ?: "",
            secretKey = entity.secretKey ?: "",
            bucket = entity.bucket,
            region = entity.region,
            extraProperties = extra,
            disableChecksums = extra["DISABLE_CHECKSUMS"]?.toBoolean() ?: false,
            disableChunkedEncoding = extra["DISABLE_CHUNKED_ENCODING"]?.toBoolean() ?: false,
            isDefault = entity.isDefault,
        )
        runCatching {
            BlobStorageRegistrar.updateProvider(cfg, oldName = oldName)
        }.onFailure { ex ->
            logger.warn("Repository '{}' updated but could not be re-activated as a provider: {}", entity.repoName, ex.message)
        }
    } else {
        BlobStorageRegistrar.unregisterProvider(oldName)
    }
}

private fun BlobStorageRepositorySettingEntity.toResponse(): BlobStorageRepositorySettingsResponse {
    val decryptedAccessKey = try {
        accessKey
    } catch (e: Exception) {
        logger.error(
            "CRITICAL: Failed to decrypt accessKey for repository '$repoName' (${id.value}). " +
                "The encryption key or salt might have changed. " +
                "The key must be re-entered to restore functionality.",
        )
        null
    }

    val decryptedSecretKey = try {
        secretKey
    } catch (e: Exception) {
        logger.error(
            "CRITICAL: Failed to decrypt secretKey for repository '$repoName' (${id.value}). " +
                "The encryption key or salt might have changed. " +
                "The secret must be re-entered to restore functionality.",
        )
        null
    }

    val extra = decodeExtraProperties(extraProperties)
    return BlobStorageRepositorySettingsResponse(
        id = id.value.toString(),
        name = repoName,
        storageType = storageType,
        url = url,
        bucket = bucket,
        region = region,
        disableChecksums = extra["DISABLE_CHECKSUMS"]?.toBoolean() ?: false,
        disableChunkedEncoding = extra["DISABLE_CHUNKED_ENCODING"]?.toBoolean() ?: false,
        extraProperties = extra,
        isDefault = isDefault,
        enabled = enabled,
        accessKey = decryptedAccessKey,
        secretKey = decryptedSecretKey,
        storageAccountName = storageAccountName,
        createdAt = createdAt.toString(),
        updatedAt = updatedAt.toString(),
    )
}

private fun encodeExtraProperties(map: Map<String, String>): String =
    Json.encodeToString(JsonObject.serializer(), JsonObject(map.mapValues { JsonPrimitive(it.value) }))

private fun decodeExtraProperties(json: String): Map<String, String> = runCatching {
    Json.parseToJsonElement(json)
        .let { it as? JsonObject }
        ?.mapValues { (_, v) -> v.jsonPrimitive.content }
        ?: emptyMap()
}.getOrDefault(emptyMap())

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
