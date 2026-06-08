// SPDX-License-Identifier: EUPL-1.2
// Copyright (C) 2026 Gemeente Utrecht
package com.baseflow.shared.services.models

import io.ktor.openapi.JsonSchema

@JsonSchema.Title("LockToken")
@JsonSchema.Description("Het vergrendel-token dat ontvangen wordt na het vergrendelen (lock/checkout) van een informatieobject.")
@JsonSchema.Example("""{"lock": "c7d72de0-2ba1-4e73-8a4a-9b6de2f1d3e0"}""")
@kotlinx.serialization.Serializable
data class LockPayload(
    @JsonSchema.Description("Het vergrendel-token (UUID). Bewaar dit token — het is nodig voor PUT/PATCH en voor unlock.")
    @JsonSchema.Format("uuid")
    val lock: String,
)

sealed class LockResult {
    data class Success(val payload: LockPayload) : LockResult()
    data object AlreadyLocked : LockResult()
}

sealed class UnlockResult {
    data object Success : UnlockResult()
    data object InvalidLock : UnlockResult()
    data object NotLocked : UnlockResult()
}
