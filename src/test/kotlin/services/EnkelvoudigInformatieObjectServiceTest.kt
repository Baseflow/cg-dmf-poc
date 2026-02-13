// SPDX-License-Identifier: EUPL-1.2
// Copyright (C) 2025-2026 Gemeente Utrecht
@file:Suppress("UnusedDataClassCopyResult")

package com.baseflow.services

import com.baseflow.api.middleware.AuditContext
import com.baseflow.entities.EIORecordEntity
import com.baseflow.api.models.EnkelvoudigInformatieObjectRequest
import com.baseflow.api.models.EnkelvoudigInformatieObjectStatus
import com.baseflow.api.models.Vertrouwelijkheidaanduiding
import com.baseflow.api.models.Ondertekening
import com.baseflow.api.models.OndertekeningSoort
import com.baseflow.config.ApplicationConfig
import com.baseflow.config.OpenZaakConfig
import com.baseflow.testutils.TestDataFactory.generateTestDocument
import com.baseflow.services.models.DeleteResult
import com.baseflow.services.models.LockResult
import com.baseflow.services.models.UnlockResult
import com.baseflow.testutils.TestDataFactory
import com.baseflow.testutils.TestDataFactory.createMockAuditContext
import com.baseflow.testutils.TestDataFactory.PDF_CONTENT
import com.baseflow.testutils.TestDataFactory.PDF_CONTENT_ALT
import com.baseflow.tooling.AllTables
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import io.mockk.*
import kotlin.test.*
import java.util.UUID
import kotlin.io.encoding.Base64

class EnkelvoudigInformatieObjectServiceTest {
    private lateinit var service: EnkelvoudigInformatieObjectService

    @BeforeTest
    fun setup() {
        Database.connect(
            "jdbc:h2:mem:test_eio;DB_CLOSE_DELAY=-1;",
            driver = "org.h2.Driver",
            user = "root",
            password = ""
        )
        transaction {
            // Create all tables
            AllTables.createMissing()
        }
        val openZaakConfig = OpenZaakConfig(validationEnabled = false)
        val mockStorageService = mockk<StorageService>()
        every { mockStorageService.uploadFile(any(), any()) } returns Unit
        service = EnkelvoudigInformatieObjectService(
            storageService = mockStorageService,
            ApplicationConfig,
            OpenZaakService(openZaakConfig),
            AuditTrailService(),
            createMockAuditContext()
        )
    }

    @AfterTest
    fun teardown() {
        transaction {
            // Drop in reverse order of dependencies
            SchemaUtils.drop(*AllTables.tables.reversedArray())
        }
    }

    @Test
    fun `create should persist and return correct data`() = runBlocking {
        val req = generateTestDocument(taal = "dut", bestandsnaam = "doc.pdf").copy(
            vertrouwelijkheidaanduiding = Vertrouwelijkheidaanduiding.INTERN
        )
        val resp = service.create(req)
        assertEquals("dut", resp.taal)
        assertEquals("doc.pdf", resp.bestandsnaam)
        assertEquals(req.informatieobjecttype, resp.informatieobjecttype)
        assertEquals(Vertrouwelijkheidaanduiding.INTERN, resp.vertrouwelijkheidaanduiding)
        assertEquals(1, resp.versie)
        assertTrue(resp.id.isNotEmpty())
    }

    @Test
    fun `getById should return created object`() = runBlocking {
        val req = generateTestDocument(taal = "dut", bestandsnaam = "doc.pdf")
        val created = service.create(req)
        val found = service.getById(UUID.fromString(created.id))
        assertNotNull(found)
        assertEquals(created.id, found.id)
        assertEquals("dut", found.taal)
        assertEquals("doc.pdf", found.bestandsnaam)
        assertEquals(1, found.versie)
    }

    @Test
    fun `update should increment version and persist new data`() = runBlocking {
        val req = generateTestDocument()
        val created = service.create(req)
        val updateReq = generateTestDocument(taal = "eng", bestandsnaam = "doc2.pdf", informatieobjecttype = "https://example.com/api/v1/informatieobjecttypen/new-type")
        val updated = service.update(UUID.fromString(created.id), updateReq)
        assertNotNull(updated)
        assertEquals(created.id, updated.id)
        assertEquals("eng", updated.taal)
        assertEquals("doc2.pdf", updated.bestandsnaam)
        assertEquals(updateReq.informatieobjecttype, updated.informatieobjecttype)
        assertEquals(2, updated.versie)
    }

