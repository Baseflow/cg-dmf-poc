package com.baseflow.services.models

sealed class DeleteResult {
    data object Success : DeleteResult()
    data object NotFound : DeleteResult()
    data object Locked : DeleteResult()
}
