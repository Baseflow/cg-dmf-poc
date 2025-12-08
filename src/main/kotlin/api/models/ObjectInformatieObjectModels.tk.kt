// SPDX-License-Identifier: EUPL-1.2
// Copyright (C) 2025 Gemeente Utrecht
package com.baseflow.api.models

import kotlinx.serialization.Serializable

/**
 * Simple EnkelvoudigInformatieObject response model
 */
@Serializable
data class ObjectInformatieObjectResponse(
    val id: String,
    val versie: Int,
    val taal: String? = null,
    val bestandsnaam: String? = null
)