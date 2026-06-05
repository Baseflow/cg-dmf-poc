// SPDX-License-Identifier: EUPL-1.2
// Copyright (C) 2026 Gemeente Utrecht
package com.baseflow.shared.api.models

internal class UploadResultaat(
    val bestandsLocatie: String,
    val bestandsFormaat: String? = null,
    val bestandsOmvang: Long? = null,
    val bestandsRepository: String? = null,
)
