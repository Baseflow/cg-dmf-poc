// SPDX-License-Identifier: EUPL-1.2
// Copyright (C) 2025 Gemeente Utrecht
package com.baseflow.testutils

import com.baseflow.api.models.CreateEIORequest
import com.baseflow.api.models.EnkelvoudigInformatieObjectStatus
import kotlinx.datetime.LocalDate

/**
 * Centralized factory for creating common test data objects.
 */
object TestDataFactory {

    /**
     * The full URL of a valid informatieobjecttype that exists in the OpenZaak test instance.
     * Source: https://openzaak.dev.baseflow.com/catalogi/api/v1/informatieobjecttypen/
     * The informatieobjecttype should be provided as a complete URL to the informatieobjecttype resource.
     */
    private const val VALID_INFORMATIEOBJECTTYPE_URL = "https://openzaak.dev.baseflow.com/catalogi/api/v1/informatieobjecttypen/1c8beee4-5b1b-4c7b-934c-925a3babd29d"

    /**
     * Generates a minimal valid CreateEIORequest for tests.
     * Optional fields are omitted by default; override via parameters as needed.
     */
    fun generateTestDocument(
        taal: String = "dut",
        bestandsnaam: String = "test.pdf",
        titel: String = "test",
        auteur: String = "auteur",
        informatieobjecttype: String = VALID_INFORMATIEOBJECTTYPE_URL
    ): CreateEIORequest = CreateEIORequest(
        creatiedatum = LocalDate(2025, 1, 1),
        bronorganisatie = "012345678",
        taal = taal,
        bestandsnaam = bestandsnaam,
        titel = titel,
        auteur = auteur,
        status = EnkelvoudigInformatieObjectStatus.CONCEPT,
        informatieobjecttype = informatieobjecttype
    )
}
