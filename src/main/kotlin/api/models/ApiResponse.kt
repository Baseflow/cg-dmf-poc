// SPDX-License-Identifier: EUPL-1.2
// Copyright (C) 2026 Gemeente Utrecht
package com.baseflow.api.models

interface ApiResponse {
}

interface ApiEntityResponse : ApiResponse {
    // NOTE id is not part of the spec, but included by us for convenience
    val id: String?
    // URL is the actual identity representation
    val url: String?
}