package com.baseflow.services.models

@kotlinx.serialization.Serializable
data class LockPayload(val lock: String)

sealed class LockResult {
    data class Success(val payload: LockPayload) : LockResult()
    data object AlreadyLocked : LockResult()
}

sealed class UnlockResult {
    data object Success : UnlockResult()
    data object InvalidLock : UnlockResult()
    data object NotLocked : UnlockResult()
}