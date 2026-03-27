// SPDX-License-Identifier: EUPL-1.2
// Copyright (C) 2026 Gemeente Utrecht
package com.baseflow.services

import com.baseflow.api.middleware.AuditContext
import com.baseflow.api.models.EnkelvoudigInformatieObjectStatus
import com.baseflow.api.models.Vertrouwelijkheidaanduiding
import com.baseflow.config.ApplicationConfig
import com.baseflow.config.OpenZaakConfig
import com.baseflow.services.models.EIOOrdering
import com.baseflow.services.models.QueryEnkelvoudigeInformatieObjectenFilter
import com.baseflow.testutils.TestDataFactory.VALID_INFORMATIEOBJECTTYPE_URL
import com.baseflow.testutils.TestDataFactory.generateTestDocument
import com.baseflow.tooling.AllTables
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Tests for the EXPERIMENTEEL filters on QueryEnkelvoudigeInformatieObjectenFilter.
 * Each test creates specific documents and asserts that filtering produces the correct subset.
 */
class EnkelvoudigInformatieObjectFilterTest {
    private lateinit var service: EnkelvoudigInformatieObjectService

    @BeforeTest
    fun setup() {
        Database.connect(
            "jdbc:h2:mem:test_eio_filter;DB_CLOSE_DELAY=-1;",
            driver = "org.h2.Driver",
            user = "root",
            password = "",
        )
        transaction { AllTables.createMissing() }

        val mockStorageService = mockk<StorageService>()
        every { mockStorageService.uploadFile(any(), any()) } returns Unit
        val auditContext = AuditContext()
        service = EnkelvoudigInformatieObjectService(
            storageService = mockStorageService,
            ApplicationConfig,
            CatalogusService(OpenZaakConfig(validationEnabled = false)),
            AuditTrailService(auditContext),
            auditContext,
        )
    }

    @AfterTest
    fun teardown() {
        transaction { SchemaUtils.drop(*AllTables.tables.reversedArray()) }
    }

    // -------------------------------------------------------------------------
    // informatieobjecttype
    // -------------------------------------------------------------------------

    @Test
    fun `filter op informatieobjecttype geeft alleen overeenkomende documenten terug`() = runBlocking {
        val altType = "https://example.com/catalogi/api/v1/informatieobjecttypen/other-type"
        service.create(generateTestDocument(titel = "Match"))
        service.create(generateTestDocument(titel = "Geen match", informatieobjecttype = altType))

        val (results, count) = service.getAll(
            QueryEnkelvoudigeInformatieObjectenFilter(informatieobjecttype = VALID_INFORMATIEOBJECTTYPE_URL),
        )

        assertEquals(1L, count)
        assertEquals("Match", results.single().titel)
    }

    @Test
    fun `filter op onbekend informatieobjecttype geeft lege lijst terug`() = runBlocking {
        service.create(generateTestDocument())

        val (results, count) = service.getAll(
            QueryEnkelvoudigeInformatieObjectenFilter(informatieobjecttype = "https://example.com/onbekend"),
        )

        assertEquals(0L, count)
        assertTrue(results.isEmpty())
    }

    // -------------------------------------------------------------------------
    // vertrouwelijkheidaanduiding
    // -------------------------------------------------------------------------

    @Test
    fun `filter op vertrouwelijkheidaanduiding geeft alleen overeenkomende documenten terug`() = runBlocking {
        service.create(generateTestDocument(titel = "Openbaar").copy(vertrouwelijkheidaanduiding = Vertrouwelijkheidaanduiding.OPENBAAR))
        service.create(generateTestDocument(titel = "Geheim").copy(vertrouwelijkheidaanduiding = Vertrouwelijkheidaanduiding.GEHEIM))

        val (results, count) = service.getAll(
            QueryEnkelvoudigeInformatieObjectenFilter(vertrouwelijkheidaanduiding = listOf("openbaar")),
        )

        assertEquals(1L, count)
        assertEquals("Openbaar", results.single().titel)
    }

