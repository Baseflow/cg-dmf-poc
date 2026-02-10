// SPDX-License-Identifier: EUPL-1.2
// Copyright (C) 2026 Gemeente Utrecht

package api.middleware

import com.baseflow.api.models.ApiEntityResponse

class AuditContext {
    var oldValue: ApiEntityResponse? = null
        private set
    var newValue: ApiEntityResponse? = null
        private set
    var customId: String? = null
        private set

    fun captureOld(entity: ApiEntityResponse?) {
        oldValue = entity
    }

    fun captureNew(entity: ApiEntityResponse?, customId: String? = null) {
        newValue = entity
        this.customId = customId
    }

    fun hasChanges(): Boolean = oldValue != null || newValue != null
}