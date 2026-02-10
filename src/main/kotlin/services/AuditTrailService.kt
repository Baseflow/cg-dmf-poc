// SPDX-License-Identifier: EUPL-1.2
// Copyright (C) 2026 Gemeente Utrecht
package com.baseflow.services

import AuditTrailEntity
import Wijzigingen
import api.middleware.AuditContext
import com.baseflow.api.ApiUrlBuilder
import com.baseflow.api.models.ApiEntityResponse
import com.baseflow.api.routes.RESOURCE_SEGMENT
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

private fun getUserFromJwt(call: PipelineCall): JWTPrincipal? {
    return call.principal<JWTPrincipal>()
}

private fun getUserId(call: PipelineCall): String? {
    return getUserFromJwt(call)?.payload?.subject
}

private fun getUserClaim(call: PipelineCall, claimName: String = "username"): String? {
    return getUserFromJwt(call)?.payload?.getClaim(claimName)?.asString()
}

private fun getAuditToelichting(call: PipelineCall): String? {
    return call.request.headers["X-Audit-Toelichting"]
}

val httpMethodToDescriptionMap = mapOf(
    HttpMethod.Get to "Object opgevraagd",
    HttpMethod.Post to "Object aangemaakt",
    HttpMethod.Patch to "Object gewijzigd",
    HttpMethod.Delete to "Object verwijderd",
    HttpMethod.Put to "Object geüpdate",
    HttpMethod.Head to "Object opgevraagd (HEAD)",
)

@OptIn(ExperimentalTime::class)
fun createAuditTrail(call: PipelineCall, context: AuditContext) {
    val before = context.oldValue
    val after = context.newValue
    if (before == null && after == null) return

    val userId = getUserId(call) ?: "unknown"
    val username = getUserClaim(call) ?: "unknown"
    val toelichting = getAuditToelichting(call)
    val appId = call.request.headers["X-NLX-Request-Application-Id"]
    val action = call.request.httpMethod
    val actieWeergave = httpMethodToDescriptionMap[call.request.httpMethod] ?: "Onbekende actie"
    var wijzigingen: Wijzigingen<ApiEntityResponse>? = null
    when (action) {
        HttpMethod.Post -> {
            wijzigingen = Wijzigingen(
                oud = null, // Voor een POST-aanroep is er geen oud object
                nieuw = after
            )
        }
        HttpMethod.Patch, HttpMethod.Put -> {
            wijzigingen = Wijzigingen(
                oud = before,
                nieuw = after
            )
        }
        HttpMethod.Delete -> {
            wijzigingen = Wijzigingen(
                oud = before,
                nieuw = null
            )
        }
    }
    val resourceUrl = ApiUrlBuilder.absolute( RESOURCE_SEGMENT, (before ?: after)?.id.toString())
    transaction {
        AuditTrailEntity.new {
            this.applicatieId = appId
            this.applicatieWeergave = applicatieId
            this.bron = "Documenten API"
            this.hoofdObject = resourceUrl
            this.resource = "enkelvoudiginformatieobjecten"
            this.resourceUrl = resourceUrl
            this.resourceWeergave = context.customId
            this.actie = action.value
            this.gebruikersId = userId
            this.gebruikersWeergave = username
            this.actieWeergave = actieWeergave
            this.resultaat = 201
            this.toelichting = toelichting
            this.wijzigingen = Json.encodeToString(wijzigingen)
            this.aanmaakdatum = Clock.System.now().toLocalDateTime(TimeZone.UTC)
        }
    }
}