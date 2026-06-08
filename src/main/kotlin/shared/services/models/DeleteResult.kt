// SPDX-License-Identifier: EUPL-1.2
// Copyright (C) 2026 Gemeente Utrecht
package com.baseflow.shared.services.models

sealed class DeleteResult {
    data object Success : DeleteResult()
    data object NotFound : DeleteResult()
    data object Locked : DeleteResult()
    data object HasReferences : DeleteResult()
}
