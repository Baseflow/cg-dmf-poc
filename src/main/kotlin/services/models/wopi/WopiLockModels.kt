// SPDX-License-Identifier: EUPL-1.2
// Copyright (C) 2026 Gemeente Utrecht
package com.baseflow.services.models.wopi

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
    data object InvalidLock : WopiUnlockResult()
    data object NotLocked : WopiUnlockResult()
}
