// SPDX-License-Identifier: EUPL-1.2
// Copyright (C) 2026 Gemeente Utrecht
package com.baseflow.settings.api.routes

import com.baseflow.shared.api.models.badRequest
import com.baseflow.shared.api.models.conflict
import com.baseflow.shared.api.models.notFound
import com.baseflow.shared.api.models.respondProblem
import com.baseflow.shared.api.models.settings.DmfSettingEntry
import com.baseflow.shared.api.models.settings.UpsertDmfSettingRequest
import com.baseflow.shared.config.BestandsDeelConfig
import com.baseflow.shared.entities.settings.DmfSettingsTable
import com.baseflow.shared.services.DmfSettingsService
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.upsert
import kotlin.time.Clock

/**
 * Setting routes for DMF key/value settings.
 *
 * Mounted at `/settings/dmf-settings`.
 *
 * Endpoints:
 * - `GET    /`        — list all entries (sorted by key)
 * - `PUT    /{key}`   — upsert a single entry (key must be in [DmfSettingsTable.KNOWN_SETTINGS])
 * - `DELETE /{key}`   — delete an entry
 */
fun Route.dmfSettingsRoutes() {
    val readonlyKeys = BestandsDeelConfig.Default.envReadonlyKeys

    route("/dmf-settings") {
        get {
            val entries = transaction {
                DmfSettingsTable.selectAll()
                    .orderBy(DmfSettingsTable.key to SortOrder.ASC)
                    .map { row ->
                        DmfSettingEntry(
                            key = row[DmfSettingsTable.key],
                            type = row[DmfSettingsTable.type],
                            value = row[DmfSettingsTable.value],
                            updatedAt = row[DmfSettingsTable.updatedAt].toString(),
                            readonly = row[DmfSettingsTable.key] in readonlyKeys,
                        )
                    }
            }
            call.respond(entries)
        }

        route("/{key}") {
            put {
                val key = call.parameters["key"]
                    ?.takeIf { it.isNotBlank() }
                    ?: return@put call.respondProblem(
                        HttpStatusCode.BadRequest,
                        badRequest("Key must not be blank.", call.request.path()),
                    )

                val type = DmfSettingsTable.KNOWN_SETTINGS[key]
                    ?: return@put call.respondProblem(
                        HttpStatusCode.BadRequest,
                        badRequest(
                            "Unknown key '$key'. Known keys: ${DmfSettingsTable.KNOWN_SETTINGS.keys.sorted().joinToString()}.",
                            call.request.path(),
                        ),
                    )

                if (key in readonlyKeys) {
                    return@put call.respondProblem(
                        HttpStatusCode.Conflict,
                        conflict(
                            "Setting '$key' is readonly (managed by environment variable) and cannot be changed via the API.",
                            call.request.path(),
                        ),
                    )
                }

                val body = runCatching { call.receive<UpsertDmfSettingRequest>() }.getOrNull()
                    ?: return@put call.respondProblem(
                        HttpStatusCode.BadRequest,
                        badRequest("Request body must be JSON with a 'value' field.", call.request.path()),
                    )

                when (type) {
                    "int" -> {
                        val longValue = body.value.toLongOrNull()
                            ?: return@put call.respondProblem(
                                HttpStatusCode.BadRequest,
                                badRequest("'value' must be a valid integer for key '$key'.", call.request.path()),
                            )
                        val minValue = DmfSettingsTable.KEY_MIN_VALUES[key]
                        if (minValue != null && longValue < minValue) {
                            return@put call.respondProblem(
                                HttpStatusCode.BadRequest,
                                badRequest("'value' for key '$key' must be at least $minValue.", call.request.path()),
                            )
                        }
                    }
                    "boolean" -> if (body.value != "true" && body.value != "false") {
                        return@put call.respondProblem(
                            HttpStatusCode.BadRequest,
                            badRequest("'value' must be 'true' or 'false' for key '$key'.", call.request.path()),
                        )
                    }
                    else -> Unit // "string" and future types: any value is accepted
                }

                val now = Clock.System.now()
                val entry = transaction {
                    DmfSettingsTable.upsert {
                        it[DmfSettingsTable.key] = key
                        it[DmfSettingsTable.type] = type
                        it[DmfSettingsTable.value] = body.value
                        it[DmfSettingsTable.updatedAt] = now
                    }
                    DmfSettingEntry(key = key, type = type, value = body.value, updatedAt = now.toString(), readonly = key in readonlyKeys)
                }

                DmfSettingsService.invalidateCache()
                call.respond(HttpStatusCode.OK, entry)
            }

            delete {
                val key = call.parameters["key"]
                    ?.takeIf { it.isNotBlank() }
                    ?: return@delete call.respondProblem(
                        HttpStatusCode.BadRequest,
                        badRequest("Key must not be blank.", call.request.path()),
                    )

                if (key !in DmfSettingsTable.KNOWN_SETTINGS) {
                    return@delete call.respondProblem(
                        HttpStatusCode.BadRequest,
                        badRequest(
                            "Unknown key '$key'. Known keys: ${DmfSettingsTable.KNOWN_SETTINGS.keys.sorted().joinToString()}.",
                            call.request.path(),
                        ),
                    )
                }

                if (key in readonlyKeys) {
                    return@delete call.respondProblem(
                        HttpStatusCode.Conflict,
                        conflict(
                            "Setting '$key' is readonly (managed by environment variable) and cannot be changed via the API.",
                            call.request.path(),
                        ),
                    )
                }

                val deleted = transaction {
                    DmfSettingsTable.deleteWhere { DmfSettingsTable.key eq key } > 0
                }

                if (!deleted) {
                    return@delete call.respondProblem(
                        HttpStatusCode.NotFound,
                        notFound("Setting '$key' not found.", call.request.path()),
                    )
                }

                DmfSettingsService.invalidateCache()
                call.respond(HttpStatusCode.NoContent)
            }
        }
    }
}
