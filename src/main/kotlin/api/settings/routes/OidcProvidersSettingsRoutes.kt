// SPDX-License-Identifier: EUPL-1.2
// Copyright (C) 2026 Gemeente Utrecht
package com.baseflow.api.settings.routes

import com.baseflow.api.models.badRequest
import com.baseflow.api.models.conflict
import com.baseflow.api.models.notFound
import com.baseflow.api.models.respondProblem
import com.baseflow.api.models.settings.CreateOidcProviderSettingsRequest
import com.baseflow.api.models.settings.OidcProviderSettingsResponse
import com.baseflow.api.models.settings.UpdateOidcProviderSettingsRequest
import com.baseflow.config.SecretCrypto
import com.baseflow.entities.settings.OidcProviderSettingEntity
import com.baseflow.entities.settings.OidcProviderSettingsTable
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
 * Setting routes for managing OIDC provider configurations.
 *
 * Mounted at `/settings/oidc-providers`.
 *
 * Endpoints:
 * - `GET    /`      — list all providers
 * - `POST   /`      — create a provider
 * - `PUT    /{id}`  — update a provider
 * - `DELETE /{id}`  — delete a provider
 */
fun Route.oidcProviderSettingsRoutes() {
    route("/oidc-providers") {
        get {
            val providers = transaction {
                OidcProviderSettingEntity.all().map { it.toResponse() }
            }
            call.respond(providers)
        }

        post {
            val body = runCatching { call.receive<CreateOidcProviderSettingsRequest>() }.getOrNull()
                ?: return@post call.respondProblem(
                    HttpStatusCode.BadRequest,
                    badRequest("Request body must be JSON with 'name', 'issuer', and 'clientId' fields.", call.request.path()),
                )
            if (body.name.isBlank()) {
                return@post call.respondProblem(
                    HttpStatusCode.BadRequest,
                    badRequest("'name' must not be blank.", call.request.path()),
                )
            }
            if (body.issuer.isBlank()) {
                return@post call.respondProblem(
                    HttpStatusCode.BadRequest,
                    badRequest("'issuer' must not be blank.", call.request.path()),
                )
            }
            if (body.clientId.isBlank()) {
                return@post call.respondProblem(
                    HttpStatusCode.BadRequest,
                    badRequest("'clientId' must not be blank.", call.request.path()),
                )
            }

            val created = transaction {
                val exists = OidcProviderSettingEntity.find {
                    OidcProviderSettingsTable.name eq body.name
                }.firstOrNull()
                if (exists != null) return@transaction null
                OidcProviderSettingEntity.new {
                    name = body.name
                    issuer = body.issuer
                    clientId = body.clientId
                    clientSecretEncrypted = body.clientSecret
                        ?.takeIf { it.isNotBlank() }
                        ?.let { SecretCrypto.encrypt(it) }
                    updatedAt = Clock.System.now().toLocalDateTime(TimeZone.UTC)
                }
            } ?: return@post call.respondProblem(
                HttpStatusCode.Conflict,
                conflict("A provider with this name already exists.", call.request.path()),
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
                val body = runCatching { call.receive<UpdateOidcProviderSettingsRequest>() }.getOrNull()
                    ?: return@put call.respondProblem(
                        HttpStatusCode.BadRequest,
                        badRequest("Request body must be JSON with 'name', 'issuer', and 'clientId' fields.", call.request.path()),
                    )
                if (body.name.isBlank()) {
                    return@put call.respondProblem(
                        HttpStatusCode.BadRequest,
                        badRequest("'name' must not be blank.", call.request.path()),
                    )
                }
                if (body.issuer.isBlank()) {
                    return@put call.respondProblem(
                        HttpStatusCode.BadRequest,
                        badRequest("'issuer' must not be blank.", call.request.path()),
                    )
                }
                if (body.clientId.isBlank()) {
                    return@put call.respondProblem(
                        HttpStatusCode.BadRequest,
                        badRequest("'clientId' must not be blank.", call.request.path()),
                    )
                }

                val updated = transaction {
                    val existing = OidcProviderSettingEntity.findById(id)
                        ?: return@transaction null
                    val nameConflict = existing.name != body.name &&
                        OidcProviderSettingEntity.find { OidcProviderSettingsTable.name eq body.name }.firstOrNull() != null
                    if (nameConflict) return@transaction "conflict"
                    existing.name = body.name
                    existing.issuer = body.issuer
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
                        notFound("OIDC provider not found.", call.request.path()),
                    )
                    "conflict" -> return@put call.respondProblem(
                        HttpStatusCode.Conflict,
                        conflict("A provider with this name already exists.", call.request.path()),
                    )
                    else -> call.respond(HttpStatusCode.OK, (updated as OidcProviderSettingEntity).toResponse())
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
                    val existing = OidcProviderSettingEntity.findById(id) ?: return@transaction false
                    existing.delete()
                    true
                }

                if (!deleted) {
                    return@delete call.respondProblem(
                        HttpStatusCode.NotFound,
                        notFound("OIDC provider not found.", call.request.path()),
                    )
                }

                call.respond(HttpStatusCode.NoContent)
            }
        }
    }
}

private fun OidcProviderSettingEntity.toResponse() = OidcProviderSettingsResponse(
    id = id.value.toString(),
    name = name,
    issuer = issuer,
    clientId = clientId,
    hasSecret = clientSecretEncrypted != null,
    clientSecret = clientSecretEncrypted?.let { SecretCrypto.decrypt(it) },
    updatedAt = updatedAt.toString(),
)
