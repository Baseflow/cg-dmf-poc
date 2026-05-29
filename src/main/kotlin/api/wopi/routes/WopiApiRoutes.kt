// SPDX-License-Identifier: EUPL-1.2
// Copyright (C) 2026 Gemeente Utrecht
package com.baseflow.api.wopi.routes

import com.baseflow.api.WOPI_API_BASE_PATH
import com.baseflow.api.middleware.*
import com.baseflow.api.models.badRequest
import com.baseflow.api.models.conflict
import com.baseflow.api.models.notFound
import com.baseflow.api.models.notImplemented
import com.baseflow.api.models.respondProblem
import com.baseflow.api.wopi.WopiFileIdPlugin
import com.baseflow.api.wopi.WopiSlatAuthPlugin
import com.baseflow.api.wopi.WopiValidatedFileIdKey
import com.baseflow.api.wopi.models.CheckFileInfoResponse
import com.baseflow.api.wopi.models.PutRelativeFileResponse
import com.baseflow.api.wopi.models.RenameFileResponse
import com.baseflow.api.wopi.models.WopiDeleteResult
import com.baseflow.api.wopi.models.WopiLockResult
import com.baseflow.api.wopi.models.WopiPutFileResult
import com.baseflow.api.wopi.models.WopiPutRelativeFileResult
import com.baseflow.api.wopi.models.WopiRenameResult
import com.baseflow.api.wopi.models.WopiTokenResponse
import com.baseflow.api.wopi.models.WopiUnlockResult
import com.baseflow.api.wopi.services.WopiDocumentService
import com.baseflow.config.WopiConfig
import com.baseflow.services.EnkelvoudigInformatieObjectService
import com.baseflow.services.WopiSlatService
import io.ktor.http.ContentDisposition
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.openapi.jsonSchema
import io.ktor.server.auth.AuthenticationStrategy
import io.ktor.server.auth.authenticate
import io.ktor.server.request.path
import io.ktor.server.request.receiveChannel
import io.ktor.server.response.*
import io.ktor.server.routing.Route
import io.ktor.server.routing.RoutingContext
import io.ktor.server.routing.get
import io.ktor.server.routing.openapi.describe
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.utils.io.ExperimentalKtorApi
import io.ktor.utils.io.jvm.javaio.toInputStream
import io.ktor.utils.io.toByteArray
import org.koin.core.parameter.parametersOf
import org.koin.ktor.plugin.scope

/**
 * Extension property to get the WopiSlatService from the DI container.
 */
private val RoutingContext.slatService: WopiSlatService
    get() = call.scope.get<WopiSlatService> {
        val config = call.scope.get<WopiConfig>()
        parametersOf(
            config.slatSecret,
            config.slatTtlSeconds,
        )
    }

/**
 * Extension property to get the EnkelvoudigInformatieObjectService from the DI container.
 */
private val RoutingContext.service: EnkelvoudigInformatieObjectService
    get() = call.scope.get<EnkelvoudigInformatieObjectService>()

/**
 * Extension property to get the WopiDocumentService from the DI container.
 */
private val RoutingContext.wopiService: WopiDocumentService
    get() = call.scope.get<WopiDocumentService>()

