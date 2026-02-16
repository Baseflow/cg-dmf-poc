// SPDX-License-Identifier: EUPL-1.2
// Copyright (C) 2026 Gemeente Utrecht

package com.baseflow.api.middleware

import com.baseflow.api.models.ApiEntityResponse
import io.ktor.server.application.*
import org.koin.core.annotation.Scoped

@Scoped
class AuditContext(val call: ApplicationCall) {
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

