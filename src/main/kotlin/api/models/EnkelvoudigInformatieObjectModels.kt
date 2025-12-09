// SPDX-License-Identifier: EUPL-1.2
// Copyright (C) 2025 Gemeente Utrecht
package com.baseflow.api.models

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.serialization.Serializable

/**
 * Simple EnkelvoudigInformatieObject request model
 */
@Serializable
data class CreateEIORequest(
    val taal: String? = null,
    val bestandsnaam: String? = null
)


@Serializable
data class Ondertekening(
    val soort: String,
    val datum: String
)

@Serializable
data class Integriteit(
    val algoritme: String,
    val waarde: String,
    val datum: String
)

@Serializable
data class EnkelvoudigInformatieObjectResponse(
    val identificatie: String,
    val bronorganisatie: String,
    val creatiedatum: LocalDate,
    val titel: String,
    val versie: Int,
    val vertrouwelijkheidaanduiding: String,
    val auteur: String,
    val status: String,
    val formaat: String,
    val taal: String,
    val bestandsnaam: String,
    val inhoud: String,
    val bestandsomvang: Long?,
    val link: String,
    val beschrijving: String,
    val beginRegistratie: LocalDateTime,
    val indicatieGebruiksrecht: Boolean,
    val verschijningsvorm: String,
    val ondertekening: Ondertekening,
    val integriteit: Integriteit,
    val informatieobjecttype: String,
    val trefwoorden: List<String>,
    val inhoudIsVervallen: Boolean,
    val locked: Boolean
)