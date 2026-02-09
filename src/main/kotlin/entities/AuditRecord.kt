import kotlinx.serialization.Serializable
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.dao.UUIDEntity
import org.jetbrains.exposed.v1.dao.UUIDEntityClass
import org.jetbrains.exposed.v1.datetime.CurrentDateTime
import org.jetbrains.exposed.v1.datetime.datetime
import org.jetbrains.exposed.v1.core.dao.id.UUIDTable as UUIDTableCore
import java.util.UUID

@Serializable
data class Wijzigingen<T>(
    val oud: T? = null,
    val nieuw: T? = null
)

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
    val wijzigingen = text("wijzigingen").default("{}")
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
