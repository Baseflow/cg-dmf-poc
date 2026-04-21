// SPDX-License-Identifier: EUPL-1.2
// Copyright (C) 2026 Gemeente Utrecht
package com.baseflow.api.wopi

import com.baseflow.api.wopi.routes.wopiApiRoutes
import com.baseflow.config.WopiConfig
import io.ktor.server.application.Application
import io.ktor.server.routing.routing

fun Application.wopiApiModule() {
    if (!WopiConfig.isEnabled()) {
        return
    }
    routing {
        wopiApiRoutes()
    }
}