    @Test
    fun `getById should return null for unknown id`() {
        val found = service.getById(UUID.randomUUID())
        assertNull(found)
    }

    @Test
    fun `lock should set lock token and persist in DB`() = runBlocking {
        val created = service.create(generateTestDocument())
        val id = UUID.fromString(created.id)

        val result = service.lock(id)
        assertNotNull(result)
        assertTrue(result is LockResult.Success)
        val token = result.payload.lock
        assertTrue(token.isNotBlank())

        transaction {
            val rec = EIORecordEntity.findById(id)
            assertNotNull(rec)
            assertEquals(token, rec.lockToken)
        }
    }

    @Test
    fun `lock should return AlreadyLocked when already locked`() = runBlocking {
        val created = service.create(generateTestDocument())
        val id = UUID.fromString(created.id)

        val first = service.lock(id)
        assertTrue(first is LockResult.Success)

        val second = service.lock(id)
        assertTrue(second is LockResult.AlreadyLocked)
    }

    @Test
    fun `unlock with correct token should clear lock`() = runBlocking {
        val created = service.create(generateTestDocument())
        val id = UUID.fromString(created.id)
        val lockRes = service.lock(id) as LockResult.Success
        val token = lockRes.payload.lock

        val unlockRes = service.unlock(id, token)
        assertTrue(unlockRes is UnlockResult.Success)

        transaction {
            val rec = EIORecordEntity.findById(id)
            assertNotNull(rec)
            assertNull(rec.lockToken)
        }
    }

    @Test
    fun `unlock when not locked should return NotLocked`() = runBlocking {
        val created = service.create(generateTestDocument())
        val id = UUID.fromString(created.id)

        val res = service.unlock(id, "some-token")
        assertTrue(res is UnlockResult.NotLocked)
    }

    @Test
    fun `unlock with invalid token should return InvalidLock and keep lock`() = runBlocking {
        val created = service.create(generateTestDocument())
        val id = UUID.fromString(created.id)
        val lockRes = service.lock(id) as LockResult.Success
        val token = lockRes.payload.lock

        val res = service.unlock(id, "$token-wrong")
        assertTrue(res is UnlockResult.InvalidLock)

        transaction {
            val rec = EIORecordEntity.findById(id)
            assertNotNull(rec)
            assertEquals(token, rec.lockToken)
        }
    }

    @Test
    fun `exists should return true for existing id and false for random id`() = runBlocking {
        val created = service.create(generateTestDocument())
        val id = UUID.fromString(created.id)
        assertTrue(service.exists(id))
        assertFalse(service.exists(UUID.randomUUID()))
    }

    @Test
    fun `delete should return NotFound for unknown id`() {
        val res = service.delete(UUID.randomUUID())
        assertTrue(res is DeleteResult.NotFound)
    }

    @Test
    fun `delete should return Locked when record has lockToken`() = runBlocking {
        val created = service.create(generateTestDocument())
        val id = UUID.fromString(created.id)
        // lock it
        service.lock(id)
        val res = service.delete(id)
        assertTrue(res is DeleteResult.Locked)
    }

    @Test
    fun `delete should return Success when record exists and is not locked`() = runBlocking {
        val created = service.create(generateTestDocument())
        val id = UUID.fromString(created.id)
        val res = service.delete(id)
        assertTrue(res is DeleteResult.Success)
        // and now it should not exist
        assertFalse(service.exists(id))
    }

    @Test
    fun `create should derive bestandsomvang from content if not provided`() = runBlocking {
        val content = "Hello World"
        val base64Content = java.util.Base64.getEncoder().encodeToString(content.toByteArray())
        val req = generateTestDocument().copy(
            inhoud = base64Content,
            bestandsomvang = null,
            formaat = "text/plain"
        )
        val resp = service.create(req)
        assertEquals(content.length.toLong(), resp.bestandsomvang)
    }

