// SPDX-License-Identifier: EUPL-1.2
// Copyright (C) 2026 Gemeente Utrecht
package com.baseflow.wopi.api.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Response body for a successful WOPI PutRelativeFile operation.
 * See https://learn.microsoft.com/en-us/microsoft-365/cloud-storage-partner-program/rest/files/putrelativefile
 */
@Serializable
data class PutRelativeFileResponse(
    /** The base file name of the newly created file, without a path component. */
    @SerialName("Name")
    val name: String,

    /** A URI to the WOPI files endpoint for the new file. */
    @SerialName("Url")
    val url: String,
)
