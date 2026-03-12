// SPDX-License-Identifier: EUPL-1.2
// Copyright (C) 2025 Gemeente Utrecht
package com.baseflow.api.models

import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respond
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * RFC 7807 Problem Details representation for consistent API error responses.
 * We keep the structure minimal and spec-compliant.
 */
@Serializable
data class ProblemDetailsResponse(
    val type: String = "about:blank",
    val title: String,
    val status: Int,
    val detail: String? = null,
    val instance: String? = null,
) : ApiResponse

private val problemJson = Json {
    encodeDefaults = false
    explicitNulls = false
    ignoreUnknownKeys = true
}

private val ProblemContentType = ContentType.parse("application/problem+json; charset=utf-8")

/** Serialize and respond with problem+json at the given HTTP status. */
suspend fun ApplicationCall.respondProblem(status: HttpStatusCode, problem: ProblemDetailsResponse) {
    val body = problemJson.encodeToString(ProblemDetailsResponse.serializer(), problem)
    respond(status, TextContent(body, ProblemContentType))
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
