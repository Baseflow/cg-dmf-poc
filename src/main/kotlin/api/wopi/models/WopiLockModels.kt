// SPDX-License-Identifier: EUPL-1.2
// Copyright (C) 2026 Gemeente Utrecht
package com.baseflow.api.wopi.models

import com.baseflow.api.models.EnkelvoudigInformatieObjectResponse
import io.ktor.openapi.JsonSchema
import kotlinx.serialization.Serializable

@JsonSchema.Title("WopiLockToken")
@JsonSchema.Example("""{"lock": "cool-lock12345678"}""")
@Serializable
data class WopiLockPayload(val lock: String)

sealed class WopiLockResult {
    data object Success : WopiLockResult()

    data object AlreadyLocked : WopiLockResult()

    data class LockMismatch(val currentFileLock: WopiLockPayload) : WopiLockResult()
}

sealed class WopiUnlockResult {
    data object Success : WopiUnlockResult()
    data object NotLocked : WopiUnlockResult()
    data class LockMismatch(val currentFileLock: WopiLockPayload) : WopiUnlockResult()
}

sealed class WopiPutFileResult {
    /** File was saved successfully; contains the updated response. */
    data class Success(val response: EnkelvoudigInformatieObjectResponse) : WopiPutFileResult()

    /** The caller supplied no lock but the file has content (non-empty file requires a lock). */
    data object LockRequired : WopiPutFileResult()

    /** The supplied lock does not match the current lock on the file. */
    data class LockMismatch(val currentLock: String) : WopiPutFileResult()

    /** No document with the given id exists. */
    data object NotFound : WopiPutFileResult()
}

sealed class WopiRenameResult {
    /** File was renamed successfully. */
    data object Success : WopiRenameResult()

    /** No document with the given id exists. */
    data object NotFound : WopiRenameResult()

    /** The supplied lock does not match the current lock on the file. */
    data class LockMismatch(val currentLock: String) : WopiRenameResult()
}

sealed class WopiDeleteResult {
    /** File was deleted successfully. */
    data object Success : WopiDeleteResult()

    /** No document with the given id exists. */
    data object NotFound : WopiDeleteResult()

    /** The file has related resources and cannot be deleted. */
    data object HasReferences : WopiDeleteResult()

    /** The file is currently locked; deletion is not allowed. */
    data class Locked(val currentLock: String) : WopiDeleteResult()
}

sealed class WopiPutRelativeFileResult {
    /**
     * New file created successfully.
     * [newFileId] is the UUID of the newly created EIO.
     * [resolvedName] is the actual file name used (may differ from the requested name if an
     * exact-overwrite was not requested and a collision was resolved).
     */
    data class Success(val newFileId: java.util.UUID, val resolvedName: String) : WopiPutRelativeFileResult()

    /** The source document with the given id does not exist. */
    data object SourceNotFound : WopiPutRelativeFileResult()

    /**
     * A file with the requested name already exists and [X-WOPI-Overwrite-Relative-Target] was
     * false (or absent). [validRelativeTarget] is a suggested alternative name the host has
     * confirmed is available.
     */
    data class NameConflict(val validRelativeTarget: String) : WopiPutRelativeFileResult()

    /**
     * The target file exists and is currently locked by a WOPI client.
     * [currentLock] is the lock token that must be presented to overwrite it.
     */
    data class TargetLocked(val currentLock: String) : WopiPutRelativeFileResult()
}
