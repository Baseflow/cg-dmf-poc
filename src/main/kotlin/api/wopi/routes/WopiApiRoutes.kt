// SPDX-License-Identifier: EUPL-1.2
// Copyright (C) 2026 Gemeente Utrecht
package com.baseflow.api.wopi.routes

import com.baseflow.api.WOPI_API_BASE_PATH
import com.baseflow.api.middleware.*
import com.baseflow.api.middleware.RequestScopeKey
import com.baseflow.api.models.EnkelvoudigInformatieObjectResponse
import com.baseflow.api.models.badRequest
import com.baseflow.api.models.conflict
import com.baseflow.api.models.notFound
import com.baseflow.api.models.notImplemented
import com.baseflow.api.models.respondProblem
import com.baseflow.api.wopi.WopiFileIdPlugin
import com.baseflow.api.wopi.WopiSlatAuthPlugin
import com.baseflow.api.wopi.WopiValidatedFileIdKey
import com.baseflow.api.wopi.models.CheckFileInfoResponse
import com.baseflow.api.wopi.models.WopiTokenResponse
import com.baseflow.config.WopiConfig
import com.baseflow.entities.EIORecordEntity
import com.baseflow.services.EnkelvoudigInformatieObjectService
import com.baseflow.services.WopiSlatService
import com.baseflow.services.models.wopi.WopiLockResult
import com.baseflow.services.models.wopi.WopiUnlockResult
import io.ktor.http.ContentDisposition
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.openapi.jsonSchema
import io.ktor.server.auth.AuthenticationStrategy
import io.ktor.server.auth.authenticate
import io.ktor.server.plugins.NotFoundException
import io.ktor.server.request.path
import io.ktor.server.request.receiveChannel
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.routing.openapi.describe
import io.ktor.utils.io.ExperimentalKtorApi
import io.ktor.utils.io.toByteArray
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