    @Test
    fun `filter op meerdere vertrouwelijkheidaanduiding waarden geeft alle overeenkomende documenten terug`() = runBlocking {
        service.create(
            generateTestDocument(titel = "Openbaar").copy(vertrouwelijkheidaanduiding = Vertrouwelijkheidaanduiding.OPENBAAR),
        )
        service.create(generateTestDocument(titel = "Intern").copy(vertrouwelijkheidaanduiding = Vertrouwelijkheidaanduiding.INTERN))
        service.create(generateTestDocument(titel = "Geheim").copy(vertrouwelijkheidaanduiding = Vertrouwelijkheidaanduiding.GEHEIM))

        val (results, count) = service.getAll(
            QueryEnkelvoudigeInformatieObjectenFilter(vertrouwelijkheidaanduiding = listOf("openbaar", "intern")),
        )

        assertEquals(2L, count)
        val titels = results.map { it.titel }.toSet()
        assertTrue("Openbaar" in titels)
        assertTrue("Intern" in titels)
    }

    @Test
    fun `filter op vertrouwelijkheidaanduiding is hoofdletterongevoelig`() = runBlocking {
        service.create(generateTestDocument(titel = "Openbaar").copy(vertrouwelijkheidaanduiding = Vertrouwelijkheidaanduiding.OPENBAAR))
        service.create(generateTestDocument(titel = "Geheim").copy(vertrouwelijkheidaanduiding = Vertrouwelijkheidaanduiding.GEHEIM))

        val (results, count) = service.getAll(
            QueryEnkelvoudigeInformatieObjectenFilter(vertrouwelijkheidaanduiding = listOf("OPENBAAR")),
        )

        assertEquals(1L, count)
        assertEquals("Openbaar", results.single().titel)
    }

    // -------------------------------------------------------------------------
    // titel
    // -------------------------------------------------------------------------

    @Test
    fun `filter op titel geeft documenten terug die de zoekterm bevatten`() = runBlocking {
        service.create(generateTestDocument(titel = "Jaarverslag 2025"))
        service.create(generateTestDocument(titel = "Kwartaalrapportage Q1"))
        service.create(generateTestDocument(titel = "Jaarplan 2026"))

        val (results, count) = service.getAll(
            QueryEnkelvoudigeInformatieObjectenFilter(titel = "jaar"),
        )

        assertEquals(2L, count)
        assertTrue(results.all { it.titel.lowercase().contains("jaar") })
    }

    @Test
    fun `filter op titel is hoofdletterongevoelig`() = runBlocking {
        service.create(generateTestDocument(titel = "Jaarverslag 2025"))
        service.create(generateTestDocument(titel = "Kwartaalrapportage"))

        val (results, count) = service.getAll(
            QueryEnkelvoudigeInformatieObjectenFilter(titel = "JAARVERSLAG"),
        )

        assertEquals(1L, count)
        assertEquals("Jaarverslag 2025", results.single().titel)
    }

    @Test
    fun `filter op titel zonder overeenkomst geeft lege lijst terug`() = runBlocking {
        service.create(generateTestDocument(titel = "Jaarverslag"))

        val (results, count) = service.getAll(
            QueryEnkelvoudigeInformatieObjectenFilter(titel = "kwartaal"),
        )

        assertEquals(0L, count)
        assertTrue(results.isEmpty())
    }

    // -------------------------------------------------------------------------
    // auteur
    // -------------------------------------------------------------------------

    @Test
    fun `filter op auteur geeft documenten terug die de zoekterm bevatten`() = runBlocking {
        service.create(generateTestDocument(auteur = "Jan de Vries"))
        service.create(generateTestDocument(auteur = "Pieter Jansen"))
        service.create(generateTestDocument(auteur = "Maria Janssen"))

        val (results, count) = service.getAll(
            QueryEnkelvoudigeInformatieObjectenFilter(auteur = "janssen"),
        )

        assertEquals(1L, count)
        assertTrue(results.all { it.auteur.lowercase().contains("janssen") })
    }

    @Test
    fun `filter op auteur is hoofdletterongevoelig`() = runBlocking {
        service.create(generateTestDocument(auteur = "Jan de Vries"))
        service.create(generateTestDocument(auteur = "Pieter Jansen"))

        val (results, count) = service.getAll(
            QueryEnkelvoudigeInformatieObjectenFilter(auteur = "JAN DE VRIES"),
        )

        assertEquals(1L, count)
        assertEquals("Jan de Vries", results.single().auteur)
    }

    // -------------------------------------------------------------------------
    // status
    // -------------------------------------------------------------------------

