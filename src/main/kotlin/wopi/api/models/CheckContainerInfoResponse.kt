// SPDX-License-Identifier: EUPL-1.2
// Copyright (C) 2026 Gemeente Utrecht
package com.baseflow.wopi.api.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Response body for the WOPI CheckContainerInfo operation.
 *
 * See https://learn.microsoft.com/en-us/microsoft-365/cloud-storage-partner-program/rest/containers/checkcontainerinfo
 */
@Serializable
data class CheckContainerInfoResponse(
    /** The display name of the container (e.g. the bronorganisatie code). */
    @SerialName("Name")
    val name: String,

    /**
     * Identifies the type of container. Use "Folder" for a generic folder container.
     * Clients use this to select an appropriate icon and behaviour.
     */
    @SerialName("FolderType")
    val folderType: String = "Folder",

    /** Whether the current user can delete files in this container. */
    @SerialName("UserCanDelete")
    val userCanDelete: Boolean = true,

    /** Whether the current user can rename files in this container. */
    @SerialName("UserCanRename")
    val userCanRename: Boolean = true,

    /** Whether the container is read-only for the current user. */
    @SerialName("IsReadOnly")
    val isReadOnly: Boolean = false,

    /** Whether the host supports the DeleteFile operation on files in this container. */
    @SerialName("SupportsDeleteFile")
    val supportsDeleteFile: Boolean = true,

    /** Whether the host supports the PutRelativeFile operation on files in this container. */
    @SerialName("SupportsPutRelativeFile")
    val supportsPutRelativeFile: Boolean = true,

    /** Whether the host supports renaming files in this container. */
    @SerialName("SupportsRenameFile")
    val supportsRenameFile: Boolean = true,
)