@OptIn(ExperimentalKtorApi::class)
fun Route.wopiApiRoutes() {
    install(AuditTrailPlugin)

    val slatService = WopiSlatService(
        secret = WopiConfig.slatSecret,
        ttlSeconds = WopiConfig.slatTtlSeconds,
    )

    route(WOPI_API_BASE_PATH) {
        install(WopiFileIdPlugin)

        // ── Token issuance ─────────────────────────────────────────────────────
        authenticate("auth-jwt", "auth-zgw", strategy = AuthenticationStrategy.FirstSuccessful) {
            post("/token/{file_id}") {
                issueToken(slatService)
            }.describe {
                operationId = "issueWopiToken"
                tag("wopi")
                summary = "Issue a WOPI access token."
                description =
                    "Issues a short-lived access token (SLAT) for the given EnkelvoudigInformatieObject. " +
                    "Pass the returned `access_token` as a query parameter when calling the WOPI file endpoints."
                parameters {
                    path("file_id") {
                        description = "The UUID of the EnkelvoudigInformatieObject to issue a token for."
                        required = true
                    }
                }
                responses {
                    response(200) {
                        description = "A short-lived access token."
                        ContentType.Application.Json { schema = jsonSchema<WopiTokenResponse>() }
                    }
                    response(400) { description = "Bad request." }
                    response(404) { description = "Document not found." }
                    response(500) { description = "Internal server error." }
                }
            }
        }

        // ── Protected file endpoints ───────────────────────────────────────────
        route("/files/{file_id}") {
            install(WopiFileIdPlugin)
            install(WopiSlatAuthPlugin) {
                this.slatService = slatService
            }

            get {
                getFileMetadata()
            }.describe {
                operationId = "getFileMetadata"
                tag("wopi")
                summary = "Get a file metadata."
                description = "Gets the metadata of a file. Requires a valid `access_token` query parameter."
                parameters {
                    path("file_id") {
                        description = "The UUID of the file to retrieve metadata for."
                        required = true
                    }
                    query("access_token") {
                        description = "Short-lived access token obtained from POST /token/{file_id}."
                        required = true
                    }
                }
                responses {
                    response(200) {
                        description = "Success."
                        ContentType.Application.Json { schema = jsonSchema<CheckFileInfoResponse>() }
                    }
                    response(401) { description = "Invalid access token." }
                    response(404) { description = "Resource not found or user unauthorized." }
                    response(500) { description = "Server error." }
                }
            }

            post {
                when (call.request.headers["X-WOPI-Override"]) {
                    "LOCK" -> lockFile()
                    "UNLOCK" -> unlockFile()
                    else -> call.respondProblem(
                        HttpStatusCode.NotImplemented,
                        badRequest("Unsupported X-WOPI-Override value", call.request.path()),
                    )
                }
            }.describe {
                tag("wopi")
                summary = "Locks/unlocks a file"
                description =
                    "The WOPI-client locks or unlocks a file, based on the `X-WOPI-Override` header. Supported values are LOCK and UNLOCK."
                parameters {
                    path("file_id") {
                        description = "The UUID of the file to lock/unlock."
                        required = true
                    }
                }
                responses {
                    response(200) { description = "Successfully locked/unlocked the file." }
                    response(400) { description = "Bad request." }
                    response(401) { description = "Unauthorized." }
                    response(403) { description = "Forbidden." }
                    response(404) { description = "Not found." }
                    response(409) { description = "Lock mismatch or locked by another interface." }
                    response(500) { description = "Internal server error." }
                }
            }

            route("/contents") {
                get {
                    getFileContents()
                }.describe {
                    operationId = "getFileContents"
                    tag("wopi")
                    summary = "Get file contents."
                    description = "Gets the contents of a file. Requires a valid `access_token` query parameter."
                    parameters {
                        path("file_id") {
                            description = "The UUID of the file to retrieve the contents for."
                            required = true
                        }
                        query("access_token") {
                            description = "Short-lived access token obtained from POST /token/{file_id}."
                            required = true
                        }
                    }
                    responses {
                        response(200) { description = "Success." }
                        response(401) { description = "Invalid access token." }
                        response(404) { description = "Resource not found or user unauthorized." }
                        response(412) { description = "File is larger than X-WOPI-MaxExpectedSize." }
                        response(500) { description = "Server error." }
                    }
                }

                post {
                    updateFileContents()
                }.describe {
                    operationId = "updateFileContents"
                    tag("wopi")
                    summary = "Update (Save) file contents."
                    description =
                        "Saves the contents of a file to the host. Requires a valid `access_token` query parameter."
                    parameters {
                        path("file_id") {
                            description = "The UUID of the file to save the contents for."
                            required = true
                        }
                        query("access_token") {
                            description = "Short-lived access token obtained from POST /token/{file_id}."
                            required = true
                        }
                    }
                    responses {
                        response(200) { description = "Success" }
                        response(401) { description = "Invalid access token." }
                        response(404) { description = "Resource not found or user unauthorized." }
                        response(409) {
                            description =
                                "Lock mismatch or locked by another interface. You must include an X-WOPI-Lock response header containing the value of the current lock on the file when using this response code."
                        }
                        response(413) { description = "File is too large. The maximum file size is host-specific." }
                        response(500) { description = "Server error." }
                        response(501) { description = "Operation not supported." }
                    }
                }
            }
        }
    }
}

private suspend fun RoutingContext.issueToken(slatService: WopiSlatService) {
    val uuid = call.attributes[WopiValidatedFileIdKey]

    val exists = transaction { EIORecordEntity.findById(uuid) != null }
    if (!exists) {
        call.respondProblem(
            HttpStatusCode.NotFound,
            notFound("EnkelvoudigInformatieObject not found", call.request.path()),
        )
        return
    }

    val (token, expiresAt) = slatService.issue(uuid)
    call.respond(HttpStatusCode.OK, WopiTokenResponse(accessToken = token, expiresAt = expiresAt))
}