@OptIn(ExperimentalKtorApi::class)
fun Route.wopiApiRoutes() {
    install(AuditTrailPlugin)

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
            install(WopiSlatAuthPlugin)

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
                    "RENAME_FILE" -> renameFile()
                    "DELETE" -> deleteFile()
                    "PUT_RELATIVE" -> putRelativeFile()
                    else -> call.respondProblem(
                        HttpStatusCode.NotImplemented,
                        badRequest("Unsupported X-WOPI-Override value", call.request.path()),
                    )
                }
            }.describe {
                tag("wopi")
                summary = "Issues a WOPI operation"
                description =
                    "The WOPI-client issues a certain WOPI operation, based on the `X-WOPI-Override` header. " +
                    "Supported values are: LOCK, UNLOCK, RENAME_FILE, DELETE, PUT_RELATIVE. " +
                    "Requires a valid `access_token` query parameter and, depending on the operation, additional headers (see below)."
                parameters {
                    path("file_id") {
                        description = "The UUID of the file to lock/unlock."
                        required = true
                    }
                }
                responses {
                    response(200) { description = "WOPI operation successful." }
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

    if (!service.exists(uuid)) {
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
    when (val result = wopiService.wopiUnlock(fileId, lock)) {
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
    when (val response = wopiService.wopiLock(fileId, lock)) {
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
    val bytes = call.receiveChannel().toByteArray()

    when (val result = wopiService.wopiPutFile(id = validatedFileId, bytes = bytes, lockValue = lockValue)) {
        is WopiPutFileResult.NotFound ->
            call.respondProblem(
                HttpStatusCode.NotFound,
                notFound("EnkelvoudigInformatieObject not found", call.request.path()),
            )

        is WopiPutFileResult.LockRequired -> {
            call.response.headers.append("X-WOPI-Lock", "")
            call.respondProblem(HttpStatusCode.Conflict, conflict("Lock mismatch."))
        }

        is WopiPutFileResult.LockMismatch -> {
            call.response.headers.append("X-WOPI-Lock", result.currentLock)
            call.respondProblem(HttpStatusCode.Conflict, conflict("Lock mismatch."))
        }

        is WopiPutFileResult.Success -> {
            call.response.headers.append("X-WOPI-Lock", lockValue ?: "")
            call.response.headers.append("X-WOPI-ItemVersion", result.response.versie.toString())
            call.respond(HttpStatusCode.OK, mapOf("LastModifiedTime" to result.response.beginRegistratie))
        }
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

    val fileVersion = wopiService.wopiGetFileVersion(fileId)
    if (fileVersion == null) {
        call.respondProblem(
            HttpStatusCode.NotFound,
            notFound("EnkelvoudigInformatieObject not found", call.request.path()),
        )
        return
    }

    if (fileVersion.bestandsomvang > maxExpectedSize) {
        call.respond(HttpStatusCode.PreconditionFailed)
        return
    }

    if (fileVersion.bestandsLocatie.isBlank()) {
        call.respondProblem(
            HttpStatusCode.NotFound,
            notFound("Document content not available for download", call.request.path()),
        )
        return
    }

    val fileName = fileVersion.bestandsnaam.ifBlank { null } ?: fileVersion.titel.ifBlank { null }
        ?: "document-${fileVersion.recordId}"
    val contentType = try {
        fileVersion.formaat?.let { ContentType.parse(it) }
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
        wopiService.streamByBestandsnaam(
            bestandsnaam = fileVersion.bestandsLocatie,
            output = this,
            repoName = fileVersion.bestandsRepository,
        )
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
            // Required CheckFileInfo properties
            baseFileName = result.bestandsnaam?.ifBlank { null } ?: result.titel.ifBlank { null } ?: "document",
            lastModifiedTime = result.beginRegistratie,
            ownerId = "", // TODO(mvanbeusekom): It is unclear how to determine the ownerId of the document.
            size = result.bestandsomvang,
            userId = "", // TODO(mvanbeusekom): It is unclear how to determine the current user accessing the document.
            version = result.versie.toString(),
            // WOPI Host capabilities
            supportsAutosave = false,
            supportsContainers = false,
            supportsDeleteFile = false,
            supportsGetLock = false,
            supportsLocks = true,
            supportsPutRelativeFile = true,
            supportsRename = true,
            supportsUpdate = true,
            // User metadata properties
            userFriendlyName = "Unknown user",
            // User permissions
            userCanRename = true,
            userCanWrite = true,
        )
        call.respond(HttpStatusCode.OK, checkFileInfoResponse)
    }
}

/**
 * Validates [fileName] against WOPI file naming rules.
 * Returns `true` (and sends a 400 response) when the name is invalid, so callers can `return` early.
 */
private suspend fun RoutingContext.respondIfInvalidFileName(fileName: String): Boolean {
    if (fileName.contains('/') || fileName.contains('\\') || fileName.startsWith('.')) {
        call.response.headers.append("X-WOPI-InvalidFileNameError", "File name contains invalid characters.")
        call.respondProblem(
            HttpStatusCode.BadRequest,
            badRequest("Requested file name contains invalid characters.", call.request.path()),
        )
        return true
    }
    return false
}

private suspend fun RoutingContext.renameFile() {
    val wopiOverride = call.request.headers["X-WOPI-Override"]
    if (wopiOverride != "RENAME_FILE") {
        call.respondProblem(
            HttpStatusCode.NotImplemented,
            notImplemented("Operation not supported. Expected X-WOPI-Override: RENAME_FILE", call.request.path()),
        )
        return
    }

    val requestedName = call.request.headers["X-WOPI-RequestedName"]?.trim()
    if (requestedName.isNullOrBlank()) {
        call.respondProblem(
            HttpStatusCode.BadRequest,
            badRequest("X-WOPI-RequestedName header is required and must not be blank.", call.request.path()),
        )
        return
    }

    if (requestedName.length >= 255) {
        call.response.headers.append("X-WOPI-InvalidFileNameError", "File name is too long.")
        call.respondProblem(
            HttpStatusCode.BadRequest,
            badRequest("Requested file name is too long.", call.request.path()),
        )
        return
    }

    if (respondIfInvalidFileName(requestedName)) return

    val validatedFileId = call.attributes[WopiValidatedFileIdKey]
    val lockValue = call.request.headers["X-WOPI-Lock"]

    when (
        val result =
            wopiService.wopiRenameFile(id = validatedFileId, newFileName = requestedName, lockValue = lockValue)
    ) {
        is WopiRenameResult.NotFound ->
            call.respondProblem(
                HttpStatusCode.NotFound,
                notFound("EnkelvoudigInformatieObject not found", call.request.path()),
            )

        is WopiRenameResult.LockMismatch -> {
            call.response.headers.append("X-WOPI-Lock", result.currentLock)
            call.respondProblem(HttpStatusCode.Conflict, conflict("Lock mismatch."))
        }

        is WopiRenameResult.Success ->
            call.respond(HttpStatusCode.OK, RenameFileResponse(name = requestedName))
    }
}

private suspend fun RoutingContext.putRelativeFile() {
    val relativeTarget = call.request.headers["X-WOPI-RelativeTarget"]?.trim()
    val suggestedTarget = call.request.headers["X-WOPI-SuggestedTarget"]?.trim()
    val contentLength = call.request.headers["X-WOPI-Size"]?.toLongOrNull()

    // Exactly one of RelativeTarget or SuggestedTarget must be provided.
    if (relativeTarget == null && suggestedTarget == null) {
        call.respondProblem(
            HttpStatusCode.BadRequest,
            badRequest(
                "Either X-WOPI-RelativeTarget or X-WOPI-SuggestedTarget header is required.",
                call.request.path(),
            ),
        )
        return
    }

    if (relativeTarget != null && suggestedTarget != null) {
        call.respondProblem(
            HttpStatusCode.BadRequest,
            badRequest(
                "Exactly one of X-WOPI-RelativeTarget or X-WOPI-SuggestedTarget must be provided.",
                call.request.path(),
            ),
        )
        return
    }

    if (contentLength == null) {
        call.respondProblem(
            HttpStatusCode.BadRequest,
            badRequest("X-WOPI-Size header is required and must be a valid integer.", call.request.path()),
        )
        return
    }

    // SuggestedTarget never overwrites — the host picks a conflict-free name.
    val targetFileName = relativeTarget ?: suggestedTarget!!

    if (respondIfInvalidFileName(targetFileName)) return

    val sourceFileId = call.attributes[WopiValidatedFileIdKey]

    call.receiveChannel().toInputStream().use { inputStream ->
        when (
            val result = wopiService.wopiPutRelativeFile(
                sourceId = sourceFileId,
                targetFileName = targetFileName,
                inputStream = inputStream,
                contentLength = contentLength,
            )
        ) {
            is WopiPutRelativeFileResult.SourceNotFound ->
                call.respondProblem(HttpStatusCode.NotFound, notFound("Source file not found.", call.request.path()))

            is WopiPutRelativeFileResult.NameConflict -> {
                call.response.headers.append("X-WOPI-ValidRelativeTarget", result.validRelativeTarget)
                call.respondProblem(
                    HttpStatusCode.Conflict,
                    conflict("A file named '$targetFileName' already exists.", call.request.path()),
                )
            }

            is WopiPutRelativeFileResult.TargetLocked -> {
                call.response.headers.append("X-WOPI-Lock", result.currentLock)
                call.respondProblem(
                    HttpStatusCode.Conflict,
                    conflict("Target file is locked.", call.request.path()),
                )
            }

            is WopiPutRelativeFileResult.Success -> {
                val fileUrl = call.request.local.let { "https://${it.serverHost}:${it.serverPort}" } +
                    "/wopi/api/v1/files/${result.fileId}"
                call.respond(
                    HttpStatusCode.OK,
                    PutRelativeFileResponse(name = result.resolvedName, url = fileUrl),
                )
            }
        }
    }
}

private suspend fun RoutingContext.deleteFile() {
    val fileId = call.attributes[WopiValidatedFileIdKey]

    when (val result = wopiService.wopiDeleteFile(fileId)) {
        is WopiDeleteResult.NotFound ->
            call.respondProblem(HttpStatusCode.NotFound, notFound("File not found", call.request.path()))

        is WopiDeleteResult.Locked -> {
            call.response.headers.append("X-WOPI-Lock", result.currentLock)
            call.respondProblem(
                HttpStatusCode.Conflict,
                conflict("File is locked and cannot be deleted.", call.request.path()),
            )
        }

        is WopiDeleteResult.HasReferences ->
            call.respondProblem(
                HttpStatusCode.Conflict,
                conflict("File cannot be deleted because it has references.", call.request.path()),
            )

        is WopiDeleteResult.Success -> call.respond(HttpStatusCode.OK)
    }
}
