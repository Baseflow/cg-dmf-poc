package com.baseflow.entities

import kotlinx.serialization.Serializable
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.dao.UUIDEntity
import org.jetbrains.exposed.v1.dao.UUIDEntityClass
import org.jetbrains.exposed.v1.datetime.CurrentDateTime
import org.jetbrains.exposed.v1.datetime.datetime
import org.jetbrains.exposed.v1.core.dao.id.UUIDTable as UUIDTableCore
import java.util.UUID
import com.baseflow.api.models.AuditTrailResponse
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import org.jetbrains.exposed.v1.json.json

@Serializable
data class Wijzigingen(
    val oud: JsonElement? = null,
    val nieuw: JsonElement? = null
) {
    companion object {
        inline fun <reified T> of(oud: T? = null, nieuw: T? = null): Wijzigingen {
            return Wijzigingen(
                oud = oud?.let { Json.encodeToJsonElement(it) },
                nieuw = nieuw?.let { Json.encodeToJsonElement(it) }
            )
        }
    }
}

object AuditTrails : UUIDTableCore("audit_trails") {
    val bron = varchar("bron", 50)
    val applicatieId = varchar("applicatie_id", 100).nullable()
    val applicatieWeergave = varchar("applicatie_weergave", 200).nullable()
    val gebruikersId = varchar("gebruikers_id", 255).nullable()
    val gebruikersWeergave = varchar("gebruikers_weergave", 255).nullable()
    val actie = varchar("actie", 50)
    val actieWeergave = varchar("actie_weergave", 200).nullable()
    val resultaat = integer("resultaat").nullable()
    val hoofdObject = varchar("hoofd_object", 1000)
    val resource = varchar("resource", 50)
    val resourceUrl = varchar("resource_url", 1000)
    val toelichting = text("toelichting").nullable()
    val resourceWeergave = varchar("resource_weergave", 200).nullable()
    val aanmaakdatum = datetime("aanmaakdatum").defaultExpression(CurrentDateTime)
    val wijzigingen = json<Wijzigingen>("wijzigingen", Json.Default).default(Wijzigingen())
}

class AuditTrailEntity(id: EntityID<UUID>) : UUIDEntity(id) {
    companion object : UUIDEntityClass<AuditTrailEntity>(AuditTrails)

    var bron by AuditTrails.bron
    var applicatieId by AuditTrails.applicatieId
    var applicatieWeergave by AuditTrails.applicatieWeergave
    var gebruikersId by AuditTrails.gebruikersId
    var gebruikersWeergave by AuditTrails.gebruikersWeergave
    var actie by AuditTrails.actie
    var actieWeergave by AuditTrails.actieWeergave
    var resultaat by AuditTrails.resultaat
    var hoofdObject by AuditTrails.hoofdObject
    var resource by AuditTrails.resource
    var resourceUrl by AuditTrails.resourceUrl
    var toelichting by AuditTrails.toelichting
    var resourceWeergave by AuditTrails.resourceWeergave
    var aanmaakdatum by AuditTrails.aanmaakdatum
    var wijzigingen by AuditTrails.wijzigingen
}

fun AuditTrailEntity.toResponse(): AuditTrailResponse {
    return AuditTrailResponse(
        uuid = this.id.value.toString(),
        bron = this.bron,
        applicatieId = this.applicatieId,
        applicatieWeergave = this.applicatieWeergave,
        gebruikersId = this.gebruikersId,
        gebruikersWeergave = this.gebruikersWeergave,
        actie = this.actie,
        actieWeergave = this.actieWeergave,
        resultaat = this.resultaat,
        hoofdObject = this.hoofdObject,
        resource = this.resource,
        resourceUrl = this.resourceUrl,
        resourceWeergave = this.resourceWeergave,
        toelichting = this.toelichting,
        wijzigingen = wijzigingen,
        aanmaakdatum = this.aanmaakdatum
    )
}