private suspend fun RoutingContext.unlockFile() {
    val lock = call.request.headers["X-WOPI-Lock"]
    if (lock == null) {
        call.respondProblem(
            HttpStatusCode.BadRequest,
            badRequest("X-WOPI-Lock header is required but missing", call.request.path()),
        )
        return
    }

    val fileId = call.attributes[WopiValidatedFileIdKey]
    when (val result = service.wopiUnlock(fileId, lock)) {
        null -> call.respondProblem(HttpStatusCode.NotFound, notFound("File not found", call.request.path()))
        is WopiUnlockResult.Success -> call.respond(HttpStatusCode.OK)
        is WopiUnlockResult.NotLocked -> {
            call.response.header("X-WOPI-Lock", "")
            call.respondProblem(HttpStatusCode.Conflict, conflict("File is not locked", call.request.path()))
        }

        is WopiUnlockResult.LockMismatch -> {
            call.response.header("X-WOPI-Lock", result.currentFileLock.lock)
            call.respondProblem(
                HttpStatusCode.Conflict,
                conflict("Lock mismatch: file is locked with a different token", call.request.path()),
            )
        }
    }
}

private suspend fun RoutingContext.lockFile() {
    val lock = call.request.headers["X-WOPI-Lock"]
    if (lock == null) {
        call.respondProblem(
            HttpStatusCode.BadRequest,
            badRequest("X-WOPI-Lock header is required but missing", call.request.path()),
        )
        return
    }

    val fileId = call.attributes[WopiValidatedFileIdKey]
    when (val response = service.wopiLock(fileId, lock)) {
        null -> {
            call.respondProblem(HttpStatusCode.NotFound, notFound("File not found", call.request.path()))
            return
        }

        is WopiLockResult.Success -> call.respond(HttpStatusCode.OK)
        is WopiLockResult.AlreadyLocked -> {
            // TODO(elitsa): RefreshLock
            call.respond(HttpStatusCode.OK)
        }

        is WopiLockResult.LockMismatch -> {
            call.response.header("X-WOPI-Lock", response.currentFileLock.lock)
            call.respondProblem(
                HttpStatusCode.Conflict,
                conflict("Lock mismatch: file is locked with a different token"),
            )
        }
    }
}

private suspend fun RoutingContext.updateFileContents() {
    val wopiOverride = call.request.headers["X-WOPI-Override"]
    if (wopiOverride != "PUT") {
        call.respondProblem(
            HttpStatusCode.NotImplemented,
            notImplemented("Operation not supported.", call.request.path()),
        )
        return
    }

    val lockValue = call.request.headers["X-WOPI-Lock"]

    val validatedFileId = call.attributes[WopiValidatedFileIdKey]
    var currentFile: EnkelvoudigInformatieObjectResponse?

    try {
        currentFile = service.getById(validatedFileId)
    } catch (_: NotFoundException) {
        call.respondProblem(HttpStatusCode.NotFound, notFound("File not found", call.request.path()))
        return
    }

    try {
        val lockMismatch: String? = when {
            lockValue == null && (currentFile?.bestandsomvang ?: 0L) > 0L -> ""
            lockValue != null && lockValue != currentFile?.lock -> currentFile?.lock ?: ""
            else -> null
        }

        if (lockMismatch != null) {
            call.response.headers.append("X-WOPI-Lock", lockMismatch)
            call.respondProblem(HttpStatusCode.Conflict, conflict("Lock mismatch."))
            return
        }

        val responseHeaderLock = currentFile?.lock ?: ""
        val bytes = call.receiveChannel().toByteArray()
        val response = service.updateWithBytes(id = validatedFileId, bytes = bytes)
        if (response == null) {
            call.respondProblem(
                HttpStatusCode.NotFound,
                notFound("EnkelvoudigInformatieObject not found", call.request.path()),
            )
            return
        }
        call.response.headers.append("X-WOPI-Lock", responseHeaderLock)
        call.response.headers.append("X-WOPI-ItemVersion", response.versie.toString())
        call.respond(HttpStatusCode.OK, mapOf("LastModifiedTime" to response.beginRegistratie))
    } catch (e: IllegalArgumentException) {
        call.respondProblem(HttpStatusCode.BadRequest, badRequest(e.message ?: "Invalid input", call.request.path()))
    }
}

