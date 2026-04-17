// SPDX-License-Identifier: EUPL-1.2
// Copyright (C) 2026 Gemeente Utrecht
package com.baseflow.api.wopi.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
@Serializable
data class CheckFileInfoResponse(
    @SerialName("BaseFileName")
    val baseFileName: String,
    @SerialName("Size")
    val size: Long,
)