    @Test
    fun `filter op status geeft alleen documenten met exact overeenkomende status terug`() = runBlocking {
        service.create(
            generateTestDocument(titel = "Concept").copy(status = EnkelvoudigInformatieObjectStatus.CONCEPT),
        )
        service.create(
            generateTestDocument(titel = "Definitief").copy(status = EnkelvoudigInformatieObjectStatus.DEFINITIEF),
        )

        run {
            val (_, count) = service.getAll(
                QueryEnkelvoudigeInformatieObjectenFilter(status = "concept"),
            )
            assertEquals(0L, count)
        }

        run {
            val (results, count) = service.getAll(
                QueryEnkelvoudigeInformatieObjectenFilter(status = EnkelvoudigInformatieObjectStatus.CONCEPT.toString()),
            )
            assertEquals(1L, count)
            assertEquals("Concept", results.single().titel)
        }
    }

    @Test
    fun `filter op status zonder overeenkomst geeft lege lijst terug`() = runBlocking {
        service.create(generateTestDocument().copy(status = EnkelvoudigInformatieObjectStatus.DEFINITIEF))

        val (results, count) = service.getAll(
            QueryEnkelvoudigeInformatieObjectenFilter(status = "concept"),
        )

        assertEquals(0L, count)
        assertTrue(results.isEmpty())
    }

    // -------------------------------------------------------------------------
    // beschrijving
    // -------------------------------------------------------------------------

    @Test
    fun `filter op beschrijving geeft documenten terug die de zoekterm bevatten`() = runBlocking {
        service.create(generateTestDocument(titel = "A").copy(beschrijving = "Rapport over klimaat en milieu"))
        service.create(generateTestDocument(titel = "B").copy(beschrijving = "Financieel overzicht 2025"))
        service.create(generateTestDocument(titel = "C").copy(beschrijving = "Klimaatbeleid gemeente"))

        val (results, count) = service.getAll(
            QueryEnkelvoudigeInformatieObjectenFilter(beschrijving = "klimaat"),
        )

        assertEquals(2L, count)
        assertTrue(results.all { it.beschrijving.orEmpty().lowercase().contains("klimaat") })
    }

    @Test
    fun `filter op beschrijving is hoofdletterongevoelig`() = runBlocking {
        service.create(generateTestDocument(titel = "A").copy(beschrijving = "Rapport over Klimaat"))
        service.create(generateTestDocument(titel = "B").copy(beschrijving = "Financieel overzicht"))

        val (results, count) = service.getAll(
            QueryEnkelvoudigeInformatieObjectenFilter(beschrijving = "KLIMAAT"),
        )

        assertEquals(1L, count)
        assertEquals("A", results.single().titel)
    }

    // -------------------------------------------------------------------------
    // creatiedatum
    // -------------------------------------------------------------------------

    @Test
    fun `filter creatiedatum__lte geeft documenten op en voor de opgegeven datum terug`() = runBlocking {
        service.create(generateTestDocument(titel = "Op datum").copy(creatiedatum = LocalDate(2025, 1, 1)))
        service.create(generateTestDocument(titel = "Voor datum").copy(creatiedatum = LocalDate(2024, 12, 31)))
        service.create(generateTestDocument(titel = "Na datum").copy(creatiedatum = LocalDate(2025, 1, 2)))

        val (results, count) = service.getAll(
            QueryEnkelvoudigeInformatieObjectenFilter(creatiedatumLte = LocalDate(2025, 1, 1)),
        )

        assertEquals(2L, count)
        val titels = results.map { it.titel }.toSet()
        assertTrue("Op datum" in titels)
        assertTrue("Voor datum" in titels)
    }

    @Test
    fun `filter creatiedatum__gte geeft documenten op en na de opgegeven datum terug`() = runBlocking {
        service.create(generateTestDocument(titel = "Op datum").copy(creatiedatum = LocalDate(2025, 3, 1)))
        service.create(generateTestDocument(titel = "Na datum").copy(creatiedatum = LocalDate(2025, 3, 2)))
        service.create(generateTestDocument(titel = "Voor datum").copy(creatiedatum = LocalDate(2025, 2, 28)))

        val (results, count) = service.getAll(
            QueryEnkelvoudigeInformatieObjectenFilter(creatiedatumGte = LocalDate(2025, 3, 1)),
        )

        assertEquals(2L, count)
        val titels = results.map { it.titel }.toSet()
        assertTrue("Op datum" in titels)
        assertTrue("Na datum" in titels)
    }

