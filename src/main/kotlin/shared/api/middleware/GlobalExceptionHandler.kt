// SPDX-License-Identifier: EUPL-1.2
// Copyright (C) 2025 Gemeente Utrecht
package com.baseflow.shared.api.middleware

import com.baseflow.shared.api.models.ProblemDetailsResponse
import com.baseflow.shared.api.models.badRequest
import com.baseflow.shared.api.models.forbidden
import com.baseflow.shared.api.models.respondProblem
import com.baseflow.shared.api.models.unauthorized
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.JsonConvertException
import io.ktor.server.application.*
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.request.path
import kotlinx.serialization.SerializationException
import org.slf4j.LoggerFactory

/**
 * Configures global exception handling for the application using StatusPages.
 */
fun Application.configureStatusPages() {
    val logger = LoggerFactory.getLogger("GlobalExceptionHandler")

    install(StatusPages) {
        exception<UnauthorizedException> { call, cause ->
            call.respondProblem(
                HttpStatusCode.Unauthorized,
                unauthorized(
                    detail = cause.message ?: "Unauthorized",
                    instance = call.request.path(),
                ),
            )
        }

        exception<ForbiddenException> { call, cause ->
            call.respondProblem(
                HttpStatusCode.Forbidden,
                forbidden(
                    detail = cause.message ?: "Forbidden",
                    instance = call.request.path(),
                ),
            )
        }

        exception<BadRequestException> { call, cause ->
            logger.error("Bad request at ${call.request.path()}: ${cause.message}")

            // Try to find a SerializationException or JsonConvertException in the cause chain
            var currentCause: Throwable? = cause
            var serializationException: Throwable? = null
            while (currentCause != null) {
                if (currentCause is SerializationException || currentCause is JsonConvertException) {
                    serializationException = currentCause
                    break
                }
                currentCause = currentCause.cause
            }

            val detail = if (serializationException != null) {
                "Invalid request body: ${serializationException.message}"
            } else {
                cause.cause?.message ?: cause.message ?: "Bad Request"
            }

            respondWithBadRequest(call, detail)
        }

        exception<Throwable> { call, cause ->
            logger.error("Unhandled exception at ${call.request.path()}", cause)
            call.respondProblem(
                HttpStatusCode.InternalServerError,
                ProblemDetailsResponse(
                    title = "Internal Server Error",
                    status = HttpStatusCode.InternalServerError.value,
                    detail = cause.message,
                    instance = call.request.path(),
                ),
            )
        }
    }
}

private suspend fun respondWithBadRequest(call: ApplicationCall, detail: String) {
    call.respondProblem(
        HttpStatusCode.BadRequest,
        badRequest(
            detail = detail,
            instance = call.request.path(),
        ),
    )
}
