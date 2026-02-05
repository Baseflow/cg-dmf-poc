// SPDX-License-Identifier: EUPL-1.2
// Copyright (C) 2026 Gemeente Utrecht
package com.baseflow.services

import AuditTrailEntity
import com.baseflow.api.ApiUrlBuilder
import com.baseflow.api.DOCUMENTEN_API_BASE_PATH
import com.baseflow.api.models.EnkelvoudigInformatieObjectResponse
import com.baseflow.api.routes.RESOURCE_SEGMENT
import com.baseflow.config.ApplicationConfig
import io.ktor.http.HttpMethod
import io.ktor.server.routing.RoutingCall
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.httpMethod
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.Serializable
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

private fun getUserFromJwt(call: RoutingCall): JWTPrincipal? {
    return call.principal<JWTPrincipal>()
}

private fun getUserId(call: RoutingCall): String? {
    return getUserFromJwt(call)?.payload?.subject
}

private fun getUserClaim(call: RoutingCall, claimName: String): String? {
    return getUserFromJwt(call)?.payload?.getClaim(claimName)?.asString()
}

private fun getAuditToelichting(call: RoutingCall): String? {
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

@Serializable
data class Wijzigingen(
    val oud: EnkelvoudigInformatieObjectResponse?,
    val nieuw: EnkelvoudigInformatieObjectResponse?
)

@OptIn(ExperimentalTime::class)
fun createAuditTrail(call: RoutingCall, eio: EnkelvoudigInformatieObjectResponse) {

    val userId = getUserId(call) ?: "unknown"
    val username = getUserClaim(call, "username") ?: "unknown"
    val toelichting = getAuditToelichting(call)
    val action = call.request.httpMethod.value
    val actieWeergave = httpMethodToDescriptionMap[call.request.httpMethod] ?: "Onbekende actie"

    transaction {
        AuditTrailEntity.new {
            this.bron = "Documenten API"
            this.hoofdObject = ApiUrlBuilder.absolute(ApplicationConfig.baseUrl(),  DOCUMENTEN_API_BASE_PATH, RESOURCE_SEGMENT, eio.id)
            this.resource = "enkelvoudiginformatieobjecten"
            this.resourceUrl = ApiUrlBuilder.absolute(ApplicationConfig.baseUrl(),  DOCUMENTEN_API_BASE_PATH, RESOURCE_SEGMENT, eio.id)
            this.resourceWeergave = "${eio.bronorganisatie} - ${eio.identificatie}"
            this.actie = action
            this.gebruikersId = userId
            this.gebruikersWeergave = username
            this.actieWeergave = actieWeergave
            this.resultaat = 201
            this.toelichting = toelichting
            this.wijzigingen = Json.encodeToString(Wijzigingen(oud = null, nieuw = eio))
            this.aanmaakdatum = Clock.System.now().toLocalDateTime(TimeZone.UTC)
        }
    }
}