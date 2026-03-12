// SPDX-License-Identifier: EUPL-1.2
// Copyright (C) 2026 Gemeente Utrecht
package com.baseflow.api.middleware

import com.baseflow.api.models.ApiEntityResponse
import com.baseflow.config.RequestScope
import com.baseflow.entities.IAuditContext
import org.koin.core.annotation.Scope
import org.koin.core.annotation.Scoped

@Scope(RequestScope::class)
@Scoped
class AuditContext {
    var oldValue: ApiEntityResponse? = null
        private set
    var newValue: ApiEntityResponse? = null
        private set
    var sourceRequest: IAuditContext? = null
        private set

    fun captureOld(entity: ApiEntityResponse?) {
        oldValue = entity
    }

    fun captureNew(entity: ApiEntityResponse?, sourceRequest: IAuditContext? = null) {
        newValue = entity
        this.sourceRequest = sourceRequest
    }

    fun hasChanges(): Boolean = oldValue != null || newValue != null

    val resourceWeergave: String
        get() = (if (sourceRequest != null) "${sourceRequest?.bronOrganisatie} - ${sourceRequest?.identificatie}"
                 else "unknown resource")
}
