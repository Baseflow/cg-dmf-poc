// SPDX-License-Identifier: EUPL-1.2
// Copyright (C) 2025 Gemeente Utrecht
package com.baseflow

import org.jetbrains.exposed.v1.core.dao.id.UUIDTable

object EIORecords : UUIDTable("eio_records") {
    // Only id for now
}
