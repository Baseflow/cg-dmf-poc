// SPDX-License-Identifier: EUPL-1.2
// Copyright (C) 2026 Gemeente Utrecht
package com.baseflow.services

import com.baseflow.api.ApiUrlBuilder
import com.baseflow.api.middleware.AuditContext
import com.baseflow.api.models.AuditTrailResponse
import com.baseflow.api.routes.RESOURCE_SEGMENT
import com.baseflow.config.RequestScope
import com.baseflow.entities.AuditTrailEntity
import com.baseflow.entities.AuditTrails
import com.baseflow.entities.Wijzigingen
import com.baseflow.entities.toResponse
import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.v1.core.like
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.koin.core.annotation.Scope
import org.koin.core.annotation.Scoped
import org.slf4j.LoggerFactory
import java.util.*
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

enum class AuditSource(val weergave: String) {
    AC("ac"),
    NRC("nrc"),
    ZRC("zrc"),
    ZTC("ztc"),
    DRC("drc"),
    BRC("brc"),
    CMC("cmc"),
    KC("kc"),
    VRC("vrc")
}

enum class AuditAction(val value: String, val weergave: String) {
    RETRIEVE("retrieve", "Object opgehaald"),
    LIST("list", "Lijst van objecten opgehaald"),
    CREATE("create", "Object aangemaakt"),
    PARTIAL_UPDATE("partial_update", "Object deels bijgewerkt"),
    DESTROY("destroy", "Object verwijderd"),
    UPDATE("update", "Object bijgewerkt"),
    HEAD("head", "Object opgevraagd (HEAD)"),
    UNKNOWN("unknown", "Onbekende actie")
}

val httpMethodToAction = mapOf(
    HttpMethod.Get to AuditAction.RETRIEVE,
    HttpMethod.Post to AuditAction.CREATE,
    HttpMethod.Patch to AuditAction.PARTIAL_UPDATE,
    HttpMethod.Delete to AuditAction.DESTROY,
    HttpMethod.Put to AuditAction.UPDATE,
    HttpMethod.Head to AuditAction.HEAD,
)

@Serializable
data class ApplicationInfo @JsonCreator constructor(
    @param:JsonProperty("uuid") val id: String,
    @param:JsonProperty("label") val label: String
)

@OptIn(ExperimentalTime::class)
@Scope(RequestScope::class)
@Scoped
open class AuditTrailService(private val context: AuditContext) {
    private val logger = LoggerFactory.getLogger(AuditTrailService::class.java)

    fun create(call: PipelineCall) {
        val before = context.oldValue
        val after = context.newValue
        if (before == null && after == null) return

        val userId = getUserId(call) ?: "unknown"
        val username = getUserClaim(call) ?: "unknown"
        val toelichting = getAuditToelichting(call)

        val appInfo = getApplicationInfo(call)

        val method = call.request.httpMethod
        var action = httpMethodToAction[method] ?: AuditAction.UNKNOWN
        if (method == HttpMethod.Get && before is List<*>) {
            action = AuditAction.LIST
        }
        val actieWeergave = action.weergave
        var wijzigingen = Wijzigingen()

        when (method) {
            HttpMethod.Post -> {
                wijzigingen = Wijzigingen.of(
                    oud = null, // Voor een POST-aanroep is er geen oud object
                    nieuw = after
                )
            }

            HttpMethod.Patch, HttpMethod.Put -> {
                wijzigingen = Wijzigingen.of(
                    oud = before,
                    nieuw = after
                )
            }

            HttpMethod.Delete -> {
                wijzigingen = Wijzigingen.of(
                    oud = before,
                    nieuw = null
                )
            }
        }
        val resourceUrl = ApiUrlBuilder.absolute(RESOURCE_SEGMENT, (before ?: after)?.id.toString())
        transaction {
            AuditTrailEntity.new {
                this.applicatieId = appInfo.id
                this.applicatieWeergave = appInfo.label
                this.bron = AuditSource.DRC.weergave
                this.hoofdObject = resourceUrl // TODO: what is the hoofdObject for this audit trail? Is it the resource URL or something else?
                this.resource = "enkelvoudiginformatieobjecten"
                this.resourceUrl = resourceUrl
                this.resourceWeergave = context.customId
                this.actie = action.value
                this.gebruikersId = userId
                this.gebruikersWeergave = username
                this.actieWeergave = actieWeergave
                this.resultaat = call.response.status()?.value
                this.toelichting = toelichting
                this.wijzigingen = wijzigingen
                this.aanmaakdatum = Clock.System.now().toLocalDateTime(TimeZone.UTC)
            }
        }
    }

    private fun getApplicationInfo(call: PipelineCall): ApplicationInfo {
        val principal = call.principal<JWTPrincipal>()
        val appId = call.request.headers["X-NLX-Request-Application-Id"]
        if (principal != null) {
            val claim = principal.payload.getClaim("applications")
            if (claim != null) {
                // First try the auth0 Claim.asArray conversion to our data class
                try {
                    val apps = claim.asArray(ApplicationInfo::class.java)
                    if (apps != null && apps.isNotEmpty()) return apps[0]
                } catch (ex: Exception) {
                    logger.debug("Claim.asArray failed for applications claim: ${ex.message}")
                }
            }
        } else if (appId != null) {
            return ApplicationInfo(appId, appId)
        }

        logger.warn("No application found for $principal")
        return ApplicationInfo("unknown", "unknown")
    }

    fun listByResource(resourceUuid: UUID): List<AuditTrailResponse> {
        return transaction {
            AuditTrailEntity.find {
                AuditTrails.resourceUrl like "%/$resourceUuid"
            }.map { it.toResponse() }
        }
    }

    fun getByUuid(resourceUuid: UUID, auditTrailUuid: UUID): AuditTrailResponse? {
        return transaction {
            val entity = AuditTrailEntity.findById(auditTrailUuid)
            if (entity != null && entity.resourceUrl.endsWith("/$resourceUuid")) {
                entity.toResponse()
            } else {
                null
            }
        }
    }

    fun removeAuditTrailsForResource(resourceUuid: UUID) {
        transaction {
            AuditTrailEntity.find { AuditTrails.resourceUrl like "%/$resourceUuid" }.forEach { it.delete() }
        }
    }
}
