// SPDX-License-Identifier: EUPL-1.2
// Copyright (C) 2026 Gemeente Utrecht
@file:OptIn(ExperimentalKtorApi::class)

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
import io.ktor.openapi.jsonSchema
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.routing.openapi.describe
import io.ktor.utils.io.ExperimentalKtorApi
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
        }.describe {
            operationId = "application_settings_list"
            tag("application-settings")
            summary = "Lijst alle applicatie-instellingen op."
            description = "Geeft een lijst van alle geregistreerde applicatie-credentials " +
                "(clientId/secret paren) die gebruikt worden voor ZGW-authenticatie."
            responses {
                response(200) {
                    description = "Lijst van applicatie-instellingen."
                    ContentType.Application.Json { schema = jsonSchema<List<ApplicationSettingsResponse>>() }
                }
                response(401) { description = "Unauthorized." }
                response(403) { description = "Forbidden — de `dmf-admin` rol ontbreekt." }
            }
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
                if (ApplicationSettingEntity.find { ApplicationSettingsTable.name eq body.name }.firstOrNull() != null) {
                    return@transaction CreateResult.NameConflict
                }
                if (ApplicationSettingEntity.find { ApplicationSettingsTable.clientId eq body.clientId }.firstOrNull() != null) {
                    return@transaction CreateResult.ClientIdConflict
                }
                CreateResult.Success(
                    ApplicationSettingEntity.new {
                        name = body.name
                        clientId = body.clientId
                        clientSecret = body.clientSecret?.takeIf { it.isNotBlank() }
                        updatedAt = Clock.System.now()
                    }.toResponse(),
                )
            }
            when (created) {
                CreateResult.NameConflict -> return@post call.respondProblem(
                    HttpStatusCode.Conflict,
                    conflict("An application with this name already exists.", call.request.path()),
                )
                CreateResult.ClientIdConflict -> return@post call.respondProblem(
                    HttpStatusCode.Conflict,
                    conflict("An application with this clientId already exists.", call.request.path()),
                )
                is CreateResult.Success -> {
                    if (created.response.clientSecret != null) {
                        ApplicationCredentialRegistrar.registerSecret(created.response.clientId, created.response.clientSecret)
                    }
                    call.respond(HttpStatusCode.Created, created.response)
                }
            }
        }.describe {
            operationId = "application_settings_create"
            tag("application-settings")
            summary = "Maak een applicatie-instelling aan."
            description = "Registreert een nieuwe applicatie met een clientId en optioneel een clientSecret " +
                "voor ZGW-authenticatie."
            requestBody {
                required = true
                description = "Gegevens van de aan te maken applicatie."
                content {
                    schema = jsonSchema<CreateApplicationSettingsRequest>()
                }
            }
            responses {
                response(201) {
                    description = "Aangemaakt."
                    ContentType.Application.Json { schema = jsonSchema<ApplicationSettingsResponse>() }
                }
                response(400) { description = "Bad request — ontbrekend of ongeldig veld." }
                response(401) { description = "Unauthorized." }
                response(403) { description = "Forbidden — de `dmf-admin` rol ontbreekt." }
                response(409) { description = "Conflict — naam of clientId bestaat al." }
            }
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
                        ?: return@transaction UpdateResult.NotFound
                    if (existing.readonly) return@transaction UpdateResult.ReadOnly
                    previousClientId = existing.clientId
                    val nameConflict = existing.name != body.name &&
                        ApplicationSettingEntity.find { ApplicationSettingsTable.name eq body.name }
                            .firstOrNull() != null
                    if (nameConflict) return@transaction UpdateResult.NameConflict
                    val clientIdConflict = existing.clientId != body.clientId &&
                        ApplicationSettingEntity.find { ApplicationSettingsTable.clientId eq body.clientId }
                            .firstOrNull() != null
                    if (clientIdConflict) return@transaction UpdateResult.ClientIdConflict
                    existing.name = body.name
                    existing.clientId = body.clientId
                    if (!body.clientSecret.isNullOrBlank()) {
                        existing.clientSecret = body.clientSecret
                    }
                    existing.updatedAt = Clock.System.now()
                    UpdateResult.Success(existing.toResponse())
                }
                when (updated) {
                    UpdateResult.NotFound -> return@put call.respondProblem(
                        HttpStatusCode.NotFound,
                        notFound("Application not found.", call.request.path()),
                    )

                    UpdateResult.ReadOnly -> return@put call.respondProblem(
                        HttpStatusCode.Forbidden,
                        forbidden("This application setting is read-only and cannot be modified.", call.request.path()),
                    )

                    UpdateResult.NameConflict -> return@put call.respondProblem(
                        HttpStatusCode.Conflict,
                        conflict("An application with this name already exists.", call.request.path()),
                    )

                    UpdateResult.ClientIdConflict -> return@put call.respondProblem(
                        HttpStatusCode.Conflict,
                        conflict("An application with this clientId already exists.", call.request.path()),
                    )

                    is UpdateResult.Success -> {
                        val response = updated.response
                        if (previousClientId != response.clientId) {
                            ApplicationCredentialRegistrar.unregisterSecret(previousClientId)
                        }
                        if (response.clientSecret != null) {
                            ApplicationCredentialRegistrar.registerSecret(response.clientId, response.clientSecret)
                        }
                        call.respond(HttpStatusCode.OK, response)
                    }
                }
            }.describe {
                operationId = "application_settings_update"
                tag("application-settings")
                summary = "Werk een applicatie-instelling bij."
                description = "Vervangt de naam, clientId en optioneel het clientSecret van een bestaande applicatie. " +
                    "Als `clientSecret` weggelaten of `null` is, blijft het bestaande secret ongewijzigd."
                parameters {
                    path("id") {
                        description = "UUID van de applicatie-instelling."
                        required = true
                    }
                }
                requestBody {
                    required = true
                    description = "Bijgewerkte gegevens van de applicatie."
                    content {
                        schema = jsonSchema<UpdateApplicationSettingsRequest>()
                    }
                }
                responses {
                    response(200) {
                        description = "Bijgewerkt."
                        ContentType.Application.Json { schema = jsonSchema<ApplicationSettingsResponse>() }
                    }
                    response(400) { description = "Bad request — ontbrekend of ongeldig veld." }
                    response(401) { description = "Unauthorized." }
                    response(403) { description = "Forbidden — de `dmf-admin` rol ontbreekt of de instelling is readonly." }
                    response(404) { description = "Not found." }
                    response(409) { description = "Conflict — naam of clientId bestaat al." }
                }
            }

            delete {
                val id = call.parameters["id"]
                    ?.let { runCatching { UUID.fromString(it) }.getOrNull() }
                    ?: return@delete call.respondProblem(
                        HttpStatusCode.BadRequest,
                        badRequest("Invalid UUID.", call.request.path()),
                    )

                val result = transaction {
                    val existing = ApplicationSettingEntity.findById(id) ?: return@transaction DeleteResult.NotFound
                    if (existing.readonly) return@transaction DeleteResult.ReadOnly
                    val clientId = existing.clientId
                    existing.delete()
                    DeleteResult.Deleted(clientId)
                }

                when (result) {
                    DeleteResult.NotFound -> return@delete call.respondProblem(
                        HttpStatusCode.NotFound,
                        notFound("Application not found.", call.request.path()),
                    )

                    DeleteResult.ReadOnly -> return@delete call.respondProblem(
                        HttpStatusCode.Forbidden,
                        forbidden("This application setting is read-only and cannot be deleted.", call.request.path()),
                    )

                    is DeleteResult.Deleted -> {
                        ApplicationCredentialRegistrar.unregisterSecret(result.clientId)
                        call.respond(HttpStatusCode.NoContent)
                    }
                }
            }.describe {
                operationId = "application_settings_delete"
                tag("application-settings")
                summary = "Verwijder een applicatie-instelling."
                parameters {
                    path("id") {
                        description = "UUID van de applicatie-instelling."
                        required = true
                    }
                }
                responses {
                    response(204) { description = "Verwijderd." }
                    response(401) { description = "Unauthorized." }
                    response(403) { description = "Forbidden — de `dmf-admin` rol ontbreekt of de instelling is readonly." }
                    response(404) { description = "Not found." }
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

                val rotateResult = transaction {
                    val existing = ApplicationSettingEntity.findById(id) ?: return@transaction RotateResult.NotFound
                    if (existing.readonly) return@transaction RotateResult.ReadOnly
                    existing.clientSecret = plaintext
                    existing.updatedAt = Clock.System.now()
                    RotateResult.Success(existing.clientId)
                }

                when (rotateResult) {
                    RotateResult.NotFound -> return@post call.respondProblem(
                        HttpStatusCode.NotFound,
                        notFound("Application not found.", call.request.path()),
                    )

                    RotateResult.ReadOnly -> return@post call.respondProblem(
                        HttpStatusCode.Forbidden,
                        forbidden("This application setting is read-only and cannot be modified.", call.request.path()),
                    )

                    is RotateResult.Success -> {
                        ApplicationCredentialRegistrar.registerSecret(rotateResult.clientId, plaintext)
                        call.respond(HttpStatusCode.OK, RotateSecretResponse(secret = plaintext))
                    }
                }
            }.describe {
                operationId = "application_settings_rotate_secret"
                tag("application-settings")
                summary = "Roteer het clientSecret van een applicatie."
                description = "Vervangt het huidige clientSecret door een nieuw geheim. " +
                    "Als `newSecret` weggelaten of leeg is, wordt automatisch een 32-byte hex secret gegenereerd. " +
                    "Het nieuwe secret wordt eenmalig in de response teruggegeven en daarna nooit meer in plaintext opgeslagen."
                parameters {
                    path("id") {
                        description = "UUID van de applicatie-instelling."
                        required = true
                    }
                }
                requestBody {
                    required = false
                    description = "Optioneel nieuw secret. Weglaten om automatisch te genereren."
                    content {
                        schema = jsonSchema<RotateSecretRequest>()
                    }
                }
                responses {
                    response(200) {
                        description = "Secret geroteerd. Het nieuwe plaintext secret wordt eenmalig teruggegeven."
                        ContentType.Application.Json { schema = jsonSchema<RotateSecretResponse>() }
                    }
                    response(400) { description = "Bad request — ongeldige UUID." }
                    response(401) { description = "Unauthorized." }
                    response(403) { description = "Forbidden — de `dmf-admin` rol ontbreekt of de instelling is readonly." }
                    response(404) { description = "Not found." }
                }
            }
        }
    }
}

// ── Typed transaction results ─────────────────────────────────────────────────

private sealed interface CreateResult {
    data object NameConflict : CreateResult
    data object ClientIdConflict : CreateResult
    data class Success(val response: ApplicationSettingsResponse) : CreateResult
}

private sealed interface UpdateResult {
    data object NotFound : UpdateResult
    data object ReadOnly : UpdateResult
    data object NameConflict : UpdateResult
    data object ClientIdConflict : UpdateResult
    data class Success(val response: ApplicationSettingsResponse) : UpdateResult
}

private sealed interface DeleteResult {
    data object NotFound : DeleteResult
    data object ReadOnly : DeleteResult
    data class Deleted(val clientId: String) : DeleteResult
}

private sealed interface RotateResult {
    data object NotFound : RotateResult
    data object ReadOnly : RotateResult
    data class Success(val clientId: String) : RotateResult
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
