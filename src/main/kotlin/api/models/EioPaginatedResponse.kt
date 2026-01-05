// SPDX-License-Identifier: EUPL-1.2
// Copyright (C) 2025 Gemeente Utrecht
package com.baseflow.api.models

import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable

/**
 * Paginated response specifically for EnkelvoudigInformatieObject results.
 *
 * Note: kotlinx.serialization doesn't properly resolve @Contextual annotations through
 * type aliases or generic type parameters. The explicit declaration ensures the
 * UrlAugmentingSerializer is correctly applied to each item in the results list.
 */
@Serializable
data class EioPaginatedResponse(
    val count: Int,
    val next: String? = null,
    val previous: String? = null,
    val results: List<@Contextual EnkelvoudigInformatieObjectResponse>
)
