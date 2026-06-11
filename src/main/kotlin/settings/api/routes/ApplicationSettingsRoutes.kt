// SPDX-License-Identifier: EUPL-1.2
// Copyright (C) 2026 Gemeente Utrecht
package com.baseflow.settings.api.routes

import com.baseflow.shared.api.models.badRequest
import com.baseflow.shared.api.models.conflict
import com.baseflow.shared.api.models.forbidden
import com.baseflow.shared.api.models.notFound
import com.baseflow.shared.api.models.respondProblem
import com.baseflow.shared.api.models.settings.ApplicationSettingsResponse
import com.baseflow.shared.api.models.settings.CreateApplicationSettingsRequest
import com.baseflow.shared.api.models.settings.RotateSecretRequest
import com.baseflow.shared.api.models.settings.RotateSecretResponse
import com.baseflow.shared.api.models.settings.UpdateApplicationSettingsRequest
import com.baseflow.shared.entities.settings.ApplicationSettingEntity
import com.baseflow.shared.entities.settings.ApplicationSettingsTable
import com.baseflow.shared.services.ApplicationCredentialRegistrar
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.slf4j.LoggerFactory
import java.security.SecureRandom
import java.util.UUID
import kotlin.time.Clock

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
                    clientSecret = body.clientSecret
                        ?.takeIf { it.isNotBlank() }
                    updatedAt = Clock.System.now().toLocalDateTime(TimeZone.UTC)
                }.toResponse()
            } ?: return@post call.respondProblem(
                HttpStatusCode.Conflict,
                conflict("An application with this name already exists.", call.request.path()),
            )
            if (created.clientSecret != null) {
                ApplicationCredentialRegistrar.registerSecret(created.clientId, created.clientSecret)
            }
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

                var previousClientId = ""
                val updated = transaction {
                    val existing = ApplicationSettingEntity.findById(id)
                        ?: return@transaction null
                    if (existing.readonly) return@transaction "readonly"
                    previousClientId = existing.clientId
                    val nameConflict = existing.name != body.name &&
                        ApplicationSettingEntity.find { ApplicationSettingsTable.name eq body.name }
                            .firstOrNull() != null
                    if (nameConflict) return@transaction "conflict"
                    existing.name = body.name
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
                        notFound("Application not found.", call.request.path()),
                    )

                    "readonly" -> return@put call.respondProblem(
                        HttpStatusCode.Forbidden,
                        forbidden("This application setting is read-only and cannot be modified.", call.request.path()),
                    )

                    "conflict" -> return@put call.respondProblem(
                        HttpStatusCode.Conflict,
                        conflict("An application with this name already exists.", call.request.path()),
                    )

                    else -> {
                        val response = updated as ApplicationSettingsResponse
                        if (previousClientId != response.clientId) {
                            ApplicationCredentialRegistrar.unregisterSecret(previousClientId)
                        }
                        if (response.clientSecret != null) {
                            ApplicationCredentialRegistrar.registerSecret(response.clientId, response.clientSecret)
                        }
                        call.respond(HttpStatusCode.OK, response)
                    }
                }
            }

            delete {
                val id = call.parameters["id"]
                    ?.let { runCatching { UUID.fromString(it) }.getOrNull() }
                    ?: return@delete call.respondProblem(
                        HttpStatusCode.BadRequest,
                        badRequest("Invalid UUID.", call.request.path()),
                    )

                val deletedClientId = transaction {
                    val existing = ApplicationSettingEntity.findById(id) ?: return@transaction null
                    if (existing.readonly) return@transaction "readonly"
                    val clientId = existing.clientId
                    existing.delete()
                    clientId
                }

                when (deletedClientId) {
                    null -> return@delete call.respondProblem(
                        HttpStatusCode.NotFound,
                        notFound("Application not found.", call.request.path()),
                    )

                    "readonly" -> return@delete call.respondProblem(
                        HttpStatusCode.Forbidden,
                        forbidden("This application setting is read-only and cannot be deleted.", call.request.path()),
                    )

                    else -> {
                        // Remove the secret from cache
                        ApplicationCredentialRegistrar.unregisterSecret(deletedClientId)
                        call.respond(HttpStatusCode.NoContent)
                    }
                }
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

                val clientId = transaction {
                    val existing = ApplicationSettingEntity.findById(id) ?: return@transaction null
                    if (existing.readonly) return@transaction "readonly"
                    existing.clientSecret = plaintext
                    existing.updatedAt = Clock.System.now().toLocalDateTime(TimeZone.UTC)
                    existing.clientId
                }

                if (clientId == null) {
                    return@post call.respondProblem(
                        HttpStatusCode.NotFound,
                        notFound("Application not found.", call.request.path()),
                    )
                }

                if (clientId == "readonly") {
                    return@post call.respondProblem(
                        HttpStatusCode.Forbidden,
                        forbidden("This application setting is read-only and cannot be modified.", call.request.path()),
                    )
                }

                ApplicationCredentialRegistrar.registerSecret(clientId, plaintext)
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

private val logger = LoggerFactory.getLogger("com.baseflow.settings.api.routes.ApplicationSettingsRoutes")

private fun ApplicationSettingEntity.toResponse(): ApplicationSettingsResponse {
    val decryptedSecret = try {
        clientSecret
    } catch (_: Exception) {
        logger.error(
            "CRITICAL: Failed to decrypt clientSecret for application '$name' (${id.value}). " +
                "The encryption key or salt might have changed. " +
                "The secret must be re-entered to restore functionality.",
        )
        null
    }

    return ApplicationSettingsResponse(
        id = id.value.toString(),
        name = name,
        clientId = clientId,
        hasSecret = decryptedSecret != null,
        clientSecret = decryptedSecret,
        readonly = readonly,
        updatedAt = updatedAt.toString(),
    )
}
