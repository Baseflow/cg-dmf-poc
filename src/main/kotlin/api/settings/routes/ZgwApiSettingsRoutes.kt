// SPDX-License-Identifier: EUPL-1.2
// Copyright (C) 2026 Gemeente Utrecht
package com.baseflow.api.settings.routes

import com.baseflow.api.models.badRequest
import com.baseflow.api.models.conflict
import com.baseflow.api.models.notFound
import com.baseflow.api.models.respondProblem
import com.baseflow.api.models.settings.CreateZgwApiSettingsRequest
import com.baseflow.api.models.settings.UpdateZgwApiSettingsRequest
import com.baseflow.api.models.settings.ZgwApiSettingsResponse
import com.baseflow.config.SecretCrypto
import com.baseflow.entities.settings.ZgwApiSettingEntity
import com.baseflow.entities.settings.ZgwApiSettingsTable
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.util.UUID
import kotlin.time.Clock

/**
 * Setting routes for managing ZGW API settings.
 *
 * Mounted at `/settings/zgw-api-settings`.
 *
 * Endpoints:
 * - `GET    /`      — list all settings
 * - `POST   /`      — create a setting
 * - `PUT    /{id}`  — update a setting
 * - `DELETE /{id}`  — delete a setting
 */
fun Route.zgwApiSettingsRoutes() {
    route("/zgw-api-settings") {
        get {
            val all = transaction {
                ZgwApiSettingEntity.all().map { it.toResponse() }
            }
            call.respond(all)
        }

        post {
            val body = runCatching { call.receive<CreateZgwApiSettingsRequest>() }.getOrNull()
                ?: return@post call.respondProblem(
                    HttpStatusCode.BadRequest,
                    badRequest("Request body must be JSON with 'name', 'baseUrl', and 'clientId' fields.", call.request.path()),
                )
            if (body.name.isBlank()) {
                return@post call.respondProblem(
                    HttpStatusCode.BadRequest,
                    badRequest("'name' must not be blank.", call.request.path()),
                )
            }
            if (body.baseUrl.isBlank()) {
                return@post call.respondProblem(
                    HttpStatusCode.BadRequest,
                    badRequest("'baseUrl' must not be blank.", call.request.path()),
                )
            }
            if (body.clientId.isBlank()) {
                return@post call.respondProblem(
                    HttpStatusCode.BadRequest,
                    badRequest("'clientId' must not be blank.", call.request.path()),
                )
            }

            val created = transaction {
                val exists = ZgwApiSettingEntity.find {
                    ZgwApiSettingsTable.name eq body.name
                }.firstOrNull()
                if (exists != null) return@transaction null
                ZgwApiSettingEntity.new {
                    name = body.name
                    baseUrl = body.baseUrl
                    clientId = body.clientId
                    clientSecretEncrypted = body.clientSecret
                        ?.takeIf { it.isNotBlank() }
                        ?.let { SecretCrypto.encrypt(it) }
                    updatedAt = Clock.System.now().toLocalDateTime(TimeZone.UTC)
                }.toResponse()
            } ?: return@post call.respondProblem(
                HttpStatusCode.Conflict,
                conflict("A ZGW API setting with this name already exists.", call.request.path()),
            )
            call.respond(HttpStatusCode.Created, created)
        }

        route("/{id}") {
            put {
                val id = call.parameters["id"]
                    ?.let { runCatching { UUID.fromString(it) }.getOrNull() }
                    ?: return@put call.respondProblem(
                        HttpStatusCode.BadRequest,
                        badRequest("Invalid UUID.", call.request.path()),
                    )
                val body = runCatching { call.receive<UpdateZgwApiSettingsRequest>() }.getOrNull()
                    ?: return@put call.respondProblem(
                        HttpStatusCode.BadRequest,
                        badRequest("Request body must be JSON with 'name', 'baseUrl', and 'clientId' fields.", call.request.path()),
                    )
                if (body.name.isBlank()) {
                    return@put call.respondProblem(
                        HttpStatusCode.BadRequest,
                        badRequest("'name' must not be blank.", call.request.path()),
                    )
                }
                if (body.baseUrl.isBlank()) {
                    return@put call.respondProblem(
                        HttpStatusCode.BadRequest,
                        badRequest("'baseUrl' must not be blank.", call.request.path()),
                    )
                }
                if (body.clientId.isBlank()) {
                    return@put call.respondProblem(
                        HttpStatusCode.BadRequest,
                        badRequest("'clientId' must not be blank.", call.request.path()),
                    )
                }

                val updated = transaction {
                    val existing = ZgwApiSettingEntity.findById(id)
                        ?: return@transaction null
                    val nameConflict = existing.name != body.name &&
                        ZgwApiSettingEntity.find { ZgwApiSettingsTable.name eq body.name }.firstOrNull() != null
                    if (nameConflict) return@transaction "conflict"
                    existing.name = body.name
                    existing.baseUrl = body.baseUrl
                    existing.clientId = body.clientId
                    if (!body.clientSecret.isNullOrBlank()) {
                        existing.clientSecretEncrypted = SecretCrypto.encrypt(body.clientSecret)
                    }
                    existing.updatedAt = Clock.System.now().toLocalDateTime(TimeZone.UTC)
                    existing
                }
                when (updated) {
                    null -> return@put call.respondProblem(
                        HttpStatusCode.NotFound,
                        notFound("ZGW API setting not found.", call.request.path()),
                    )
                    "conflict" -> return@put call.respondProblem(
                        HttpStatusCode.Conflict,
                        conflict("A ZGW API setting with this name already exists.", call.request.path()),
                    )
                    else -> call.respond(HttpStatusCode.OK, (updated as ZgwApiSettingEntity).toResponse())
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
                    val existing = ZgwApiSettingEntity.findById(id) ?: return@transaction false
                    existing.delete()
                    true
                }

                if (!deleted) {
                    return@delete call.respondProblem(
                        HttpStatusCode.NotFound,
                        notFound("ZGW API setting not found.", call.request.path()),
                    )
                }

                call.respond(HttpStatusCode.NoContent)
            }
        }
    }
}

private fun ZgwApiSettingEntity.toResponse() = ZgwApiSettingsResponse(
    id = id.value.toString(),
    name = name,
    baseUrl = baseUrl,
    clientId = clientId,
    hasSecret = clientSecretEncrypted != null,
    clientSecret = clientSecretEncrypted?.let { SecretCrypto.decrypt(it) },
    updatedAt = updatedAt.toString(),
)
