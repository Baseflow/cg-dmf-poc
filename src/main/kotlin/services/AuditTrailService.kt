// SPDX-License-Identifier: EUPL-1.2
// Copyright (C) 2026 Gemeente Utrecht
package com.baseflow.services

import AuditTrailEntity
import Wijzigingen
import com.baseflow.api.ApiUrlBuilder
import com.baseflow.api.DOCUMENTEN_API_BASE_PATH
import com.baseflow.api.models.EnkelvoudigInformatieObjectResponse
import com.baseflow.api.routes.RESOURCE_SEGMENT
import com.baseflow.config.ApplicationConfig
import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.routing.*
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

private fun getUserFromJwt(call: RoutingCall): JWTPrincipal? {
    return call.principal<JWTPrincipal>()
}

private fun getUserId(call: RoutingCall): String? {
    return getUserFromJwt(call)?.payload?.subject
}

private fun getUserClaim(call: RoutingCall, claimName: String = "username"): String? {
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

@OptIn(ExperimentalTime::class)
fun createAuditTrail(call: RoutingCall, eio: EnkelvoudigInformatieObjectResponse? = null) {
    if (eio == null) {
        return // Als er geen EIO is, kunnen we geen zinvolle audit trail makenw
    }
    val userId = getUserId(call) ?: "unknown"
    val username = getUserClaim(call) ?: "unknown"
    val toelichting = getAuditToelichting(call)
    val action = call.request.httpMethod
    val actieWeergave = httpMethodToDescriptionMap[call.request.httpMethod] ?: "Onbekende actie"
    var wijzigingen: Wijzigingen<EnkelvoudigInformatieObjectResponse>? = null
    if (action == HttpMethod.Post) {
        wijzigingen = Wijzigingen(
            oud = null, // Voor een POST-aanroep is er geen oud object
            nieuw = eio
        )
    } else if (action == HttpMethod.Patch || action == HttpMethod.Put) {
        wijzigingen = Wijzigingen(
            oud = null, // Voor een PATCH-aanroep zou je hier het oude object moeten ophalen voordat je de wijzigingen toepast
            nieuw = eio
        )
    } else if (action == HttpMethod.Delete) {
        wijzigingen = Wijzigingen(
            oud = eio, // Voor een DELETE-aanroep is het oude object het verwijderde object
            nieuw = null
        )
    }
    val resourceUrl = ApiUrlBuilder.absolute(ApplicationConfig.baseUrl(),  DOCUMENTEN_API_BASE_PATH, RESOURCE_SEGMENT, eio.id)
    transaction {
        AuditTrailEntity.new {
            this.bron = "Documenten API"
            this.hoofdObject = resourceUrl
            this.resource = "enkelvoudiginformatieobjecten"
            this.resourceUrl = resourceUrl
            this.resourceWeergave = "${eio.bronorganisatie} - ${eio.identificatie}"
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