    @Test
    fun `filter creatiedatum bereik geeft alleen documenten binnen het bereik terug`() = runBlocking {
        service.create(generateTestDocument(titel = "Te oud").copy(creatiedatum = LocalDate(2023, 12, 31)))
        service.create(generateTestDocument(titel = "In bereik").copy(creatiedatum = LocalDate(2024, 6, 15)))
        service.create(generateTestDocument(titel = "Te nieuw").copy(creatiedatum = LocalDate(2025, 1, 2)))

        val (results, count) = service.getAll(
            QueryEnkelvoudigeInformatieObjectenFilter(
                creatiedatumGte = LocalDate(2024, 1, 1),
                creatiedatumLte = LocalDate(2025, 1, 1),
            ),
        )

        assertEquals(1L, count)
        assertEquals("In bereik", results.single().titel)
    }

    // -------------------------------------------------------------------------
    // trefwoorden (array contains all — alle opgegeven trefwoorden moeten aanwezig zijn)
    // -------------------------------------------------------------------------

    @Test
    fun `filter op trefwoorden geeft documenten terug die alle opgegeven trefwoorden bevatten`() = runBlocking {
        service.create(generateTestDocument(titel = "A").copy(trefwoorden = listOf("klimaat", "milieu", "energie")))
        service.create(generateTestDocument(titel = "B").copy(trefwoorden = listOf("klimaat", "milieu")))
        service.create(generateTestDocument(titel = "C").copy(trefwoorden = listOf("energie")))

        val (results, count) = service.getAll(
            QueryEnkelvoudigeInformatieObjectenFilter(trefwoorden = listOf("klimaat", "milieu")),
        )

        assertEquals(2L, count)
        val titels = results.map { it.titel }.toSet()
        assertTrue("A" in titels)
        assertTrue("B" in titels)
    }

    @Test
    fun `filter op trefwoorden geeft geen resultaat als niet alle trefwoorden aanwezig zijn`() = runBlocking {
        service.create(generateTestDocument(titel = "A").copy(trefwoorden = listOf("klimaat")))

        val (results, count) = service.getAll(
            QueryEnkelvoudigeInformatieObjectenFilter(trefwoorden = listOf("klimaat", "milieu")),
        )

        assertEquals(0L, count)
        assertTrue(results.isEmpty())
    }

    @Test
    fun `filter op lege trefwoorden lijst geeft alle documenten terug`() = runBlocking {
        service.create(generateTestDocument(titel = "A").copy(trefwoorden = listOf("klimaat")))
        service.create(generateTestDocument(titel = "B").copy(trefwoorden = emptyList()))

        val (_, count) = service.getAll(
            QueryEnkelvoudigeInformatieObjectenFilter(trefwoorden = emptyList()),
        )

        assertEquals(2L, count)
    }

    // -------------------------------------------------------------------------
    // trefwoorden__overlap (array overlap — ten minste één trefwoord moet aanwezig zijn)
    // -------------------------------------------------------------------------

    @Test
    fun `filter op trefwoorden__overlap geeft documenten terug met ten minste één overeenkomend trefwoord`() = runBlocking {
        service.create(generateTestDocument(titel = "A").copy(trefwoorden = listOf("klimaat", "energie")))
        service.create(generateTestDocument(titel = "B").copy(trefwoorden = listOf("milieu")))
        service.create(generateTestDocument(titel = "C").copy(trefwoorden = listOf("financiën")))

        val (results, count) = service.getAll(
            QueryEnkelvoudigeInformatieObjectenFilter(trefwoordenOverlap = listOf("klimaat", "milieu")),
        )

        assertEquals(2L, count)
        val titels = results.map { it.titel }.toSet()
        assertTrue("A" in titels)
        assertTrue("B" in titels)
    }

    @Test
    fun `filter op trefwoorden__overlap geeft geen resultaat als geen enkel trefwoord overeenkomt`() = runBlocking {
        service.create(generateTestDocument(titel = "A").copy(trefwoorden = listOf("klimaat")))

        val (results, count) = service.getAll(
            QueryEnkelvoudigeInformatieObjectenFilter(trefwoordenOverlap = listOf("financiën")),
        )

        assertEquals(0L, count)
        assertTrue(results.isEmpty())
    }

