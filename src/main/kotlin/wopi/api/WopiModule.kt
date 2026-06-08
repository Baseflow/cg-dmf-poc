// SPDX-License-Identifier: EUPL-1.2
// Copyright (C) 2026 Gemeente Utrecht
package com.baseflow.wopi.api

import com.baseflow.shared.config.WopiConfig
import com.baseflow.wopi.api.routes.wopiApiRoutes
import io.ktor.server.application.Application
import io.ktor.server.routing.routing

fun Application.wopiApiModule(config: WopiConfig) {
    if (!config.isEnabled()) {
        return
    }

    routing {
        wopiApiRoutes()
    }
}
