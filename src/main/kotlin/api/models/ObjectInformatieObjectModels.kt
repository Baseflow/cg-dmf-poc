// SPDX-License-Identifier: EUPL-1.2
// Copyright (C) 2025-2026 Gemeente Utrecht
package com.baseflow.api.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * ObjectInformatieObject request model for creating a relation
 */
@Serializable
data class CreateOIORequest(
    val informatieobject: String,
    @SerialName("object")
    val subjectObject: String,
    @SerialName("objectType")
    val subjectType: SubjectTypeEnum,
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
@ResourceSegment(ResourceSegments.OBJECT_INFORMATIE_OBJECTEN)
data class ObjectInformatieObjectResponse(
    override val id: String? = null,
    override val url: String? = null,
    val informatieobject: String,
    @SerialName("object")
    val subjectObject: String,
    @SerialName("objectType")
    val subjectType: SubjectTypeEnum,
) : ApiEntityResponse

/**
 * SubjectType/ObjectType enum according to the API specification
 */
@Serializable
enum class SubjectTypeEnum {
    @SerialName("besluit")
    BESLUIT,

    @SerialName("zaak")
    ZAAK,

    @SerialName("verzoek")
    VERZOEK,
}
