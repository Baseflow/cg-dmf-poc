// SPDX-License-Identifier: EUPL-1.2
// Copyright (C) 2026 Gemeente Utrecht
package com.baseflow.wopi.web

import com.baseflow.shared.config.WopiConfig
import io.ktor.server.application.Application
import io.ktor.server.routing.routing

fun Application.wopiWebModule(config: WopiConfig) {
    if (!config.isEnabled()) {
        return
    }

    routing {
        wopiWebRoutes()
    }
}