    @Test
    fun `filter op lege trefwoorden__overlap lijst geeft alle documenten terug`() = runBlocking {
        service.create(generateTestDocument(titel = "A").copy(trefwoorden = listOf("klimaat")))
        service.create(generateTestDocument(titel = "B").copy(trefwoorden = emptyList()))

        val (_, count) = service.getAll(
            QueryEnkelvoudigeInformatieObjectenFilter(trefwoordenOverlap = emptyList()),
        )

        assertEquals(2L, count)
    }

    // -------------------------------------------------------------------------
    // registratiedatum
    // -------------------------------------------------------------------------

    @Test
    fun `filter registratiedatum__lte geeft alle documenten terug als alle voor de opgegeven datum zijn`() = runBlocking {
        service.create(generateTestDocument(titel = "A"))
        service.create(generateTestDocument(titel = "B"))
        val futureTime = LocalDateTime(2099, 1, 1, 0, 0, 0)

        val (results, count) = service.getAll(
            QueryEnkelvoudigeInformatieObjectenFilter(registratiedatumLte = futureTime),
        )

        assertEquals(2L, count)
        assertEquals(2, results.size)
    }

    @Test
    fun `filter registratiedatum__gte geeft geen documenten als alle documenten voor de opgegeven datum zijn`() = runBlocking {
        service.create(generateTestDocument(titel = "Oud"))
        val futureTime = LocalDateTime(2099, 1, 1, 0, 0, 0)

        val (results, count) = service.getAll(
            QueryEnkelvoudigeInformatieObjectenFilter(registratiedatumGte = futureTime),
        )

        assertEquals(0L, count)
        assertTrue(results.isEmpty())
    }

    // -------------------------------------------------------------------------
    // locked
    // -------------------------------------------------------------------------

    @Test
    fun `filter locked=true geeft alleen vergrendelde documenten terug`() = runBlocking {
        val locked = service.create(generateTestDocument(titel = "Vergrendeld"))
        service.lock(java.util.UUID.fromString(locked.id))
        service.create(generateTestDocument(titel = "Niet vergrendeld"))

        val (results, count) = service.getAll(
            QueryEnkelvoudigeInformatieObjectenFilter(locked = true),
        )

        assertEquals(1L, count)
        assertEquals("Vergrendeld", results.single().titel)
    }

    @Test
    fun `filter locked=false geeft alleen niet-vergrendelde documenten terug`() = runBlocking {
        val locked = service.create(generateTestDocument(titel = "Vergrendeld"))
        service.lock(java.util.UUID.fromString(locked.id))
        service.create(generateTestDocument(titel = "Niet vergrendeld"))

        val (results, count) = service.getAll(
            QueryEnkelvoudigeInformatieObjectenFilter(locked = false),
        )

        assertEquals(1L, count)
        assertEquals("Niet vergrendeld", results.single().titel)
    }

    @Test
    fun `filter zonder locked geeft alle documenten terug ongeacht vergrendeling`() = runBlocking {
        val locked = service.create(generateTestDocument(titel = "Vergrendeld"))
        service.lock(java.util.UUID.fromString(locked.id))
        service.create(generateTestDocument(titel = "Niet vergrendeld"))

        val (results, count) = service.getAll(
            QueryEnkelvoudigeInformatieObjectenFilter(locked = null),
        )

        assertEquals(2L, count)
        assertEquals(2, results.size)
    }

    // -------------------------------------------------------------------------
    // ordering
    // -------------------------------------------------------------------------

    @Test
    fun `ordering op titel ascending sorteert resultaten alfabetisch`() = runBlocking {
        service.create(generateTestDocument(titel = "Zebra"))
        service.create(generateTestDocument(titel = "Appel"))
        service.create(generateTestDocument(titel = "Mango"))

        val (results, _) = service.getAll(
            QueryEnkelvoudigeInformatieObjectenFilter(ordering = listOf(EIOOrdering.TITEL_ASC)),
        )

        assertEquals(listOf("Appel", "Mango", "Zebra"), results.map { it.titel })
    }