    @Test
    fun `create should use provided bestandsomvang even if content is present`() = runBlocking {
        val content = "Hello World"
        val base64Content = java.util.Base64.getEncoder().encodeToString(content.toByteArray())
        val req = generateTestDocument().copy(
            inhoud = base64Content,
            bestandsomvang = 100L, // Intentionally different from content size
            formaat = "text/plain"
        )
        val resp = service.create(req)
        assertEquals(100L, resp.bestandsomvang)
    }

    @Test
    fun `create should null bestandsomvang if not provided and no content`() = runBlocking {
        val req = generateTestDocument().copy(
            inhoud = null,
            bestandsomvang = null,
            link = "https://example.com/file"
        )
        val resp = service.create(req)
        assertEquals(null, resp.bestandsomvang)
    }

    @Test
    fun `create should inherit vertrouwelijkheidaanduiding from informatieobjecttype if not provided`() = runBlocking {
        // We need a custom service with a mocked OpenZaakService for this
        val mockOpenZaakService = mockk<OpenZaakService>()
        coEvery { mockOpenZaakService.validateInformatieobjecttype(any()) } returns InformatieObjectType(
            url = "https://example.com/api/v1/informatieobjecttypen/1",
            omschrijving = "Mock Type",
            vertrouwelijkheidaanduiding = "geheim"
        )

        val customService = EnkelvoudigInformatieObjectService(
            StorageService(),
            ApplicationConfig,
            mockOpenZaakService,
            AuditTrailService(),
            createMockAuditContext()
        )

        val req = generateTestDocument().copy(vertrouwelijkheidaanduiding = null)
        val resp = customService.create(req)

        assertEquals(Vertrouwelijkheidaanduiding.GEHEIM, resp.vertrouwelijkheidaanduiding)
        coVerify { mockOpenZaakService.validateInformatieobjecttype(any()) }
    }

    @Test
    fun `create should fail when informatieobjecttype exceeds 200 characters`() = runBlocking {
        val longUrl = "https://example.com/" + "a".repeat(181) // 20 + 181 = 201 chars

        val exception = assertFailsWith<IllegalArgumentException> {
            generateTestDocument(informatieobjecttype = longUrl)
        }
        assertEquals("Informatieobjecttype mag maximaal 200 karakters lang zijn", exception.message)
    }


    @Test
    fun `patch should update only provided properties`() = runBlocking {
        val req = generateTestDocument(taal = "dut", bestandsnaam = "doc.pdf")
        val resp = service.create(req)
        assertEquals("dut", resp.taal)
        assertEquals("doc.pdf", resp.bestandsnaam)
        assertEquals(req.informatieobjecttype, resp.informatieobjecttype)
        assertEquals(1, resp.versie)
        assertTrue(resp.id.isNotEmpty())

        val uuid = UUID.fromString(resp.id)
        val patchReq = EnkelvoudigInformatieObjectRequest(
            taal = "eng",
            bestandsnaam = "doc2.pdf",
            bronorganisatie = "012345678",
            informatieobjecttype = TestDataFactory.VALID_INFORMATIEOBJECTTYPE_URL,
            creatiedatum = resp.creatiedatum,
            titel = resp.titel,
            auteur = resp.auteur,
        )

        val patchedResp = service.update(uuid, patchReq, true)
        assertNotNull(patchedResp)
        assertEquals("eng", patchedResp.taal)
        assertEquals("doc2.pdf", patchedResp.bestandsnaam)
        assertEquals(req.informatieobjecttype, patchedResp.informatieobjecttype)
        assertEquals(req.bronorganisatie, patchedResp.bronorganisatie)
        assertEquals(req.creatiedatum, patchedResp.creatiedatum)
        assertEquals(req.titel, patchedResp.titel)
        assertEquals(req.auteur, patchedResp.auteur)
        assertEquals(2, patchedResp.versie)
        assertTrue(patchedResp.id.isNotEmpty())
        // Version was changed though
        assertNotEquals(resp.inhoud, patchedResp.inhoud)
        // check remaining properties equal to non-patched response
        assertEquals(resp.id, patchedResp.id)
        assertEquals(resp.formaat, patchedResp.formaat)
        assertEquals(resp.integriteit, patchedResp.integriteit)
        assertEquals(resp.status, patchedResp.status)
        assertEquals(resp.ondertekening, patchedResp.ondertekening)
        assertEquals(resp.indicatieGebruiksrecht, patchedResp.indicatieGebruiksrecht)
        assertEquals(resp.verschijningsvorm, patchedResp.verschijningsvorm)
        assertEquals(resp.trefwoorden, patchedResp.trefwoorden)
        assertEquals(resp.inhoudIsVervallen, patchedResp.inhoudIsVervallen)
    }

