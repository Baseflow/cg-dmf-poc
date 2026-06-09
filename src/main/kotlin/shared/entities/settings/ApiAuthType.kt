// SPDX-License-Identifier: EUPL-1.2
// Copyright (C) 2026 Gemeente Utrecht
package com.baseflow.shared.entities.settings

enum class ApiAuthType(val value: String) {
    ZGW_AUTH("zgw-auth"),

    // TODO: Bearer token authentication is not yet implemented in CatalogusService/NotificationService.
    BEARER("bearer"),

    NONE("none"),
}