    @Test
    fun `ordering op titel descending sorteert resultaten omgekeerd alfabetisch`() = runBlocking {
        service.create(generateTestDocument(titel = "Zebra"))
        service.create(generateTestDocument(titel = "Appel"))
        service.create(generateTestDocument(titel = "Mango"))

        val (results, _) = service.getAll(
            QueryEnkelvoudigeInformatieObjectenFilter(ordering = listOf(EIOOrdering.TITEL_DESC)),
        )

        assertEquals(listOf("Zebra", "Mango", "Appel"), results.map { it.titel })
    }

    @Test
    fun `ordering op auteur ascending sorteert resultaten alfabetisch op auteur`() = runBlocking {
        service.create(generateTestDocument(auteur = "Piet"))
        service.create(generateTestDocument(auteur = "Anna"))
        service.create(generateTestDocument(auteur = "Karel"))

        val (results, _) = service.getAll(
            QueryEnkelvoudigeInformatieObjectenFilter(ordering = listOf(EIOOrdering.AUTEUR_ASC)),
        )

        assertEquals(listOf("Anna", "Karel", "Piet"), results.map { it.auteur })
    }

    @Test
    fun `ordering op auteur descending sorteert resultaten omgekeerd alfabetisch op auteur`() = runBlocking {
        service.create(generateTestDocument(auteur = "Piet"))
        service.create(generateTestDocument(auteur = "Anna"))
        service.create(generateTestDocument(auteur = "Karel"))

        val (results, _) = service.getAll(
            QueryEnkelvoudigeInformatieObjectenFilter(ordering = listOf(EIOOrdering.AUTEUR_DESC)),
        )

        assertEquals(listOf("Piet", "Karel", "Anna"), results.map { it.auteur })
    }

    @Test
    fun `ordering op creatiedatum ascending sorteert resultaten van oud naar nieuw`() = runBlocking {
        service.create(generateTestDocument(titel = "Nieuw").copy(creatiedatum = LocalDate(2025, 6, 1)))
        service.create(generateTestDocument(titel = "Oud").copy(creatiedatum = LocalDate(2023, 1, 1)))
        service.create(generateTestDocument(titel = "Midden").copy(creatiedatum = LocalDate(2024, 3, 15)))

        val (results, _) = service.getAll(
            QueryEnkelvoudigeInformatieObjectenFilter(ordering = listOf(EIOOrdering.CREATIEDATUM_ASC)),
        )

        assertEquals(listOf("Oud", "Midden", "Nieuw"), results.map { it.titel })
    }

    @Test
    fun `ordering op creatiedatum descending sorteert resultaten van nieuw naar oud`() = runBlocking {
        service.create(generateTestDocument(titel = "Nieuw").copy(creatiedatum = LocalDate(2025, 6, 1)))
        service.create(generateTestDocument(titel = "Oud").copy(creatiedatum = LocalDate(2023, 1, 1)))
        service.create(generateTestDocument(titel = "Midden").copy(creatiedatum = LocalDate(2024, 3, 15)))

        val (results, _) = service.getAll(
            QueryEnkelvoudigeInformatieObjectenFilter(ordering = listOf(EIOOrdering.CREATIEDATUM_DESC)),
        )

        assertEquals(listOf("Nieuw", "Midden", "Oud"), results.map { it.titel })
    }

    @Test
    fun `ordering op bestandsomvang ascending sorteert resultaten van klein naar groot`() = runBlocking {
        service.create(generateTestDocument(titel = "Groot").copy(bestandsomvang = 3000L))
        service.create(generateTestDocument(titel = "Klein").copy(bestandsomvang = 100L))
        service.create(generateTestDocument(titel = "Midden").copy(bestandsomvang = 1500L))

        val (results, _) = service.getAll(
            QueryEnkelvoudigeInformatieObjectenFilter(ordering = listOf(EIOOrdering.BESTANDSOMVANG_ASC)),
        )

        assertEquals(listOf("Klein", "Midden", "Groot"), results.map { it.titel })
    }

    @Test
    fun `ordering op bestandsomvang descending sorteert resultaten van groot naar klein`() = runBlocking {
        service.create(generateTestDocument(titel = "Groot").copy(bestandsomvang = 3000L))
        service.create(generateTestDocument(titel = "Klein").copy(bestandsomvang = 100L))
        service.create(generateTestDocument(titel = "Midden").copy(bestandsomvang = 1500L))

        val (results, _) = service.getAll(
            QueryEnkelvoudigeInformatieObjectenFilter(ordering = listOf(EIOOrdering.BESTANDSOMVANG_DESC)),
        )

        assertEquals(listOf("Groot", "Midden", "Klein"), results.map { it.titel })
    }

