// SPDX-License-Identifier: EUPL-1.2
// Copyright (C) 2025 Gemeente Utrecht
package com.baseflow.testutils

import com.baseflow.api.middleware.AuditContext
import com.baseflow.api.models.EnkelvoudigInformatieObjectRequest
import com.baseflow.api.models.EnkelvoudigInformatieObjectStatus
import com.baseflow.api.models.Integriteit
import com.baseflow.api.models.IntegriteitAlgoritme
import com.baseflow.api.models.Vertrouwelijkheidaanduiding
import io.ktor.server.application.*
import io.mockk.mockk
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
    const val VALID_INFORMATIEOBJECTTYPE_URL = "https://openzaak.dev.baseflow.com/catalogi/api/v1/informatieobjecttypen/1c8beee4-5b1b-4c7b-934c-925a3babd29d"
    const val PDF_CONTENT = "JVBERi0xLjQKMSAwIG9iago8PC9UeXBlIC9DYXRhbG9nCi9QYWdlcyAyIDAgUgo+PgplbmRvYmoKMiAwIG9iago8PC9UeXBlIC9QYWdlcwovS2lkcyBbMyAwIFJdCi9Db3VudCAxCj4+CmVuZG9iagozIDAgb2JqCjw8L1R5cGUgL1BhZ2UKL1BhcmVudCAyIDAgUgovTWVkaWFCb3ggWzAgMCA1OTUgODQyXQovQ29udGVudHMgNSAwIFIKL1Jlc291cmNlcyA8PC9Qcm9jU2V0IFsvUERGIC9UZXh0XQovRm9udCA8PC9GMSA0IDAgUj4+Cj4+Cj4+CmVuZG9iago0IDAgb2JqCjw8L1R5cGUgL0ZvbnQKL1N1YnR5cGUgL1R5cGUxCi9OYW1lIC9GMQovQmFzZUZvbnQgL0hlbHZldGljYQovRW5jb2RpbmcgL01hY1JvbWFuRW5jb2RpbmcKPj4KZW5kb2JqCjUgMCBvYmoKPDwvTGVuZ3RoIDUzCj4+CnN0cmVhbQpCVAovRjEgMjAgVGYKMjIwIDQwMCBUZAooRHVtbXkgUERGKSBUagpFVAplbmRzdHJlYW0KZW5kb2JqCnhyZWYKMCA2CjAwMDAwMDAwMDAgNjU1MzUgZgowMDAwMDAwMDA5IDAwMDAwIG4KMDAwMDAwMDA2MyAwMDAwMCBuCjAwMDAwMDAxMjQgMDAwMDAgbgowMDAwMDAwMjc3IDAwMDAwIG4KMDAwMDAwMDM5MiAwMDAwMCBuCnRyYWlsZXIKPDwvU2l6ZSA2Ci9Sb290IDEgMCBSCj4+CnN0YXJ0eHJlZgo0OTUKJSVFT0YK"
    const val PDF_CONTENT_ALT = "JVBERi0xLjEKJcKlwrHDqwoKMSAwIG9iagogIDw8IC9UeXBlIC9DYXRhbG9nCiAgICAgL1BhZ2VzIDIgMCBSCiAgPj4KZW5kb2JqCgoyIDAgb2JqCiAgPDwgL1R5cGUgL1BhZ2VzCiAgICAgL0tpZHMgWzMgMCBSXQogICAgIC9Db3VudCAxCiAgICAgL01lZGlhQm94IFswIDAgMzAwIDE0NF0KICA+PgplbmRvYmoKCjMgMCBvYmoKICA8PCAgL1R5cGUgL1BhZ2UKICAgICAgL1BhcmVudCAyIDAgUgogICAgICAvUmVzb3VyY2VzCiAgICAgICA8PCAvRm9udAogICAgICAgICAgIDw8IC9GMQogICAgICAgICAgICAgICA8PCAvVHlwZSAvRm9udAogICAgICAgICAgICAgICAgICAvU3VidHlwZSAvVHlwZTEKICAgICAgICAgICAgICAgICAgL0Jhc2VGb250IC9UaW1lcy1Sb21hbgogICAgICAgICAgICAgICA+PgogICAgICAgICAgID4+CiAgICAgICA+PgogICAgICAvQ29udGVudHMgNCAwIFIKICA+PgplbmRvYmoKCjQgMCBvYmoKICA8PCAvTGVuZ3RoIDU1ID4+CnN0cmVhbQogIEJUCiAgICAvRjEgMTggVGYKICAgIDAgMCBUZAogICAgKEhlbGxvIFdvcmxkKSBUagogIEVUCmVuZHN0cmVhbQplbmRvYmoKCnhyZWYKMCA1CjAwMDAwMDAwMDAgNjU1MzUgZiAKMDAwMDAwMDAxOCAwMDAwMCBuIAowMDAwMDAwMDc3IDAwMDAwIG4gCjAwMDAwMDAxNzggMDAwMDAgbiAKMDAwMDAwMDQ1NyAwMDAwMCBuIAp0cmFpbGVyCiAgPDwgIC9Sb290IDEgMCBSCiAgICAgIC9TaXplIDUKICA+PgpzdGFydHhyZWYKNTY1CiUlRU9GCg=="

    /**
     * Generates a minimal valid CreateEIORequest for tests.
     * Optional fields are omitted by default; override via parameters as needed.
     */
    fun generateTestDocument(
        taal: String = "dut",
        bestandsnaam: String = "test.pdf",
        titel: String = "test",
        auteur: String = "auteur",
        informatieobjecttype: String = VALID_INFORMATIEOBJECTTYPE_URL,
        identificatie: String? = null
    ): EnkelvoudigInformatieObjectRequest = EnkelvoudigInformatieObjectRequest(
        identificatie = identificatie,
        creatiedatum = LocalDate(2025, 1, 1),
        bronorganisatie = "012345678",
        taal = taal,
        bestandsnaam = bestandsnaam,
        titel = titel,
        auteur = auteur,
        status = EnkelvoudigInformatieObjectStatus.CONCEPT,
        informatieobjecttype = informatieobjecttype,
        formaat = "application/pdf",
        bestandsomvang = 123456789L,
        link = "https://example.com/test.pdf",
        integriteit = Integriteit(
            algoritme = IntegriteitAlgoritme.SHA_256,
            waarde = "sha256:abcdef1234567890",
            datum = LocalDate(2025, 1, 1),
        ),
        trefwoorden = listOf("test", "example"),
        vertrouwelijkheidaanduiding = Vertrouwelijkheidaanduiding.OPENBAAR,
        beschrijving = "Test beschrijving",
        indicatieGebruiksrecht = true,
    )

    /**
     * Creates a mock AuditContext for testing purposes
     */
    fun createMockAuditContext(): AuditContext {
        val mockCall = mockk<ApplicationCall>(relaxed = true)
        return AuditContext(mockCall)
    }
}
