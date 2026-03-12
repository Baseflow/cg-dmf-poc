// SPDX-License-Identifier: EUPL-1.2
// Copyright (C) 2026 Gemeente Utrecht
package com.baseflow.services.models

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
)
