// SPDX-License-Identifier: EUPL-1.2
// Copyright (C) 2026 Gemeente Utrecht
package com.baseflow.settings.api.routes

import com.baseflow.shared.api.models.badRequest
import com.baseflow.shared.api.models.conflict
import com.baseflow.shared.api.models.forbidden
import com.baseflow.shared.api.models.notFound
import com.baseflow.shared.api.models.respondProblem
import com.baseflow.shared.api.models.settings.ApiConnectionSettingResponse
import com.baseflow.shared.api.models.settings.CreateApiConnectionSettingRequest
import com.baseflow.shared.api.models.settings.UpdateApiConnectionSettingRequest
import com.baseflow.shared.entities.settings.ApiAuthType
import com.baseflow.shared.entities.settings.ApiConnectionSettingEntity
import com.baseflow.shared.entities.settings.ApiConnectionSettingsTable
import com.baseflow.shared.entities.settings.ApiConnectionType
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.slf4j.LoggerFactory
import java.net.URI
import java.util.UUID
import kotlin.time.Clock

/**
 * Setting routes for managing API connection settings (API koppelingen).
 *
 * Mounted at `/settings/api-connection-settings`.
 *
 * Endpoints:
 * - `GET    /`      — list all settings
 * - `POST   /`      — create a setting
 * - `PUT    /{id}`  — update a setting
 * - `DELETE /{id}`  — delete a setting
 */
fun Route.apiKoppelingenRoutes() {
    route("/api-connection-settings") {
        get {
            val all = transaction {
                ApiConnectionSettingEntity.all().map { it.toResponse() }
            }
            call.respond(all)
        }

        post {
            val body = runCatching { call.receive<CreateApiConnectionSettingRequest>() }.getOrNull()
                ?: return@post call.respondProblem(
                    HttpStatusCode.BadRequest,
                    badRequest("Request body must be JSON with 'name', 'baseUrl', 'clientId', and 'apiType' fields.", call.request.path()),
                )
            validateApiConnectionFields(body.name, body.baseUrl, body.clientId, body.apiType, body.authType)?.let {
                return@post call.respondProblem(HttpStatusCode.BadRequest, badRequest(it, call.request.path()))
            }

            val created = transaction {
                val exists = ApiConnectionSettingEntity.find {
                    ApiConnectionSettingsTable.name eq body.name
                }.firstOrNull()
                if (exists != null) return@transaction null
                val now = Clock.System.now().toLocalDateTime(TimeZone.UTC)
                ApiConnectionSettingEntity.new {
                    name = body.name
                    baseUrl = body.baseUrl
                    clientId = body.clientId
                    clientSecret = body.clientSecret?.takeIf { it.isNotBlank() }
                    apiType = body.apiType
                    authType = body.authType
                    validationEnabled = body.validationEnabled
                    enabled = body.enabled
                    createdAt = now
                    updatedAt = now
                }.toResponse()
            } ?: return@post call.respondProblem(
                HttpStatusCode.Conflict,
                conflict("An API connection setting with this name already exists.", call.request.path()),
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
                val body = runCatching { call.receive<UpdateApiConnectionSettingRequest>() }.getOrNull()
                    ?: return@put call.respondProblem(
                        HttpStatusCode.BadRequest,
                        badRequest(
                            "Request body must be JSON with 'name', 'baseUrl', 'clientId', and 'apiType' fields.",
                            call.request.path(),
                        ),
                    )
                validateApiConnectionFields(body.name, body.baseUrl, body.clientId, body.apiType, body.authType)?.let {
                    return@put call.respondProblem(HttpStatusCode.BadRequest, badRequest(it, call.request.path()))
                }

                val outcome = transaction {
                    val existing = ApiConnectionSettingEntity.findById(id) ?: return@transaction PutOutcome.NotFound
                    if (existing.readonly) return@transaction PutOutcome.Readonly
                    val nameConflict = existing.name != body.name &&
                        ApiConnectionSettingEntity.find { ApiConnectionSettingsTable.name eq body.name }.firstOrNull() != null
                    if (nameConflict) return@transaction PutOutcome.Conflict
                    existing.name = body.name
                    existing.baseUrl = body.baseUrl
                    existing.clientId = body.clientId
                    if (!body.clientSecret.isNullOrBlank()) {
                        existing.clientSecret = body.clientSecret
                    }
                    existing.apiType = body.apiType
                    existing.authType = body.authType
                    existing.validationEnabled = body.validationEnabled
                    existing.enabled = body.enabled
                    existing.updatedAt = Clock.System.now().toLocalDateTime(TimeZone.UTC)
                    PutOutcome.Ok(existing.toResponse())
                }
                when (outcome) {
                    PutOutcome.NotFound -> call.respondProblem(
                        HttpStatusCode.NotFound,
                        notFound("API connection setting not found.", call.request.path()),
                    )
                    PutOutcome.Readonly -> call.respondProblem(
                        HttpStatusCode.Forbidden,
                        forbidden("This API connection setting is read-only and cannot be modified.", call.request.path()),
                    )
                    PutOutcome.Conflict -> call.respondProblem(
                        HttpStatusCode.Conflict,
                        conflict("An API connection setting with this name already exists.", call.request.path()),
                    )
                    is PutOutcome.Ok -> call.respond(HttpStatusCode.OK, outcome.response)
                }
            }

            delete {
                val id = call.parameters["id"]
                    ?.let { runCatching { UUID.fromString(it) }.getOrNull() }
                    ?: return@delete call.respondProblem(
                        HttpStatusCode.BadRequest,
                        badRequest("Invalid UUID.", call.request.path()),
                    )

                val outcome = transaction {
                    val existing = ApiConnectionSettingEntity.findById(id) ?: return@transaction DeleteOutcome.NotFound
                    if (existing.readonly) return@transaction DeleteOutcome.Readonly
                    existing.delete()
                    DeleteOutcome.Deleted
                }
                when (outcome) {
                    DeleteOutcome.NotFound -> call.respondProblem(
                        HttpStatusCode.NotFound,
                        notFound("API connection setting not found.", call.request.path()),
                    )
                    DeleteOutcome.Readonly -> call.respondProblem(
                        HttpStatusCode.Forbidden,
                        forbidden("This API connection setting is read-only and cannot be deleted.", call.request.path()),
                    )
                    DeleteOutcome.Deleted -> call.respond(HttpStatusCode.NoContent)
                }
            }
        }
    }
}

