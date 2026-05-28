// SPDX-License-Identifier: EUPL-1.2
// Copyright (C) 2025-2026 Gemeente Utrecht
package com.baseflow.api.models

import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.openapi.JsonSchema
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.path
import io.ktor.server.response.respond
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * RFC 7807 Problem Details representation for consistent API error responses.
 * We keep the structure minimal and spec-compliant.
 */
@JsonSchema.Title("Foutmelding")
@JsonSchema.Description("RFC 7807 Problem Details — gestandaardiseerde foutmelding voor API-fouten.")
@JsonSchema.Example(
    """{
  "type": "https://drc.example.com/api/v1/fouten/validatie",
  "title": "Validatiefout",
  "status": 400,
  "detail": "bronorganisatie: Dit veld is verplicht.",
  "instance": "/api/v1/enkelvoudiginformatieobjecten"
}""",
)
@Serializable
data class ProblemDetailsResponse(
    @JsonSchema.Description("URI-referentie die het probleemtype identificeert. Standaard 'about:blank'.")
    @JsonSchema.Format("uri")
    val type: String = "about:blank",

    @JsonSchema.Description("Een korte, leesbare samenvatting van het probleemtype.")
    @JsonSchema.Example("\"Validatiefout\"")
    val title: String,

    @JsonSchema.Description("De HTTP-statuscode voor dit probleem.")
    @JsonSchema.Example("400")
    val status: Int,

    @JsonSchema.Description("Een leesbare uitleg van dit specifieke probleem.")
    @JsonSchema.Example("\"bronorganisatie: Dit veld is verplicht.\"")
    val detail: String? = null,

    @JsonSchema.Description("Een URI-referentie die de specifieke instantie van het probleem identificeert (bijv. het request-pad).")
    @JsonSchema.Format("uri")
    @JsonSchema.Example("\"/api/v1/enkelvoudiginformatieobjecten\"")
    val instance: String? = null,
) : ApiResponse

private val problemJson = Json {
    encodeDefaults = false
    explicitNulls = false
    ignoreUnknownKeys = true
}

private val ProblemContentType = ContentType.parse("application/problem+json; charset=utf-8")

/**
 * Serialize and respond with problem+json at the given HTTP status.
 *
 * **Do not call this from a plugin hook** (`onCall`, `on(AuthenticationChecked)`, etc.) to reject a request.
 * Sending a response from a hook does not stop the pipeline — the route handler will still execute.
 * Throw a typed exception instead (e.g. [com.baseflow.api.middleware.ForbiddenException]) and let
 * `StatusPages` handle it, which properly terminates the pipeline.
 */
suspend fun ApplicationCall.respondProblem(status: HttpStatusCode, problem: ProblemDetailsResponse) {
    val body = problemJson.encodeToString(ProblemDetailsResponse.serializer(), problem)
    respond(status, TextContent(body, ProblemContentType))
}

/**
 * Convenience overload: builds the [ProblemDetailsResponse] from the status code and message,
 * deriving the `instance` from the request path automatically.
 *
 * The `title` and `detail` fields are set based on [status]:
 * - 400 Bad Request
 * - 401 Unauthorized
 * - 404 Not Found
 * - 409 Conflict
 * - 412 Precondition Failed
 * - 501 Not Implemented
 * - any other code falls back to the status code's description as title
 */
suspend fun ApplicationCall.respondProblem(status: HttpStatusCode, message: String) {
    val title = when (status) {
        HttpStatusCode.BadRequest -> "Bad Request"
        HttpStatusCode.Unauthorized -> "Unauthorized"
        HttpStatusCode.NotFound -> "Not Found"
        HttpStatusCode.Conflict -> "Conflict"
        HttpStatusCode.PreconditionFailed -> "Precondition Failed"
        HttpStatusCode.NotImplemented -> "Not Implemented"
        else -> status.description
    }
    respondProblem(
        status,
        ProblemDetailsResponse(
            title = title,
            status = status.value,
            detail = message,
            instance = request.path(),
        ),
    )
}

// Convenience factories
fun badRequest(detail: String, instance: String? = null) = ProblemDetailsResponse(
    title = "Bad Request",
    status = HttpStatusCode.BadRequest.value,
    detail = detail,
    instance = instance,
)

fun notFound(detail: String, instance: String? = null) = ProblemDetailsResponse(
    title = "Not Found",
    status = HttpStatusCode.NotFound.value,
    detail = detail,
    instance = instance,
)

fun conflict(detail: String, instance: String? = null) = ProblemDetailsResponse(
    title = "Conflict",
    status = HttpStatusCode.Conflict.value,
    detail = detail,
    instance = instance,
)

fun forbidden(detail: String, instance: String? = null) = ProblemDetailsResponse(
    title = "Forbidden",
    status = HttpStatusCode.Forbidden.value,
    detail = detail,
    instance = instance,
)

fun notImplemented(detail: String, instance: String? = null) = ProblemDetailsResponse(
    title = "Not Implemented",
    status = HttpStatusCode.NotImplemented.value,
    detail = detail,
    instance = instance,
)