private suspend fun RoutingContext.getFileContents() {
    val fileId = call.attributes[WopiValidatedFileIdKey]
    val maxExpectedSize: Int = call.request.headers["X-WOPI-MaxExpectedSize"]?.let {
        try {
            it.toInt()
        } catch (_: NumberFormatException) {
            call.respondProblem(
                HttpStatusCode.PreconditionFailed,
                badRequest("File is larger than X-WOPI-MaxExpectedSize.", call.request.path()),
            )
            return
        }
    } ?: Int.MAX_VALUE

    val eio = transaction {
        val record = EIORecordEntity.findById(fileId) ?: return@transaction null
        record.versions.maxByOrNull { it.versie }
    }

    if (eio == null) {
        call.respondProblem(
            HttpStatusCode.NotFound,
            notFound("EnkelvoudigInformatieObject not found", call.request.path()),
        )
        return
    }

    val fileSize = eio.bestandsomvang ?: 0L
    if (fileSize > maxExpectedSize) {
        call.respond(HttpStatusCode.PreconditionFailed)
        return
    }

    val objectKey = eio.bestandsLocatie
    if (objectKey.isBlank()) {
        call.respondProblem(
            HttpStatusCode.NotFound,
            notFound("Document content not available for download", call.request.path()),
        )
        return
    }

    val fileName = eio.bestandsnaam.ifBlank { null } ?: eio.titel.ifBlank { null } ?: "document-${eio.id}"
    val contentType = try {
        eio.formaat?.let { ContentType.parse(it) }
    } catch (_: Exception) {
        ContentType.Application.OctetStream
    } ?: ContentType.Application.OctetStream

    call.response.headers.append(
        HttpHeaders.ContentDisposition,
        ContentDisposition.Attachment
            .withParameter(ContentDisposition.Parameters.FileNameAsterisk, fileName, true)
            .toString(),
    )
    call.response.headers.append(HttpHeaders.ContentType, contentType.toString())

    call.respondOutputStream {
        service.streamByBestandsnaam(bestandsnaam = objectKey, output = this, repoName = eio.bestandsRepository)
    }
}

private suspend fun RoutingContext.getFileMetadata() {
    val fileId = call.attributes[WopiValidatedFileIdKey]

    val result = service.getById(fileId, emptyList())

    if (result == null) {
        call.respondProblem(
            HttpStatusCode.NotFound,
            notFound("EnkelvoudigInformatieObject not found", call.request.path()),
        )
    } else if (result.bestandsomvang == null) {
        call.respondProblem(
            HttpStatusCode.NotFound,
            notFound("Document content not available", call.request.path()),
        )
    } else {
        val checkFileInfoResponse = CheckFileInfoResponse(
            baseFileName = result.bestandsnaam?.ifBlank { null } ?: result.titel.ifBlank { null } ?: "document",
            size = result.bestandsomvang,
            userCanWrite = true,
            supportsAutosave = false,
            userFriendlyName = "Unknown user",
            supportsLocks = true,
            supportsGetLock = true,
            supportsUpdate = true,
            lastModifiedTime = result.beginRegistratie,
            version = result.versie.toString(),
        )
        call.respond(HttpStatusCode.OK, checkFileInfoResponse)
    }
}

private val RoutingContext.service: EnkelvoudigInformatieObjectService
    get() = call.attributes[RequestScopeKey].get()
