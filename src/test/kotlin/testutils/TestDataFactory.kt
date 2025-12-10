// SPDX-License-Identifier: EUPL-1.2
// Copyright (C) 2025 Gemeente Utrecht
package com.baseflow.testutils

import com.baseflow.api.models.CreateEIORequest
import kotlinx.datetime.LocalDate

/**
 * Centralized factory for creating common test data objects.
 */
object TestDataFactory {
    /**
     * Generates a minimal valid CreateEIORequest for tests.
     * Optional fields are omitted by default; override via parameters as needed.
     */
    fun generateTestDocument(
        taal: String = "dut",
        bestandsnaam: String = "test.pdf",
        titel: String = "test",
        auteur: String = "auteur"
    ): CreateEIORequest = CreateEIORequest(
//        creatiedatum = LocalDate(2025, 1, 1),
//        bronorganisatie = "012345678",
        taal = taal,
        bestandsnaam = bestandsnaam,
        titel = titel,
        auteur = auteur,
//        informatieobjecttype = "https://IOT.test"
    )
}
