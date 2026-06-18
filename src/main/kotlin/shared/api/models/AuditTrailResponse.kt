// SPDX-License-Identifier: EUPL-1.2
// Copyright (C) 2026 Gemeente Utrecht
package com.baseflow.shared.api.models

import com.baseflow.shared.entities.Wijzigingen
import io.ktor.openapi.JsonSchema
import kotlinx.serialization.Serializable
import kotlin.time.Instant

@JsonSchema.Title("AuditTrailRegel")
@JsonSchema.Description("Een audittrail-regel die een wijziging op een INFORMATIEOBJECT vastlegt.")
@JsonSchema.Example(
    """{
  "uuid": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
  "bron": "DRC",
  "applicatieId": "gzac",
  "applicatieWeergave": "GZAC",
  "gebruikersId": "user@gemeente.nl",
  "gebruikersWeergave": "Jan Jansen",
  "actie": "create",
  "actieWeergave": "Aangemaakt",
  "resultaat": 201,
  "hoofdObject": "https://drc.example.com/api/v1/enkelvoudiginformatieobjecten/550e8400-e29b-41d4-a716-446655440000",
  "resource": "enkelvoudiginformatieobject",
  "resourceUrl": "https://drc.example.com/api/v1/enkelvoudiginformatieobjecten/550e8400-e29b-41d4-a716-446655440000",
  "resourceWeergave": "Besluit vergunning omgevingsrecht",
  "toelichting": "",
  "wijzigingen": {"oud": {}, "nieuw": {"titel": "Besluit vergunning omgevingsrecht"}},
  "aanmaakdatum": "2024-01-15T10:30:00Z"
}""",
)
@Serializable
data class AuditTrailResponse(
    @JsonSchema.Description("De UUID van deze audittrail-regel.")
    @JsonSchema.Format("uuid")
    @JsonSchema.ReadOnly
    val uuid: String,

    @JsonSchema.Description(
        "De naam van het systeem (component) dat de actie heeft uitgevoerd. " +
            "Mogelijke waarden: ac (Autorisaties API), nrc (Notificaties API), zrc (Zaken API), " +
            "ztc (Catalogi API), drc (Documenten API), brc (Besluiten API), " +
            "cmc (Contactmomenten API), kc (Klanten API), vrc (Verzoeken API).",
    )
    val bron: String?,

    @JsonSchema.Description("De ID van de applicatie die de actie heeft uitgevoerd.")
    val applicatieId: String?,

    @JsonSchema.Description("Een leesbare weergave van de applicatie die de actie heeft uitgevoerd.")
    val applicatieWeergave: String?,

    @JsonSchema.Description("De ID van de gebruiker die de actie heeft uitgevoerd.")
    val gebruikersId: String?,

    @JsonSchema.Description("Een leesbare weergave van de gebruiker die de actie heeft uitgevoerd.")
    val gebruikersWeergave: String?,

    @JsonSchema.Description("De uitgevoerde actie, bijv. create, update, destroy, retrieve, list.")
    val actie: String?,

    @JsonSchema.Description("Een leesbare omschrijving van de uitgevoerde actie.")
    val actieWeergave: String?,

    @JsonSchema.Description("De HTTP-statuscode van het resultaat van de actie, bijv. 200, 201, 204.")
    val resultaat: Int?,

    @JsonSchema.Description("URL van het hoofd-object (het INFORMATIEOBJECT) waarop de actie betrekking heeft.")
    @JsonSchema.Format("uri")
    val hoofdObject: String?,

    @JsonSchema.Description("De naam van de resource, bijv. enkelvoudiginformatieobject.")
    val resource: String?,

    @JsonSchema.Description("URL van de specifieke resource waarop de actie is uitgevoerd.")
    @JsonSchema.Format("uri")
    val resourceUrl: String?,

    @JsonSchema.Description("Een leesbare weergave van de resource waarop de actie is uitgevoerd (bijv. de documenttitel).")
    val resourceWeergave: String?,

    @JsonSchema.Description("Optionele toelichting bij de actie.")
    val toelichting: String?,

    @JsonSchema.Description(
        "De gewijzigde velden met hun oude en nieuwe waarden, weergegeven als twee objecten: 'oud' (situatie vóór de actie) en 'nieuw' (situatie ná de actie).",
    )
    val wijzigingen: Wijzigingen,

    @JsonSchema.Description("De datum-tijd waarop de audittrail-regel is aangemaakt (ISO 8601 date-time).")
    @JsonSchema.Format("date-time")
    @JsonSchema.ReadOnly
    val aanmaakdatum: Instant?,
) : ApiResponse
