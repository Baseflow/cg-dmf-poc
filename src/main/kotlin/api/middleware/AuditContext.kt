// SPDX-License-Identifier: EUPL-1.2
// Copyright (C) 2026 Gemeente Utrecht

package com.baseflow.api.middleware

import com.baseflow.api.models.ApiEntityResponse
import com.baseflow.config.RequestScope
import org.koin.core.annotation.Scope
import org.koin.core.annotation.Scoped

@Scope(RequestScope::class)
@Scoped
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