    @Test
    fun `put should update all properties`() = runBlocking {
        val req = generateTestDocument(taal = "dut", bestandsnaam = "doc.pdf")
        val resp = service.create(req)
        assertEquals("dut", resp.taal)
        assertEquals("doc.pdf", resp.bestandsnaam)
        assertEquals(req.informatieobjecttype, resp.informatieobjecttype)
        assertEquals(1, resp.versie)
        assertTrue(resp.id.isNotEmpty())

        val uuid = UUID.fromString(resp.id)
        val putReq = EnkelvoudigInformatieObjectRequest(
            taal = "eng",
            bestandsnaam = "doc2.pdf",
            bronorganisatie = "012345678",
            informatieobjecttype = TestDataFactory.VALID_INFORMATIEOBJECTTYPE_URL,
            creatiedatum = resp.creatiedatum,
            titel = resp.titel,
            auteur = resp.auteur,
        )

        val putResp = service.update(uuid, putReq)
        assertNotNull(putResp)
        assertEquals("eng", putResp.taal)
        assertEquals("doc2.pdf", putResp.bestandsnaam)
        assertEquals(req.informatieobjecttype, putResp.informatieobjecttype)
        assertEquals(req.bronorganisatie, putResp.bronorganisatie)
        assertEquals(req.creatiedatum, putResp.creatiedatum)
        assertEquals(req.titel, putResp.titel)
        assertEquals(req.auteur, putResp.auteur)
        assertEquals(2, putResp.versie)
        assertTrue(putResp.id.isNotEmpty())
        // Version was changed though
        assertNotEquals(resp.inhoud, putResp.inhoud)
        // check remaining properties are cleared
        assertEquals("", putResp.formaat)
        assertNull(putResp.integriteit)
        assertEquals(null, putResp.status)
        assertNull(putResp.ondertekening)
        assertEquals(false, putResp.indicatieGebruiksrecht)
        assertEquals("", putResp.verschijningsvorm)
        assertEquals(emptyList(), putResp.trefwoorden)
        assertEquals(false, putResp.inhoudIsVervallen)
    }

    @Test
    fun `create and patch should check for required title`() = runBlocking {
        val req = EnkelvoudigInformatieObjectRequest(
            taal = "eng",
            bestandsnaam = "doc2.pdf",
            bronorganisatie = "012345678",
            creatiedatum =  LocalDate(2025, 1, 1),
            auteur = "auteur",
        )

        val createException = assertFailsWith<IllegalArgumentException> {
            service.create(req)
        }
        assertEquals("Titel mag niet leeg zijn", createException.message)

        val putException = assertFailsWith<IllegalArgumentException> {
            service.update(UUID.randomUUID(), req, false)
        }
        assertEquals("Titel mag niet leeg zijn", putException.message)

    }

    @Test
    fun `create and patch should check for required informatieobjecttype`() = runBlocking {
        val req = EnkelvoudigInformatieObjectRequest(
            taal = "eng",
            bestandsnaam = "doc2.pdf",
            bronorganisatie = "012345678",
            creatiedatum =  LocalDate(2025, 1, 1),
            auteur = "auteur",
            titel = "titel"
        )
        val createException = assertFailsWith<IllegalArgumentException> {
            service.create(req)
        }
        assertEquals("Informatieobjecttype mag niet leeg zijn", createException.message)
        val putException = assertFailsWith<IllegalArgumentException> {
            service.update(UUID.randomUUID(), req, false)
        }
        assertEquals("Informatieobjecttype mag niet leeg zijn", putException.message)
    }

