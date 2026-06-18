// SPDX-License-Identifier: EUPL-1.2
// Copyright (C) 2026 Gemeente Utrecht
package com.baseflow.shared.api.models.settings

import kotlinx.serialization.Serializable

@Serializable
data class DmfSettingEntry(val key: String, val type: String, val value: String, val updatedAt: String)

@Serializable
data class UpsertDmfSettingRequest(val value: String)
