// SPDX-License-Identifier: EUPL-1.2
// Copyright (C) 2026 Gemeente Utrecht
package com.baseflow.shared.services.models

import com.baseflow.shared.api.models.ObjectInformatieObjectResponse

/**
 * Result of creating an ObjectInformatieObject relation
 */
sealed class CreateOIOResult {
    data class Success(val payload: ObjectInformatieObjectResponse) : CreateOIOResult()
    data class Conflict(val message: String) : CreateOIOResult()
}

/**
 * Result of deleting an ObjectInformatieObject relation
 */
sealed class DeleteOIOResult {
    object Success : DeleteOIOResult()
    object NotFound : DeleteOIOResult()
}