    @Test
    fun `create and patch should check for required bronorganisatie`() = runBlocking {
        val req = EnkelvoudigInformatieObjectRequest(
            taal = "eng",
            bestandsnaam = "doc2.pdf",
            creatiedatum =  LocalDate(2025, 1, 1),
            auteur = "auteur",
            titel = "titel",
            informatieobjecttype = TestDataFactory.VALID_INFORMATIEOBJECTTYPE_URL
        )
        val createException = assertFailsWith<IllegalArgumentException> {
            service.create(req)
        }
        assertEquals("Bronorganisatie mag niet leeg zijn", createException.message)
        val putException = assertFailsWith<IllegalArgumentException> {
            service.update(UUID.randomUUID(), req, false)
        }
        assertEquals("Bronorganisatie mag niet leeg zijn", putException.message)
    }

    @Test
    fun `create and patch should check for required creatiedatum`() = runBlocking {
        val req = EnkelvoudigInformatieObjectRequest(
            taal = "eng",
            bestandsnaam = "doc2.pdf",
            bronorganisatie = "012345678",
            auteur = "auteur",
            titel = "titel",
            informatieobjecttype = TestDataFactory.VALID_INFORMATIEOBJECTTYPE_URL
        )
        val createException = assertFailsWith<IllegalArgumentException> {
            service.create(req)
        }
        assertEquals("Creatiedatum mag niet leeg zijn", createException.message)
        val putException = assertFailsWith<IllegalArgumentException> {
            service.update(UUID.randomUUID(), req, false)
        }
        assertEquals("Creatiedatum mag niet leeg zijn", putException.message)
    }

    @Test
    fun `create and patch should check for required auteur`() = runBlocking {
        val req = EnkelvoudigInformatieObjectRequest(
            taal = "eng",
            bestandsnaam = "doc2.pdf",
            bronorganisatie = "012345678",
            creatiedatum =  LocalDate(2025, 1, 1),
            titel = "titel",
            informatieobjecttype = TestDataFactory.VALID_INFORMATIEOBJECTTYPE_URL
        )
        val createException = assertFailsWith<IllegalArgumentException> {
            service.create(req)
        }
        assertEquals("Auteur mag niet leeg zijn", createException.message)
        val putException = assertFailsWith<IllegalArgumentException> {
            service.update(UUID.randomUUID(), req, false)
        }
        assertEquals("Auteur mag niet leeg zijn", putException.message)
    }

    @Test
    fun `create and patch should check for valid taal`() = runBlocking {
        val req = EnkelvoudigInformatieObjectRequest(
            bestandsnaam = "doc2.pdf",
            bronorganisatie = "012345678",
            creatiedatum =  LocalDate(2025, 1, 1),
            auteur = "auteur",
            titel = "titel",
            informatieobjecttype = TestDataFactory.VALID_INFORMATIEOBJECTTYPE_URL
        )
        val createException = assertFailsWith<IllegalArgumentException> {
            service.create(req)
        }
        assertEquals("Taal mag niet leeg zijn", createException.message)
        val putException = assertFailsWith<IllegalArgumentException> {
            service.update(UUID.randomUUID(), req, false)
        }
        assertEquals("Taal mag niet leeg zijn", putException.message)

        // create invalid taal
        val invalidCreateException = assertFailsWith<IllegalArgumentException> {
            req.copy(taal = "invalid")
        }
        assertEquals("Taal moet conform ISO 639-2/B code zijn", invalidCreateException.message)
    }

    @Test
    fun `identificatie mag niet langer dan 40 karakters zijn`() = runBlocking {
        val lonId = "a".repeat(41)
        val createException = assertFailsWith<IllegalArgumentException> {
            EnkelvoudigInformatieObjectRequest(identificatie = lonId)
        }
        assertEquals("Identificatie mag maximaal 40 karakters lang zijn", createException.message)
    }

    @Test
    fun `bestandsnaam mag niet langer dan 255 karakters zijn`() = runBlocking {
        val lonId = "a".repeat(256)
        val createException = assertFailsWith<IllegalArgumentException> {
            EnkelvoudigInformatieObjectRequest(bestandsnaam = lonId)
        }
        assertEquals("Bestandsnaam mag maximaal 255 karakters lang zijn", createException.message)
    }

