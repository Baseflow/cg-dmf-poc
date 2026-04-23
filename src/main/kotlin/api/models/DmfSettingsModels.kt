// SPDX-License-Identifier: EUPL-1.2
// Copyright (C) 2026 Gemeente Utrecht
package com.baseflow.api.models

import kotlinx.serialization.Serializable

@Serializable
data class DmfSettingsResponse(
    val triggerSize: Long,
    val chunkSize: Long,
    val validationEnabled: Boolean,
)

@Serializable
data class UpdateDmfSettingsRequest(
    val triggerSize: Long,
    val chunkSize: Long,
    val validationEnabled: Boolean,
)
