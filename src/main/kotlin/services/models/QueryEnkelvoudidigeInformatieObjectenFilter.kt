// SPDX-License-Identifier: EUPL-1.2
// Copyright (C) 2025-2026 Gemeente Utrecht
package com.baseflow.services.models

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime

/**
 * Defines the valid ordering options for EnkelvoudigInformatieObject queries.
 * Prefix with '-' for descending order (e.g. "-auteur").
 */
enum class EIOOrdering(val value: String) {
    AUTEUR_ASC("auteur"),
    AUTEUR_DESC("-auteur"),
    BESTANDSOMVANG_ASC("bestandsomvang"),
    BESTANDSOMVANG_DESC("-bestandsomvang"),
    CREATIEDATUM_ASC("creatiedatum"),
    CREATIEDATUM_DESC("-creatiedatum"),
    FORMAAT_ASC("formaat"),
    FORMAAT_DESC("-formaat"),
    STATUS_ASC("status"),
    STATUS_DESC("-status"),
    TITEL_ASC("titel"),
    TITEL_DESC("-titel"),
    VERTROUWELIJKHEIDAANDUIDING_ASC("vertrouwelijkheidaanduiding"),
    VERTROUWELIJKHEIDAANDUIDING_DESC("-vertrouwelijkheidaanduiding");

    companion object {
        fun fromValue(value: String): EIOOrdering? = entries.firstOrNull { it.value == value }
    }
}

class QueryEnkelvoudigeInformatieObjectenFilter(
    val bronOrganisatie: String? = null,
    val trefwoorden: List<String> = emptyList(),
    val identificatie: String? = null,
    val page: Int = 1,
    /**
     * Default pageSize set to 100 to align with Open Zaak (DRF) default behavior.
     * NOTE: pageSize is not yet defined in the Documenten API 1.5.0 specification.
     * This parameter is an extension used for pagination control and might require filing a ticket for the official spec.
     */
    val pageSize: Int = 100,
    val uuids: List<String> = emptyList(),
    /** EXPERIMENTEEL: URL-referentie naar de gerelateerde OBJECT */
    val objectUrl: String? = null,
    /** EXPERIMENTEEL: Het type van het gerelateerde OBJECT */
    val objectType: String? = null,
    /** EXPERIMENTEEL: URL-referentie naar het INFORMATIEOBJECTTYPE */
    val informatieobjecttype: String? = null,
    /** EXPERIMENTEEL: Comma-separated list of vertrouwelijkheidaanduiding values */
    val vertrouwelijkheidaanduiding: List<String> = emptyList(),
    /** EXPERIMENTEEL: Titel van het informatieobject (case-insensitive contains) */
    val titel: String? = null,
    /** EXPERIMENTEEL: Auteur van het informatieobject (case-insensitive contains) */
    val auteur: String? = null,
    /** EXPERIMENTEEL: Status van het informatieobject (exact match) */
    val status: String? = null,
    /** EXPERIMENTEEL: Beschrijving van het informatieobject (case-insensitive contains) */
    val beschrijving: String? = null,
    /** EXPERIMENTEEL: creatiedatum strictly before this date */
    val creatiedatumLt: LocalDate? = null,
    /** EXPERIMENTEEL: creatiedatum on or before this date */
    val creatiedatumLte: LocalDate? = null,
    /** EXPERIMENTEEL: creatiedatum strictly after this date */
    val creatiedatumGt: LocalDate? = null,
    /** EXPERIMENTEEL: creatiedatum on or after this date */
    val creatiedatumGte: LocalDate? = null,
    /** EXPERIMENTEEL: beginRegistratie (registratiedatum) strictly before this datetime (ISO-8601) */
    val registratiedatumLt: LocalDateTime? = null,
    /** EXPERIMENTEEL: beginRegistratie (registratiedatum) on or before this datetime (ISO-8601) */
    val registratiedatumLte: LocalDateTime? = null,
    /** EXPERIMENTEEL: beginRegistratie (registratiedatum) strictly after this datetime (ISO-8601) */
    val registratiedatumGt: LocalDateTime? = null,
    /** EXPERIMENTEEL: beginRegistratie (registratiedatum) on or after this datetime (ISO-8601) */
    val registratiedatumGte: LocalDateTime? = null,
    /** EXPERIMENTEEL: locked (boolean) indication whether the information object is locked for editing */
    val locked: Boolean? = null,
    /** EXPERIMENTEEL: Ordering of the results. Prefix with '-' for descending order. */
    val ordering: List<EIOOrdering> = emptyList(),
)
