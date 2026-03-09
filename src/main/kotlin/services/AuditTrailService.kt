// SPDX-License-Identifier: EUPL-1.2
// Copyright (C) 2026 Gemeente Utrecht
package com.baseflow.services

import com.baseflow.api.ApiUrlBuilder
import com.baseflow.api.middleware.AuditContext
import com.baseflow.api.models.AuditTrailResponse
import com.baseflow.api.models.getResourceSegment
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

private fun getUserFromJwt(call: PipelineCall): JWTPrincipal? = call.principal<JWTPrincipal>()

private fun getUserId(call: PipelineCall): String? {
    val principal = getUserFromJwt(call) ?: return null
    // Keycloak tokens use 'sub', ZGW tokens use 'user_id'
    return principal.payload.subject
        ?: principal.payload.getClaim("user_id")?.asString()
}

private fun getUserClaim(call: PipelineCall, claimName: String = "username"): String? {
    val principal = getUserFromJwt(call) ?: return null
    // Try the requested claim first, then fall back to ZGW-style claims
    return principal.payload.getClaim(claimName)?.asString()?.ifEmpty { null }
        ?: principal.payload.getClaim("user_id")?.asString()?.ifEmpty { null }
        ?: principal.payload.getClaim("user_representation")?.asString()?.ifEmpty { null }
}

private fun getAuditToelichting(call: PipelineCall): String? = call.request.headers["X-Audit-Toelichting"]

enum class AuditSource(val weergave: String) {
    AC("ac"),
    NRC("nrc"),
    ZRC("zrc"),
    ZTC("ztc"),
    DRC("drc"),
    BRC("brc"),
    CMC("cmc"),
    KC("kc"),
    VRC("vrc"),
}

enum class AuditAction(val value: String, val weergave: String) {
    RETRIEVE("retrieve", "Object opgehaald"),
    LIST("list", "Lijst van objecten opgehaald"),
    CREATE("create", "Object aangemaakt"),
    PARTIAL_UPDATE("partial_update", "Object deels bijgewerkt"),
    DESTROY("destroy", "Object verwijderd"),
    UPDATE("update", "Object bijgewerkt"),
    HEAD("head", "Object opgevraagd (HEAD)"),
    UNKNOWN("unknown", "Onbekende actie"),
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
    @param:JsonProperty("label") val label: String,
)

@OptIn(ExperimentalTime::class)
@Scope(RequestScope::class)
@Scoped
open class AuditTrailService(private val context: AuditContext) {
    private val logger = LoggerFactory.getLogger(AuditTrailService::class.java)

    fun create(call: PipelineCall) {
        val before = context.oldValue
        val after = context.newValue
        val method = call.request.httpMethod
        // Skip logging if there are no changes, and it's not a DELETE operation (audit trails are supposed to be deleted on DELETE)
        if ((before == null && after == null) || method == HttpMethod.Delete) return

        val userId = getUserId(call) ?: "unknown"
        val username = getUserClaim(call) ?: "unknown"
        val toelichting = getAuditToelichting(call)

        val appInfo = getApplicationInfo(call)

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
                    nieuw = after,
                )
            }

            HttpMethod.Patch, HttpMethod.Put -> {
                wijzigingen = Wijzigingen.of(
                    oud = before,
                    nieuw = after,
                )
            }

            HttpMethod.Delete -> {
                wijzigingen = Wijzigingen.of(
                    oud = before,
                    nieuw = null,
                )
            }
        }
        val entity = before ?: after
        val resourceSegment = entity?.getResourceSegment()?.value.orEmpty()
        val resourceUrl = ApiUrlBuilder.absolute(resourceSegment, entity?.id.toString())
        transaction {
            AuditTrailEntity.new {
                this.applicatieId = appInfo.id
                this.applicatieWeergave = appInfo.label
                this.bron = AuditSource.DRC.weergave
                // TODO: what is the hoofdObject for this audit trail? Is it the resource URL or something else?
                this.hoofdObject = resourceUrl
                this.resource = resourceSegment
                this.resourceUrl = resourceUrl
                this.resourceWeergave = context.resourceWeergave
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
            // Try Keycloak-style 'applications' claim first
            val claim = principal.payload.getClaim("applications")
            if (claim != null && !claim.isMissing) {
                try {
                    val apps = claim.asArray(ApplicationInfo::class.java)
                    if (apps != null && apps.isNotEmpty()) return apps[0]
                } catch (ex: Exception) {
                    logger.debug("Claim.asArray failed for applications claim: ${ex.message}")
                }
            }

            // Fall back to ZGW-style 'client_id' claim
            val clientId = principal.payload.getClaim("client_id")?.asString()
            if (!clientId.isNullOrEmpty()) {
                return ApplicationInfo(clientId, clientId)
            }
        } else if (appId != null) {
            return ApplicationInfo(appId, appId)
        }

        logger.warn("No application found for $principal")
        return ApplicationInfo("unknown", "unknown")
    }

    fun listByResource(resourceUuid: UUID): List<AuditTrailResponse> = transaction {
        AuditTrailEntity.find {
            AuditTrails.resourceUrl like "%/$resourceUuid"
        }.map { it.toResponse() }
    }

    fun getByUuid(resourceUuid: UUID, auditTrailUuid: UUID): AuditTrailResponse? = transaction {
        val entity = AuditTrailEntity.findById(auditTrailUuid)
        if (entity != null && entity.resourceUrl.endsWith("/$resourceUuid")) {
            entity.toResponse()
        } else {
            null
        }
    }

    fun removeAuditTrailsForResource(resourceUuid: UUID) {
        transaction {
            AuditTrailEntity.find { AuditTrails.resourceUrl like "%/$resourceUuid" }.forEach { it.delete() }
        }
    }
}
