// SPDX-License-Identifier: EUPL-1.2
// Copyright (C) 2026 Gemeente Utrecht
package com.baseflow.wopi.api.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class ShareUrlType {

    @SerialName("ReadOnly")
    READ_ONLY,

    @SerialName("ReadWrite")
    READ_WRITE,
}

@Serializable
data class CheckFileInfoResponse(
    // Required response properties (see: https://learn.microsoft.com/en-us/microsoft-365/cloud-storage-partner-program/rest/files/checkfileinfo/checkfileinfo-response#required-response-properties)
    @SerialName("BaseFileName")
    val baseFileName: String,
    @SerialName("Size")
    val size: Long,
    @SerialName("LastModifiedTime")
    val lastModifiedTime: String,
    @SerialName("OwnerId")
    val ownerId: String,
    @SerialName("UserId")
    val userId: String,
    @SerialName("Version")
    val version: String,

    // WOPI host capabilities (see: https://learn.microsoft.com/en-us/microsoft-365/cloud-storage-partner-program/rest/files/checkfileinfo/checkfileinfo-response#wopi-host-capabilities-properties)
    @SerialName("SupportedShareUrlTypes")
    val supportedShareUrlTypes: List<ShareUrlType>? = null,
    // TODO(mvanbeusekom): The "SupportsAutosave" property is not part of the standard as documented by Microsoft.
    //                     We should investigate if this property is actually supported or if it is Collabora Online
    //                     specific.
    @SerialName("SupportsAutosave")
    val supportsAutosave: Boolean? = null,
    @SerialName("SupportsCobalt")
    val supportsCobalt: Boolean? = null,
    @SerialName("SupportsContainers")
    val supportsContainers: Boolean? = null,
    @SerialName("SupportsDeleteFile")
    val supportsDeleteFile: Boolean? = null,
    @SerialName("SupportsEcosystem")
    val supportsEcosystem: Boolean? = null,
    @SerialName("SupportsExtendedLockLength")
    val supportsExtendedLockLength: Boolean? = null,
    @SerialName("SupportsFolders")
    val supportsFolders: Boolean? = null,
    @SerialName("SupportsGetFileWopiSrc")
    val supportsGetFileWopiSrc: Boolean? = null,
    @SerialName("SupportsGetLock")
    val supportsGetLock: Boolean? = null,
    @SerialName("SupportsLocks")
    val supportsLocks: Boolean? = null,
    // TODO(mvanbeusekom): The "SupportsPutRelativeFile" property is not part of the standard as documented by Microsoft.
    //                     We should investigate if this property is actually supported or if it is Collabora Online
    //                     specific.
    @SerialName("SupportsPutRelativeFile")
    val supportsPutRelativeFile: Boolean? = null,
    @SerialName("SupportsRename")
    val supportsRename: Boolean? = null,
    @SerialName("SupportsUpdate")
    val supportsUpdate: Boolean? = null,
    @SerialName("SupportsUserInfo")
    val supportsUserInfo: Boolean? = null,

    // User metadata properties (see: https://learn.microsoft.com/en-us/microsoft-365/cloud-storage-partner-program/rest/files/checkfileinfo/checkfileinfo-response#user-metadata-properties)
    @SerialName("IsAnonymousUser")
    val isAnonymousUser: Boolean? = null,
    @SerialName("IsEduUser")
    val isEduUser: Boolean? = null,
    @SerialName("LicenseCheckForEditIsEnabled")
    val licenseCheckForEditIsEnabled: Boolean? = null,
    @SerialName("UserFriendlyName")
    val userFriendlyName: String? = null,
    @SerialName("UserInfo")
    val userInfo: String? = null,

    // User permissions properties (see: https://learn.microsoft.com/en-us/microsoft-365/cloud-storage-partner-program/rest/files/checkfileinfo/checkfileinfo-response#user-permissions-properties)
    @SerialName("ReadOnly")
    val readOnly: Boolean? = null,
    @SerialName("RestrictedWebViewOnly")
    val restrictedWebViewOnly: Boolean? = null,
    @SerialName("UserCanAttend")
    val userCanAttend: Boolean? = null,
    @SerialName("UserCanNotWriteRelative")
    val userCanNotWriteRelative: Boolean? = null,
    @SerialName("UserCanPresent")
    val userCanPresent: Boolean? = null,
    @SerialName("UserCanRename")
    val userCanRename: Boolean? = null,
    @SerialName("UserCanWrite")
    val userCanWrite: Boolean? = null,
)
