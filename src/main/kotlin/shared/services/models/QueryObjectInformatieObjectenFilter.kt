// SPDX-License-Identifier: EUPL-1.2
// Copyright (C) 2026 Gemeente Utrecht
package com.baseflow.shared.services.models

/**
 * Filter parameters for querying ObjectInformatieObjecten
 */
data class QueryObjectInformatieObjectenFilter(
    val informatieobject: String? = null,
    val subjectObject: String? = null,
    val expand: List<String> = emptyList(),
    val page: Int = 1,
    /**
     * Default pageSize set to 100 to align with Open Zaak (DRF) default behavior.
     * NOTE: pageSize is not yet defined in the Documenten API 1.5.0 specification.
     * This parameter is an extension used for pagination control and might require filing a ticket for the official spec.
     */
    val pageSize: Int = 100,
)
