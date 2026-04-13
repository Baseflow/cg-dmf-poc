// SPDX-License-Identifier: EUPL-1.2
// Copyright (C) 2025-2026 Gemeente Utrecht
package com.baseflow.api.models

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/**
 * ObjectInformatieObject request model for creating a relation
 */
@Serializable
data class CreateOIORequest(
    val informatieobject: String,
    @SerialName("object")
    val subjectObject: String,
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
    override val id: String? = null,
    override val url: String? = null,
    val informatieobject: String,
    @SerialName("object")
    val subjectObject: String,
    @SerialName("objectType")
    val subjectType: SubjectType,
) : ApiEntityResponse

/**
 * SubjectType is a validated string representing the type of object related to an informatieobject.
 * It must be a single word (letters, digits, and dashes only; no spaces or other special characters).
 *
 * Examples of valid values: "zaak", "besluit", "verzoek", "mijn-object-type"
 */
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
