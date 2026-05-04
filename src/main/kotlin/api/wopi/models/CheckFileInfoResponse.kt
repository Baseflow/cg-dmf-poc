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
    @SerialName("UserFriendlyName")
    val userFriendlyName: String,
    @SerialName("UserCanWrite")
    val userCanWrite: Boolean,
    @SerialName("SupportsLocks")
    val supportsLocks: Boolean,
    @SerialName("SupportsGetLock")
    val supportsGetLock: Boolean,
    @SerialName("SupportsUpdate")
    val supportsUpdate: Boolean,
    @SerialName("SupportsAutosave")
    val supportsAutosave: Boolean,
    @SerialName("LastModifiedTime")
    val lastModifiedTime: String,
    @SerialName("Version")
    val version: String? = null,
)