private val logger = LoggerFactory.getLogger("com.baseflow.settings.api.routes.ApiKoppelingenRoutes")

private fun ApiConnectionSettingEntity.toResponse(): ApiConnectionSettingResponse {
    val decryptedSecret = try {
        clientSecret
    } catch (e: Exception) {
        logger.error(
            "CRITICAL: Failed to decrypt clientSecret for API connection setting '$name' (${id.value}). " +
                "The encryption key or salt might have changed. " +
                "The secret must be re-entered to restore functionality.",
        )
        null
    }

    return ApiConnectionSettingResponse(
        id = id.value.toString(),
        name = name,
        baseUrl = baseUrl,
        clientId = clientId,
        hasSecret = decryptedSecret != null,
        clientSecret = decryptedSecret,
        apiType = apiType,
        authType = authType,
        validationEnabled = validationEnabled,
        enabled = enabled,
        readonly = readonly,
        createdAt = createdAt.toString(),
        updatedAt = updatedAt.toString(),
    )
}

private sealed class PutOutcome {
    data class Ok(val response: ApiConnectionSettingResponse) : PutOutcome()
    object NotFound : PutOutcome()
    object Readonly : PutOutcome()
    object Conflict : PutOutcome()
}

private sealed class DeleteOutcome {
    object Deleted : DeleteOutcome()
    object NotFound : DeleteOutcome()
    object Readonly : DeleteOutcome()
}

private fun validateApiConnectionFields(name: String, baseUrl: String, clientId: String, apiType: String, authType: String): String? {
    if (name.isBlank()) return "'name' must not be blank."
    if (name.length > 100) return "'name' must not exceed 100 characters."
    if (baseUrl.isBlank()) return "'baseUrl' must not be blank."
    if (!isValidHttpUrl(baseUrl)) return "'baseUrl' must be a valid http or https URL."
    if (ApiConnectionType.entries.none { it.value == apiType }) {
        return "'apiType' must be one of: ${ApiConnectionType.entries.joinToString { it.value }}."
    }
    if (ApiAuthType.entries.none { it.value == authType }) {
        return "'authType' must be one of: ${ApiAuthType.entries.joinToString { it.value }}."
    }
    val needsClientId = authType != ApiAuthType.NONE.value && authType != ApiAuthType.BEARER.value
    if (needsClientId && clientId.isBlank()) return "'clientId' must not be blank when authType is '$authType'."
    return null
}

private fun isValidHttpUrl(url: String): Boolean = runCatching {
    val uri = URI(url)
    uri.scheme in listOf("http", "https") && !uri.host.isNullOrEmpty()
}.getOrDefault(false)
