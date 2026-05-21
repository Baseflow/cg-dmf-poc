// SPDX-License-Identifier: EUPL-1.2
// Copyright (C) 2026 Gemeente Utrecht
package com.baseflow.api.middleware

import com.baseflow.api.models.ApiEntityResponse
import com.baseflow.entities.IAuditContext

class AuditContext {
    var oldValue: ApiEntityResponse? = null
        private set
    var newValue: ApiEntityResponse? = null
        private set
    var sourceRequest: IAuditContext? = null
        private set

    fun captureOld(entity: ApiEntityResponse?, sourceRequest: IAuditContext? = null) {
        oldValue = entity
        if (sourceRequest != null) this.sourceRequest = sourceRequest
    }

    fun captureNew(entity: ApiEntityResponse?, sourceRequest: IAuditContext? = null) {
        newValue = entity
        if (sourceRequest != null) this.sourceRequest = sourceRequest
    }

    fun hasChanges(): Boolean = oldValue != null || newValue != null

    val resourceWeergave: String
        get() = (
            if (sourceRequest != null) {
                "${sourceRequest?.bronOrganisatie} - ${sourceRequest?.identificatie}"
            } else {
                "unknown resource"
            }
            )
}
