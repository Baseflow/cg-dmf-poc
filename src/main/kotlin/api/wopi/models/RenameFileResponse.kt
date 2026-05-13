// SPDX-License-Identifier: EUPL-1.2
// Copyright (C) 2026 Gemeente Utrecht
package com.baseflow.api.wopi.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Response body for a successful WOPI RenameFile operation.
 * See https://learn.microsoft.com/en-us/microsoft-365/cloud-storage-partner-program/rest/files/renamefile
 */
@Serializable
data class RenameFileResponse(
    /** The new base file name, without a path component, after the rename. */
    @SerialName("Name")
    val name: String,
)
