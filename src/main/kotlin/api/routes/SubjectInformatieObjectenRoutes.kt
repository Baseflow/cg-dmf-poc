// SPDX-License-Identifier: EUPL-1.2
// Copyright (C) 2026 Gemeente Utrecht
package com.baseflow.api.routes

import io.ktor.server.routing.*

/**
 * SubjectInformatieObject routes
 *
 * EXPERIMENTAL: This handles relations between documents and subject objects.
 * Similar to ObjectInformatieObjecten, but with pagination support.
 */
fun Route.subjectInformatieObjectenRoutes() {
    ObjectInformatieObjectenRoutes(this, "subjectinformatieobjecten", experimental = true).register()
}