    @Test
    fun `titel mag niet langer dan 200 karakters zijn`() = runBlocking {
        val lonId = "a".repeat(201)
        val createException = assertFailsWith<IllegalArgumentException> {
            EnkelvoudigInformatieObjectRequest(titel = lonId)
        }
        assertEquals("Titel mag maximaal 200 karakters lang zijn", createException.message)
    }

    @Test
    fun `auteur mag niet langer dan 200 karakters zijn`() = runBlocking {
        val lonId = "a".repeat(201)
        val createException = assertFailsWith<IllegalArgumentException> {
            EnkelvoudigInformatieObjectRequest(auteur = lonId)
        }
        assertEquals("Auteur mag maximaal 200 karakters lang zijn", createException.message)
    }

    @Test
    fun `beschrijving mag niet langer dan 1000 karakters zijn`() = runBlocking {
        val lonId = "a".repeat(1001)
        val createException = assertFailsWith<IllegalArgumentException> {
            EnkelvoudigInformatieObjectRequest(beschrijving = lonId)
        }
        assertEquals("Beschrijving mag maximaal 1000 karakters lang zijn", createException.message)
    }

    @Test
    fun `formaat mag niet langer dan 255 karakters zijn`() = runBlocking {
        val lonId = "a".repeat(256)
        val createException = assertFailsWith<IllegalArgumentException> {
            EnkelvoudigInformatieObjectRequest(formaat = lonId)
        }
        assertEquals("Formaat mag maximaal 255 karakters lang zijn", createException.message)
    }

    @Test
    fun `link mag niet langer dan 200 karakters zijn`() = runBlocking {
        val lonId = "a".repeat(201)
        val createException = assertFailsWith<IllegalArgumentException> {
            EnkelvoudigInformatieObjectRequest(link = lonId)
        }
        assertEquals("Link mag maximaal 200 karakters lang zijn", createException.message)
    }

    @Test
    fun `informatieobjecttype mag niet langer dan 200 karakters zijn`() = runBlocking {
        val lonId = "a".repeat(201)
        val createException = assertFailsWith<IllegalArgumentException> {
            EnkelvoudigInformatieObjectRequest(informatieobjecttype = lonId)
        }
        assertEquals("Informatieobjecttype mag maximaal 200 karakters lang zijn", createException.message)
    }

    @Test
    fun `trefwoorden mogen niet langer dan 100 karakters zijn`() = runBlocking {
        val lonId = "a".repeat(101)
        val trefwoorden = listOf(lonId, "Hello world")
        val createException = assertFailsWith<IllegalArgumentException> {
            EnkelvoudigInformatieObjectRequest(trefwoorden = trefwoorden)
        }
        assertEquals("Elk trefwoord mag maximaal 100 karakters lang zijn", createException.message)
    }

    @Test
    fun `ondertekening mag niet worden opgegeven voor status 'in bewerking' of 'ter vaststelling'`() = runBlocking {
        val inBewerkingException = assertFailsWith<IllegalArgumentException> {
            EnkelvoudigInformatieObjectRequest(ondertekening = Ondertekening(
                soort = OndertekeningSoort.DIGITAAL,
                datum = LocalDate(2025, 1, 1),
            ), status = EnkelvoudigInformatieObjectStatus.IN_BEWERKING)
        }
        assertEquals("Ondertekening mag niet worden opgegeven voor status 'in bewerking' of 'ter vaststelling'", inBewerkingException.message)

        val terVastStellingException = assertFailsWith<IllegalArgumentException> {
            EnkelvoudigInformatieObjectRequest(ondertekening = Ondertekening(
                soort = OndertekeningSoort.PKI,
                datum = LocalDate(2025, 1, 1),
            ), status = EnkelvoudigInformatieObjectStatus.TER_VASTSTELLING)
        }
        assertEquals("Ondertekening mag niet worden opgegeven voor status 'in bewerking' of 'ter vaststelling'", terVastStellingException.message)

        // check successful with another state
        val successfulRequest = EnkelvoudigInformatieObjectRequest(ondertekening = Ondertekening(
            soort = OndertekeningSoort.PKI,
            datum = LocalDate(2025, 1, 1),
        ), status = EnkelvoudigInformatieObjectStatus.DEFINITIEF)

        assertNotNull(successfulRequest)

        // assertNotNull doesn't return a Unit type...hence we need this extra implicit return...
        Unit
    }

