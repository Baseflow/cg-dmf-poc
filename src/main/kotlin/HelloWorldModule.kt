// SPDX-License-Identifier: EUPL-1.2
// Copyright (C) 2025-2026 Gemeente Utrecht
package com.baseflow

import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.routing.openapi.hide
import io.ktor.utils.io.ExperimentalKtorApi

@OptIn(ExperimentalKtorApi::class)
fun Application.helloWorldModule() {
    routing {
        /**
         * Hello world endpoint.
         *
         * Responses:
         *   - 200 Prints 'hello world'.
         */
        get("/") {
            call.respondText("hello world")
        }
            .hide()
    }
}
