// SPDX-License-Identifier: EUPL-1.2
// Copyright (C) 2026 Gemeente Utrecht
package com.baseflow.settings.api.routes

import com.baseflow.shared.api.models.badRequest
import com.baseflow.shared.api.models.conflict
import com.baseflow.shared.api.models.notFound
import com.baseflow.shared.api.models.respondProblem
import com.baseflow.shared.api.models.settings.CreateZgwApiSettingsRequest
import com.baseflow.shared.api.models.settings.UpdateZgwApiSettingsRequest
import com.baseflow.shared.api.models.settings.ZgwApiSettingsResponse
import com.baseflow.shared.entities.settings.ApiConnectionSettingEntity
import com.baseflow.shared.entities.settings.ApiConnectionSettingsTable
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
                ApiConnectionSettingEntity.all().map { it.toResponse() }
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
                val exists = ApiConnectionSettingEntity.find {
                    ApiConnectionSettingsTable.name eq body.name
                }.firstOrNull()
                if (exists != null) return@transaction null
                ApiConnectionSettingEntity.new {
                    name = body.name
                    baseUrl = body.baseUrl
                    clientId = body.clientId
                    clientSecret = body.clientSecret
                        ?.takeIf { it.isNotBlank() }
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
                    val existing = ApiConnectionSettingEntity.findById(id)
                        ?: return@transaction null
                    val nameConflict = existing.name != body.name &&
                        ApiConnectionSettingEntity.find { ApiConnectionSettingsTable.name eq body.name }.firstOrNull() != null
                    if (nameConflict) return@transaction "conflict"
                    existing.name = body.name
                    existing.baseUrl = body.baseUrl
                    existing.clientId = body.clientId
                    if (!body.clientSecret.isNullOrBlank()) {
                        existing.clientSecret = body.clientSecret
                    }
                    existing.updatedAt = Clock.System.now().toLocalDateTime(TimeZone.UTC)
                    existing.toResponse()
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
                    else -> call.respond(HttpStatusCode.OK, updated as ZgwApiSettingsResponse)
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
                    val existing = ApiConnectionSettingEntity.findById(id) ?: return@transaction false
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

private val logger = LoggerFactory.getLogger("com.baseflow.settings.api.routes.ZgwApiSettingsRoutes")

private fun ApiConnectionSettingEntity.toResponse(): ZgwApiSettingsResponse {
    val decryptedSecret = try {
        clientSecret
    } catch (e: Exception) {
        logger.error(
            "CRITICAL: Failed to decrypt clientSecret for ZGW API setting '$name' (${id.value}). " +
                "The encryption key or salt might have changed. " +
                "The secret must be re-entered to restore functionality.",
        )
        null
    }

    return ZgwApiSettingsResponse(
        id = id.value.toString(),
        name = name,
        baseUrl = baseUrl,
        clientId = clientId,
        hasSecret = decryptedSecret != null,
        clientSecret = decryptedSecret,
        updatedAt = updatedAt.toString(),
    )
}