    @Test
    fun `bestandsnaam moet opgegeven zijn als inhoud is opgegeven`() = runBlocking {
        val createException = assertFailsWith<IllegalArgumentException> {
            EnkelvoudigInformatieObjectRequest(
                bestandsomvang = 123456L,
                formaat = "application/pdf",
                inhoud = "inhoud",
            )
        }
        assertEquals("Bestandsnaam moet worden opgegeven als inhoud is opgegeven", createException.message)
    }

    @Test
    fun `formaat moet worden bepaald zijn als inhoud is opgegeven`() = runBlocking {

        val inputFileName = "/testdata/pdf_sample.pdf"
        val resource = requireNotNull(javaClass.getResource(inputFileName)) {
            "Missing test resource: $inputFileName"
        }
        val bytes = resource.readBytes()
        val pdfContent = Base64.encode(bytes)
        var request = generateTestDocument()
        request = request.copy(
                bestandsnaam = "doc.pdf",
                bestandsomvang = 123456L,
                inhoud = pdfContent
            )
        val resp = service.create(request)
        assertEquals("application/pdf", resp.formaat)
    }

    @Test
    fun `formaat moet opgegeven zijn als het formaat niet bepaald kan worden`() = runBlocking{
        var request = generateTestDocument()
        request = request.copy(inhoud = "dGVzdA==", formaat = null)
        val exception = assertFailsWith<IllegalArgumentException> {
            service.create(request)
        }
        assertEquals("Unable to determine file format from content. Please specify the 'formaat' field in the request.", exception.message)
    }


    @Test fun `update file location if content has changed`() = runBlocking {
        val req = generateTestDocument(bestandsnaam = "doc.pdf")
        val reqWithContent = req.copy(inhoud = PDF_CONTENT, formaat = "application/pdf", bestandsomvang = 595L)
        val resp = service.create(reqWithContent)

        // get entity from database
        var eio = transaction {
            val record = EIORecordEntity.findById(UUID.fromString(resp.id)) ?: return@transaction null
            record.versions.maxByOrNull { it.versie } ?: return@transaction null
        }

        assertNotNull(eio)
        assertEquals(eio.versie, 1)
        assertEquals("${resp.id}/1/${req.bestandsnaam}", eio.bestandsLocatie)


        val requestWithUpdatedContent = req.copy(inhoud = PDF_CONTENT_ALT, formaat = "application/pdf", bestandsomvang = 620L)
        val patchedResp = service.update(UUID.fromString(resp.id), requestWithUpdatedContent, false)
        assertNotNull(patchedResp)
        assertEquals("doc.pdf", patchedResp.bestandsnaam)

        eio = transaction {
            val record = EIORecordEntity.findById(UUID.fromString(resp.id)) ?: return@transaction null
            record.versions.maxByOrNull { it.versie } ?: return@transaction null
        }

        assertNotNull(eio)
        assertEquals(eio.versie, 2)
        assertEquals("${resp.id}/2/${req.bestandsnaam}", eio.bestandsLocatie)

        // perform a patch without content and check that the file location is not updated
        val patchedResp2 = service.update(UUID.fromString(resp.id), requestWithUpdatedContent.copy(inhoud = ""), false)
        assertNotNull(patchedResp2)
        assertEquals("doc.pdf", patchedResp2.bestandsnaam)
        eio = transaction {
            val record = EIORecordEntity.findById(UUID.fromString(resp.id)) ?: return@transaction null
            record.versions.maxByOrNull { it.versie } ?: return@transaction null
        }

        assertNotNull(eio)
        assertEquals(eio.versie, 3)
        assertEquals("${resp.id}/2/${req.bestandsnaam}", eio.bestandsLocatie)
    }
}