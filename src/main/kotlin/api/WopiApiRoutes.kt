package com.baseflow.api

import com.baseflow.api.middleware.*
import com.baseflow.api.models.badRequest
import com.baseflow.api.models.notFound
import com.baseflow.api.models.respondProblem
import com.baseflow.services.EnkelvoudigInformatieObjectService
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.path
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.routing.openapi.hide
import io.ktor.utils.io.ExperimentalKtorApi
import java.util.UUID
import com.baseflow.api.middleware.RequestScopeKey
import com.baseflow.api.models.wopi.CheckFileInfoResponse
import com.baseflow.entities.EIORecordEntity
import io.ktor.http.ContentDisposition
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

@OptIn(ExperimentalKtorApi::class)
fun Route.wopiApiRoutes() {
    install(AuditTrailPlugin)

    route(WOPI_API_BASE_PATH) {

        get("/files/{file_id}") {
            getFileMetadata()
        }.hide()

        get("/files/{file_id}/contents") {
            getFileContents()
        }.hide()

    }
}


private suspend fun RoutingContext.getFileContents() {
    val fileId = call.parameters["file_id"]
    if (fileId == null) {
        call.respondProblem(
            HttpStatusCode.BadRequest, badRequest("file_id parameter is required", call.request.path())
        )
        return
    }

    try {
        val uuid = UUID.fromString(fileId)

        val eio = transaction {
            val record =
                EIORecordEntity.findById(uuid) ?: return@transaction null
            val eio = record.versions.maxByOrNull { it.versie }
            return@transaction eio
        }

        if (eio == null) {
            call.respondProblem(
                HttpStatusCode.NotFound,
                notFound("EnkelvoudigInformatieObject not found", call.request.path()),
            )
            return
        }

        // Ensure we have a stored object key to stream
        val objectKey = eio.bestandsLocatie
        if (objectKey.isBlank()) {
            call.respondProblem(
                HttpStatusCode.NotFound,
                notFound("Document content not available for download", call.request.path()),
            )
            return
        }

        // Derive filename and content type when possible;
        val fileName = objectKey.ifBlank { "document-${eio.id}" }
        val contentType = try {
            // eio.formaat is expected to be a MIME type; if not, fallback below
            eio.formaat?.let { ContentType.parse(it) }
        } catch (_: Exception) {
            ContentType.Application.OctetStream
        }

        // Set headers before starting the stream
        call.response.headers.append(
            HttpHeaders.ContentDisposition,
            ContentDisposition.Attachment
                .withParameter(ContentDisposition.Parameters.FileName, fileName)
                .toString(),
        )
        call.response.headers.append(HttpHeaders.ContentType, contentType.toString())
        // TODO: support Range requests, ETag, Last-Modified when metadata is available

        // Stream the object from storage directly to the HTTP response
        call.respondOutputStream {
            service.streamByBestandsnaam(bestandsnaam = objectKey, output = this)
        }
    } catch (_: IllegalArgumentException) {
        call.respondProblem(HttpStatusCode.BadRequest, badRequest("Invalid UUID format", call.request.path()))
    }
}

private suspend fun RoutingContext.getFileMetadata() {
    val fileId = call.parameters["file_id"]
    if (fileId == null) {
        call.respondProblem(HttpStatusCode.BadRequest, badRequest("UUID parameter is required", call.request.path()))
        return
    }

    try {
        val uuid = UUID.fromString(fileId)
        val result = service.getById(uuid, emptyList())

        if (result == null) {
            call.respondProblem(
                HttpStatusCode.NotFound,
                notFound("EnkelvoudigInformatieObject not found", call.request.path()),
            )
        } else {
            val checkFileInfoResponse = CheckFileInfoResponse(
                BaseFileName = result.bestandsnaam ?: "document",
                Size = result.bestandsomvang ?: 0L
            )
            call.respond(
                HttpStatusCode.OK,
                checkFileInfoResponse
            )
        }
    } catch (_: IllegalArgumentException) {
        call.respondProblem(HttpStatusCode.BadRequest, badRequest("Invalid UUID format", call.request.path()))
    }
}

private val RoutingContext.service: EnkelvoudigInformatieObjectService
    get() = call.attributes[RequestScopeKey].get()

fun Application.wopiApiModule(
    useAuthentication: Boolean = false,
) {
    routing {
        if (useAuthentication) {
            authenticate("auth-jwt", "auth-zgw", strategy = AuthenticationStrategy.FirstSuccessful) {
                wopiApiRoutes()
            }
        } else {
            wopiApiRoutes()
        }
    }
}


