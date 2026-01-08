// SPDX-License-Identifier: EUPL-1.2
// Copyright (C) 2026 Gemeente Utrecht
package com.baseflow.services.models

/**
 * Filter parameters for querying ObjectInformatieObjecten
 */
data class QueryObjectInformatieObjectenFilter(
    val informatieobject: String? = null,
    val subjectObject: String? = null,
    val expand: List<String> = emptyList()
)

