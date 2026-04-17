// SPDX-License-Identifier: EUPL-1.2
// Copyright (C) 2026 Gemeente Utrecht
package com.baseflow.api.routes

import com.baseflow.api.models.OidcSettingsResponse
import com.baseflow.api.models.UpdateOidcSettingsRequest
import com.baseflow.api.models.badRequest
import com.baseflow.api.models.notFound
import com.baseflow.api.models.respondProblem
import com.baseflow.config.OidcCrypto
import com.baseflow.entities.OidcSettingsEntity
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.util.UUID

private val SETTINGS_ID = UUID.fromString("00000000-0000-0000-0000-000000000001")

/**
 * Admin routes for managing OIDC provider settings.
 *
 * Mounted at `/admin/oidc-settings`.
 *
 * Endpoints:
 * - `GET  /` — get current OIDC settings (404 if not yet configured)
 * - `PUT  /` — create or update OIDC settings
 */
fun Route.oidcSettingsRoutes() {
    route("/oidc-settings") {
        /**
         * Geeft de huidige OIDC-instellingen.
         *
         * Responses:
         *   - 200 De huidige OIDC-instellingen.
         *   - 404 Nog geen OIDC-instellingen geconfigureerd.
         *
         * @tag Admin
         */
        get {
            val settings = transaction {
                OidcSettingsEntity.findById(SETTINGS_ID)
            } ?: return@get call.respondProblem(
                HttpStatusCode.NotFound,
                notFound("OIDC settings have not been configured yet.", call.request.path()),
            )
            call.respond(settings.toResponse())
        }

        /**
         * Maakt of overschrijft de OIDC-instellingen.
         *
         * Responses:
         *   - 200 De bijgewerkte OIDC-instellingen.
         *   - 400 Ongeldige aanvraag.
         *
         * @tag Admin
         */
        put {
            val body = runCatching { call.receive<UpdateOidcSettingsRequest>() }.getOrNull()
                ?: return@put call.respondProblem(
                    HttpStatusCode.BadRequest,
                    badRequest("Request body must be JSON with 'issuer' and 'clientId' fields.", call.request.path()),
                )

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
                val existing = OidcSettingsEntity.findById(SETTINGS_ID)
                if (existing != null) {
                    existing.issuer = body.issuer
                    existing.clientId = body.clientId
                    if (!body.clientSecret.isNullOrBlank()) {
                        existing.clientSecretEncrypted = OidcCrypto.encrypt(body.clientSecret)
                    }
                    existing.updatedAt = Clock.System.now().toLocalDateTime(TimeZone.UTC)
                    existing
                } else {
                    OidcSettingsEntity.new(SETTINGS_ID) {
                        issuer = body.issuer
                        clientId = body.clientId
                        clientSecretEncrypted = body.clientSecret
                            ?.takeIf { it.isNotBlank() }
                            ?.let { OidcCrypto.encrypt(it) }
                        updatedAt = Clock.System.now().toLocalDateTime(TimeZone.UTC)
                    }
                }
            }
            call.respond(HttpStatusCode.OK, updated.toResponse())
        }
    }
}

private fun OidcSettingsEntity.toResponse() = OidcSettingsResponse(
    issuer = issuer,
    clientId = clientId,
    hasSecret = clientSecretEncrypted != null,
    updatedAt = updatedAt.toString(),
)