    @Test
    fun `ordering op status ascending sorteert resultaten alfabetisch op status`() = runBlocking {
        service.create(
            generateTestDocument(titel = "TerVaststelling").copy(status = EnkelvoudigInformatieObjectStatus.TER_VASTSTELLING),
        )
        service.create(
            generateTestDocument(titel = "Concept").copy(status = EnkelvoudigInformatieObjectStatus.CONCEPT),
        )
        service.create(
            generateTestDocument(titel = "Definitief").copy(status = EnkelvoudigInformatieObjectStatus.DEFINITIEF),
        )

        val (results, _) = service.getAll(
            QueryEnkelvoudigeInformatieObjectenFilter(ordering = listOf(EIOOrdering.STATUS_ASC)),
        )

        // concept < definitief < ter_vaststelling alphabetically
        assertEquals("Concept", results.first().titel)
        assertEquals("TerVaststelling", results.last().titel)
    }

    @Test
    fun `ordering op vertrouwelijkheidaanduiding ascending sorteert resultaten alfabetisch`() = runBlocking {
        service.create(generateTestDocument(titel = "Geheim").copy(vertrouwelijkheidaanduiding = Vertrouwelijkheidaanduiding.GEHEIM))
        service.create(generateTestDocument(titel = "Openbaar").copy(vertrouwelijkheidaanduiding = Vertrouwelijkheidaanduiding.OPENBAAR))
        service.create(generateTestDocument(titel = "Intern").copy(vertrouwelijkheidaanduiding = Vertrouwelijkheidaanduiding.INTERN))

        val (results, _) = service.getAll(
            QueryEnkelvoudigeInformatieObjectenFilter(ordering = listOf(EIOOrdering.VERTROUWELIJKHEIDAANDUIDING_ASC)),
        )

        // geheim < intern < openbaar alphabetically
        assertEquals("Geheim", results.first().titel)
        assertEquals("Openbaar", results.last().titel)
    }

    @Test
    fun `geen ordering geeft alle documenten terug`() = runBlocking {
        service.create(generateTestDocument(titel = "A"))
        service.create(generateTestDocument(titel = "B"))
        service.create(generateTestDocument(titel = "C"))

        val (results, count) = service.getAll(
            QueryEnkelvoudigeInformatieObjectenFilter(ordering = emptyList()),
        )

        assertEquals(3L, count)
        assertEquals(3, results.size)
    }

    // -------------------------------------------------------------------------
    // EIOOrdering enum
    // -------------------------------------------------------------------------

    @Test
    fun `EIOOrdering fromValue geeft het juiste enum terug voor geldige waarden`() {
        assertEquals(EIOOrdering.AUTEUR_ASC, EIOOrdering.fromValue("auteur"))
        assertEquals(EIOOrdering.AUTEUR_DESC, EIOOrdering.fromValue("-auteur"))
        assertEquals(EIOOrdering.BESTANDSOMVANG_ASC, EIOOrdering.fromValue("bestandsomvang"))
        assertEquals(EIOOrdering.BESTANDSOMVANG_DESC, EIOOrdering.fromValue("-bestandsomvang"))
        assertEquals(EIOOrdering.CREATIEDATUM_ASC, EIOOrdering.fromValue("creatiedatum"))
        assertEquals(EIOOrdering.CREATIEDATUM_DESC, EIOOrdering.fromValue("-creatiedatum"))
        assertEquals(EIOOrdering.FORMAAT_ASC, EIOOrdering.fromValue("formaat"))
        assertEquals(EIOOrdering.FORMAAT_DESC, EIOOrdering.fromValue("-formaat"))
        assertEquals(EIOOrdering.STATUS_ASC, EIOOrdering.fromValue("status"))
        assertEquals(EIOOrdering.STATUS_DESC, EIOOrdering.fromValue("-status"))
        assertEquals(EIOOrdering.TITEL_ASC, EIOOrdering.fromValue("titel"))
        assertEquals(EIOOrdering.TITEL_DESC, EIOOrdering.fromValue("-titel"))
        assertEquals(EIOOrdering.VERTROUWELIJKHEIDAANDUIDING_ASC, EIOOrdering.fromValue("vertrouwelijkheidaanduiding"))
        assertEquals(
            EIOOrdering.VERTROUWELIJKHEIDAANDUIDING_DESC,
            EIOOrdering.fromValue("-vertrouwelijkheidaanduiding"),
        )
    }

