// SPDX-License-Identifier: EUPL-1.2
// Copyright (C) 2026 Gemeente Utrecht

package com.baseflow.api.models

import com.baseflow.entities.Wijzigingen
import kotlinx.datetime.LocalDateTime
import kotlinx.serialization.Serializable

@Serializable
data class AuditTrailResponse(
    val uuid: String,
    val bron: String?,
    val applicatieId: String?,
    val applicatieWeergave: String?,
    val gebruikersId: String?,
    val gebruikersWeergave: String?,
    val actie: String?,
    val actieWeergave: String?,
    val resultaat: Int?,
    val hoofdObject: String?,
    val resource: String?,
    val resourceUrl: String?,
    val resourceWeergave: String?,
    val toelichting: String?,
    val wijzigingen: Wijzigingen,
    val aanmaakdatum: LocalDateTime?
) : ApiResponse