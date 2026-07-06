// SPDX-License-Identifier: EUPL-1.2
// Copyright (C) 2026 Gemeente Utrecht
package com.baseflow.wopi.shared.tooling

import io.ktor.server.application.ApplicationCall

fun ApplicationCall.getAccessToken(): String? = parameters["access_token"]
    ?: request.headers["Authorization"]?.removePrefix("Bearer ")?.trim()
