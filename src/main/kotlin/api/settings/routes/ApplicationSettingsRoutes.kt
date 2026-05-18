// SPDX-License-Identifier: EUPL-1.2
// Copyright (C) 2026 Gemeente Utrecht
package com.baseflow.api.settings.routes

import com.baseflow.api.models.badRequest
import com.baseflow.api.models.conflict
import com.baseflow.api.models.notFound
import com.baseflow.api.models.respondProblem
import com.baseflow.api.models.settings.ApplicationSettingsResponse
import com.baseflow.api.models.settings.CreateApplicationSettingsRequest
import com.baseflow.api.models.settings.RotateSecretRequest
import com.baseflow.api.models.settings.RotateSecretResponse
import com.baseflow.api.models.settings.UpdateApplicationSettingsRequest
import com.baseflow.config.SecretCrypto
import com.baseflow.entities.settings.ApplicationSettingEntity
import com.baseflow.entities.settings.ApplicationSettingsTable
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.security.SecureRandom
import java.util.UUID

/**
 * Setting routes for managing application credential configurations.
 *
 * Mounted at `/settings/application-settings`.
 *
 * Endpoints:
 * - `GET    /`                    — list all applications
 * - `POST   /`                    — create an application
 * - `PUT    /{id}`                — update an application
 * - `DELETE /{id}`                — delete an application
 * - `POST   /{id}/rotate-secret`  — rotate the client secret
 */
fun Route.applicationSettingsRoutes() {
    route("/application-settings") {
        get {
            val applications = transaction {
                ApplicationSettingEntity.all().map { it.toResponse() }
            }
            call.respond(applications)
        }

        post {
            val body = runCatching { call.receive<CreateApplicationSettingsRequest>() }.getOrNull()
                ?: return@post call.respondProblem(
                    HttpStatusCode.BadRequest,
                    badRequest("Request body must be JSON with 'name' and 'clientId' fields.", call.request.path()),
                )
            if (body.name.isBlank()) {
                return@post call.respondProblem(
                    HttpStatusCode.BadRequest,
                    badRequest("'name' must not be blank.", call.request.path()),
                )
            }
            if (body.clientId.isBlank()) {
                return@post call.respondProblem(
                    HttpStatusCode.BadRequest,
                    badRequest("'clientId' must not be blank.", call.request.path()),
                )
            }

            val created = transaction {
                val exists = ApplicationSettingEntity.find {
                    ApplicationSettingsTable.name eq body.name
                }.firstOrNull()
                if (exists != null) return@transaction null
                ApplicationSettingEntity.new {
                    name = body.name
                    clientId = body.clientId
                    clientSecretEncrypted = body.clientSecret
                        ?.takeIf { it.isNotBlank() }
                        ?.let { SecretCrypto.encrypt(it) }
                    updatedAt = Clock.System.now().toLocalDateTime(TimeZone.UTC)
                }
            } ?: return@post call.respondProblem(
                HttpStatusCode.Conflict,
                conflict("An application with this name already exists.", call.request.path()),
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
                val body = runCatching { call.receive<UpdateApplicationSettingsRequest>() }.getOrNull()
                    ?: return@put call.respondProblem(
                        HttpStatusCode.BadRequest,
                        badRequest("Request body must be JSON with 'name' and 'clientId' fields.", call.request.path()),
                    )
                if (body.name.isBlank()) {
                    return@put call.respondProblem(
                        HttpStatusCode.BadRequest,
                        badRequest("'name' must not be blank.", call.request.path()),
                    )
                }
                if (body.clientId.isBlank()) {
                    return@put call.respondProblem(
                        HttpStatusCode.BadRequest,
                        badRequest("'clientId' must not be blank.", call.request.path()),
                    )
                }

                val updated = transaction {
                    val existing = ApplicationSettingEntity.findById(id)
                        ?: return@transaction null
                    val nameConflict = existing.name != body.name &&
                        ApplicationSettingEntity.find { ApplicationSettingsTable.name eq body.name }.firstOrNull() != null
                    if (nameConflict) return@transaction "conflict"
                    existing.name = body.name
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
                        notFound("Application not found.", call.request.path()),
                    )
                    "conflict" -> return@put call.respondProblem(
                        HttpStatusCode.Conflict,
                        conflict("An application with this name already exists.", call.request.path()),
                    )
                    else -> call.respond(HttpStatusCode.OK, (updated as ApplicationSettingEntity).toResponse())
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
                    val existing = ApplicationSettingEntity.findById(id) ?: return@transaction false
                    existing.delete()
                    true
                }

                if (!deleted) {
                    return@delete call.respondProblem(
                        HttpStatusCode.NotFound,
                        notFound("Application not found.", call.request.path()),
                    )
                }

                call.respond(HttpStatusCode.NoContent)
            }

            post("/rotate-secret") {
                val id = call.parameters["id"]
                    ?.let { runCatching { UUID.fromString(it) }.getOrNull() }
                    ?: return@post call.respondProblem(
                        HttpStatusCode.BadRequest,
                        badRequest("Invalid UUID.", call.request.path()),
                    )
                val body = runCatching { call.receive<RotateSecretRequest>() }.getOrElse { RotateSecretRequest() }

                val plaintext = body.newSecret?.takeIf { it.isNotBlank() } ?: generateSecret()

                val found = transaction {
                    val existing = ApplicationSettingEntity.findById(id) ?: return@transaction false
                    existing.clientSecretEncrypted = SecretCrypto.encrypt(plaintext)
                    existing.updatedAt = Clock.System.now().toLocalDateTime(TimeZone.UTC)
                    true
                }

                if (!found) {
                    return@post call.respondProblem(
                        HttpStatusCode.NotFound,
                        notFound("Application not found.", call.request.path()),
                    )
                }

                call.respond(HttpStatusCode.OK, RotateSecretResponse(secret = plaintext))
            }
        }
    }
}

private val secureRandom = SecureRandom()

private fun generateSecret(): String {
    val bytes = ByteArray(32)
    secureRandom.nextBytes(bytes)
    return bytes.joinToString("") { "%02x".format(it) }
}

private fun ApplicationSettingEntity.toResponse() = ApplicationSettingsResponse(
    id = id.value.toString(),
    name = name,
    clientId = clientId,
    hasSecret = clientSecretEncrypted != null,
    clientSecret = clientSecretEncrypted?.let { SecretCrypto.decrypt(it) },
    updatedAt = updatedAt.toString(),
)
