// SPDX-License-Identifier: EUPL-1.2
// Copyright (C) 2026 Gemeente Utrecht
package com.baseflow.api.settings.routes

import com.baseflow.api.models.settings.DmfSettingsResponse
import com.baseflow.api.models.ProblemDetailsResponse
import com.baseflow.api.models.settings.UpdateDmfSettingsRequest
import com.baseflow.api.models.badRequest
import com.baseflow.api.models.respondProblem
import com.baseflow.entities.settings.DmfSettingEntity
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

/**
 * Setting routes for managing DMF settings.
 *
 * Mounted at `/settings/dmf-settings`.
 *
 * Endpoints:
 * - `GET /`  — retrieve current settings
 * - `PUT /`  — update settings
 */
fun Route.dmfSettingsRoutes() {
    route("/dmf-settings") {
        get {
            val settings = transaction {
                DmfSettingEntity.findById(DmfSettingEntity.SINGLETON_ID)
            } ?: return@get call.respondProblem(
                HttpStatusCode.InternalServerError,
                ProblemDetailsResponse(
                    title = "Internal Server Error",
                    status = HttpStatusCode.InternalServerError.value,
                    detail = "DMF settings are not initialized.",
                    instance = call.request.path(),
                ),
            )
            call.respond(settings.toResponse())
        }

        put {
            val body = runCatching { call.receive<UpdateDmfSettingsRequest>() }.getOrNull()
                ?: return@put call.respondProblem(
                    HttpStatusCode.BadRequest,
                    badRequest(
                        "Request body must be JSON with 'triggerSize', 'chunkSize', and 'validationEnabled' fields.",
                        call.request.path(),
                    ),
                )
            if (body.triggerSize < 1) return@put call.respondProblem(
                HttpStatusCode.BadRequest,
                badRequest("'triggerSize' must be at least 1.", call.request.path()),
            )
            if (body.chunkSize < 1) return@put call.respondProblem(
                HttpStatusCode.BadRequest,
                badRequest("'chunkSize' must be at least 1.", call.request.path()),
            )

            val updated = transaction {
                val settings = DmfSettingEntity.findById(DmfSettingEntity.SINGLETON_ID)
                    ?: return@transaction null
                settings.triggerSizeBytes = body.triggerSize
                settings.chunkSizeBytes = body.chunkSize
                settings.validationEnabled = body.validationEnabled
                settings.updatedAt = Clock.System.now().toLocalDateTime(TimeZone.UTC)
                settings
            } ?: return@put call.respondProblem(
                HttpStatusCode.InternalServerError,
                ProblemDetailsResponse(
                    title = "Internal Server Error",
                    status = HttpStatusCode.InternalServerError.value,
                    detail = "DMF settings are not initialized.",
                    instance = call.request.path(),
                ),
            )

            call.respond(HttpStatusCode.OK, updated.toResponse())
        }
    }
}

private fun DmfSettingEntity.toResponse() = DmfSettingsResponse(
    triggerSize = triggerSizeBytes,
    chunkSize = chunkSizeBytes,
    validationEnabled = validationEnabled,
)
