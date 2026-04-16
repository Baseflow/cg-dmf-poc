// SPDX-License-Identifier: EUPL-1.2
// Copyright (C) 2025-2026 Gemeente Utrecht
package com.baseflow.api.models

import io.ktor.openapi.JsonSchema
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/**
 * ObjectInformatieObject request model for creating a relation
 */
@JsonSchema.Title("ObjectInformatieObjectRequest")
@JsonSchema.Description(
    "Request-model voor het aanmaken van een relatie tussen een INFORMATIEOBJECT en een ander object (bijv. zaak, besluit of custom objecttype).",
)
@JsonSchema.Example(
    """{
  "informatieobject": "https://drc.example.com/api/v1/enkelvoudiginformatieobjecten/550e8400-e29b-41d4-a716-446655440000",
  "object": "https://zaken.example.com/api/v1/zaken/6ba7b810-9dad-11d1-80b4-00c04fd430c8",
  "objectType": "zaak"
}""",
)
@Serializable
data class CreateOIORequest(
    @JsonSchema.Description("URL-referentie naar het te koppelen INFORMATIEOBJECT.")
    @JsonSchema.Format("uri")
    @JsonSchema.MaxLength(1000)
    @JsonSchema.Example("\"https://drc.example.com/api/v1/enkelvoudiginformatieobjecten/550e8400-e29b-41d4-a716-446655440000\"")
    val informatieobject: String,

    @JsonSchema.Description("URL-referentie naar het object waaraan het INFORMATIEOBJECT gekoppeld wordt (bijv. zaak, besluit, verzoek).")
    @JsonSchema.Format("uri")
    @JsonSchema.MaxLength(1000)
    @JsonSchema.Example("\"https://zaken.example.com/api/v1/zaken/6ba7b810-9dad-11d1-80b4-00c04fd430c8\"")
    @SerialName("object")
    val subjectObject: String,

    @JsonSchema.Description(
        "Het type van het gerelateerde OBJECT. Standaard objecttypen: `zaak`, `besluit`. " +
            "**EXPERIMENTEEL**: dit PoC breidt de standaard uit met ondersteuning voor elk geldig objecttype " +
            "in kleine letters met optionele koppeltekens (bijv. `verzoek`, `mijn-object-type`).",
    )
    @JsonSchema.Pattern("^[a-z0-9]+(-[a-z0-9]+)*$")
    @JsonSchema.Example("\"zaak\"")
    @SerialName("objectType")
    val subjectType: SubjectType,
) : ApiRequest {
    init {
        require(informatieobject.isNotBlank()) { "Informatieobject mag niet leeg zijn" }
        require(subjectObject.isNotBlank()) { "Object mag niet leeg zijn" }
        require(informatieobject.length <= 1000) { "Informatieobject mag maximaal 1000 karakters lang zijn" }
        require(subjectObject.length <= 1000) { "Object mag maximaal 1000 karakters lang zijn" }
    }
}

/**
 * ObjectInformatieObject response model
 */
@JsonSchema.Title("ObjectInformatieObject")
@JsonSchema.Description(
    "Een relatie tussen een INFORMATIEOBJECT en een ander object (bijv. zaak, besluit of custom objecttype). " +
        "Koppelt een document aan een object uit een andere API.",
)
@JsonSchema.Example(
    """{
  "url": "https://drc.example.com/api/v1/objectinformatieobjecten/3fa85f64-5717-4562-b3fc-2c963f66afa6",
  "informatieobject": "https://drc.example.com/api/v1/enkelvoudiginformatieobjecten/550e8400-e29b-41d4-a716-446655440000",
  "object": "https://zaken.example.com/api/v1/zaken/6ba7b810-9dad-11d1-80b4-00c04fd430c8",
  "objectType": "zaak"
}""",
)
@Serializable
/**
 * The ResourceSegment annotation is used to associate this response model with the correct API endpoint segment for
 * documentation and routing purposes. It indicates that this response model corresponds to the
 * "object-informatie-objecten" segment of the API.
 *
 * Both used for objectInformatieObjectenRoutes & subjectInformatieObjectenRoutes, but the latter route is not
 * definite yet and marked as experimental, so we keep the annotation here for now to ensure correct documentation
 * generation.
 */
@ResourceSegment(ResourceSegments.OBJECT_INFORMATIE_OBJECTEN)
data class ObjectInformatieObjectResponse(
    @JsonSchema.Description("De UUID van deze OBJECTINFORMATIEOBJECT relatie.")
    @JsonSchema.Format("uuid")
    @JsonSchema.ReadOnly
    override val id: String? = null,

    @JsonSchema.Description("De URL van deze OBJECTINFORMATIEOBJECT relatie.")
    @JsonSchema.Format("uri")
    @JsonSchema.ReadOnly
    override val url: String? = null,

    @JsonSchema.Description("URL-referentie naar het gekoppelde INFORMATIEOBJECT.")
    @JsonSchema.Format("uri")
    @JsonSchema.Example("\"https://drc.example.com/api/v1/enkelvoudiginformatieobjecten/550e8400-e29b-41d4-a716-446655440000\"")
    val informatieobject: String,

    @JsonSchema.Description("URL-referentie naar het object waaraan het INFORMATIEOBJECT gekoppeld is.")
    @JsonSchema.Format("uri")
    @JsonSchema.Example("\"https://zaken.example.com/api/v1/zaken/6ba7b810-9dad-11d1-80b4-00c04fd430c8\"")
    @SerialName("object")
    val subjectObject: String,

    @JsonSchema.Description(
        "Het type van het gerelateerde OBJECT. Standaard objecttypen: `zaak`, `besluit`. " +
            "**EXPERIMENTEEL**: dit PoC breidt de standaard uit met ondersteuning voor elk geldig objecttype " +
            "in kleine letters met optionele koppeltekens (bijv. `verzoek`, `mijn-object-type`).",
    )
    @JsonSchema.Pattern("^[a-z0-9]+(-[a-z0-9]+)*$")
    @JsonSchema.Example("\"zaak\"")
    @SerialName("objectType")
    val subjectType: SubjectType,
) : ApiEntityResponse

/**
 * SubjectType is a validated string representing the type of object related to an informatieobject.
 * It must be a single word (letters, digits, and dashes only; no spaces or other special characters).
 *
 * Examples of valid values: "zaak", "besluit", "verzoek", "mijn-object-type"
 */
@JsonSchema.Description(
    "Het type object dat gerelateerd is aan het INFORMATIEOBJECT. " +
        "Moet bestaan uit kleine letters, cijfers en koppeltekens (geen spaties). " +
        "Voorbeelden: zaak, besluit, verzoek, mijn-object-type.",
)
@Serializable(with = SubjectTypeSerializer::class)
@JvmInline
value class SubjectType private constructor(val value: String) {
    init {
        require(value.isNotBlank()) { "objectType mag niet leeg zijn" }
        require(value.matches(Regex("^[a-z0-9]+(-[a-z0-9]+)*$"))) {
            "objectType moet bestaan uit kleine letters, cijfers en koppeltekens, en mag niet beginnen of eindigen met een koppelteken (bijvoorbeeld: zaak of mijn-object-type)"
        }
    }

    companion object {
        operator fun invoke(value: String) = SubjectType(value.lowercase())
    }
}

object SubjectTypeSerializer : KSerializer<SubjectType> {
    override val descriptor = PrimitiveSerialDescriptor("SubjectType", kotlinx.serialization.descriptors.PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: SubjectType) {
        encoder.encodeString(value.value)
    }

    override fun deserialize(decoder: Decoder): SubjectType = SubjectType(decoder.decodeString())
}