    @Test
    fun `EIOOrdering fromValue geeft null terug voor ongeldige waarden`() {
        assertEquals(null, EIOOrdering.fromValue("onbekend"))
        assertEquals(null, EIOOrdering.fromValue(""))
        assertEquals(null, EIOOrdering.fromValue("-onbekend"))
        assertEquals(null, EIOOrdering.fromValue("Auteur")) // case-sensitive
    }

    // -------------------------------------------------------------------------
    // Gecombineerde filters
    // -------------------------------------------------------------------------

    @Test
    fun `combinatie van titel en status filters beperkt resultaten correct`() = runBlocking {
        service.create(generateTestDocument(titel = "Jaarverslag").copy(status = EnkelvoudigInformatieObjectStatus.DEFINITIEF))
        service.create(generateTestDocument(titel = "Jaarverslag concept").copy(status = EnkelvoudigInformatieObjectStatus.CONCEPT))
        service.create(generateTestDocument(titel = "Kwartaalrapportage").copy(status = EnkelvoudigInformatieObjectStatus.DEFINITIEF))

        val (results, count) = service.getAll(
            QueryEnkelvoudigeInformatieObjectenFilter(
                titel = "jaarverslag",
                status = EnkelvoudigInformatieObjectStatus.DEFINITIEF.toString(),
            ),
        )

        assertEquals(1L, count)
        assertEquals("Jaarverslag", results.single().titel)
    }

    @Test
    fun `combinatie van auteur en creatiedatum filters beperkt resultaten correct`() = runBlocking {
        service.create(generateTestDocument(auteur = "Jan").copy(creatiedatum = LocalDate(2024, 1, 1)))
        service.create(generateTestDocument(auteur = "Jan").copy(creatiedatum = LocalDate(2025, 6, 1)))
        service.create(generateTestDocument(auteur = "Piet").copy(creatiedatum = LocalDate(2024, 1, 1)))

        val (results, count) = service.getAll(
            QueryEnkelvoudigeInformatieObjectenFilter(
                auteur = "jan",
                creatiedatumLte = LocalDate(2024, 12, 31),
            ),
        )

        assertEquals(1L, count)
        assertEquals("Jan", results.single().auteur)
        assertEquals(LocalDate(2024, 1, 1), results.single().creatiedatum)
    }

    @Test
    fun `filter en ordering samen filteren en sorteren resultaten correct`() = runBlocking {
        service.create(generateTestDocument(titel = "Zebra").copy(status = EnkelvoudigInformatieObjectStatus.DEFINITIEF))
        service.create(generateTestDocument(titel = "Appel").copy(status = EnkelvoudigInformatieObjectStatus.DEFINITIEF))
        service.create(generateTestDocument(titel = "Mango").copy(status = EnkelvoudigInformatieObjectStatus.CONCEPT))

        val (results, count) = service.getAll(
            QueryEnkelvoudigeInformatieObjectenFilter(
                status = "DEFINITIEF",
                ordering = listOf(EIOOrdering.TITEL_ASC),
            ),
        )

        assertEquals(2L, count)
        assertEquals(listOf("Appel", "Zebra"), results.map { it.titel })
    }

    @Test
    fun `filter op vertrouwelijkheidaanduiding met ordering sorteert gefilterde resultaten`() = runBlocking {
        service.create(generateTestDocument(titel = "C").copy(vertrouwelijkheidaanduiding = Vertrouwelijkheidaanduiding.INTERN))
        service.create(generateTestDocument(titel = "A").copy(vertrouwelijkheidaanduiding = Vertrouwelijkheidaanduiding.INTERN))
        service.create(generateTestDocument(titel = "B").copy(vertrouwelijkheidaanduiding = Vertrouwelijkheidaanduiding.GEHEIM))

        val (results, count) = service.getAll(
            QueryEnkelvoudigeInformatieObjectenFilter(
                vertrouwelijkheidaanduiding = listOf("intern"),
                ordering = listOf(EIOOrdering.TITEL_ASC),
            ),
        )

        assertEquals(2L, count)
        assertEquals(listOf("A", "C"), results.map { it.titel })
    }
}

