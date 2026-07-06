// SPDX-License-Identifier: EUPL-1.2
// Copyright (C) 2026 Gemeente Utrecht
package com.baseflow.shared.services.models

import kotlinx.serialization.Serializable

@Serializable
public data class SlatPayload(val fileId: String, val expiresAt: Long, val userId: String)
