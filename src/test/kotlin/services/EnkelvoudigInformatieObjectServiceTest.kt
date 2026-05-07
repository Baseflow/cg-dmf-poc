// SPDX-License-Identifier: EUPL-1.2
// Copyright (C) 2025-2026 Gemeente Utrecht
@file:Suppress("UnusedDataClassCopyResult")

package com.baseflow.services

import com.baseflow.api.middleware.AuditContext
import com.baseflow.api.models.EnkelvoudigInformatieObjectRequest
import com.baseflow.api.models.EnkelvoudigInformatieObjectStatus
import com.baseflow.api.models.Ondertekening
import com.baseflow.api.models.OndertekeningSoort
import com.baseflow.api.models.Vertrouwelijkheidaanduiding
import com.baseflow.config.ApplicationConfig
import com.baseflow.config.BestandsDeelConfig
import com.baseflow.config.OpenZaakConfig
import com.baseflow.entities.BestandsDeelEntity
import com.baseflow.entities.BestandsDelen
import com.baseflow.entities.EIORecordEntity
import com.baseflow.entities.EIOVersionTrefwoorden
import com.baseflow.entities.Trefwoorden
import com.baseflow.services.models.DeleteResult
import com.baseflow.services.models.EIOOrdering
import com.baseflow.services.models.LockResult
import com.baseflow.services.models.QueryEnkelvoudigeInformatieObjectenFilter
import com.baseflow.services.models.UnlockResult
import com.baseflow.testutils.TestDataFactory
import com.baseflow.testutils.TestDataFactory.PDF_CONTENT
import com.baseflow.testutils.TestDataFactory.PDF_CONTENT_ALT
import com.baseflow.testutils.TestDataFactory.generateTestDocument
import com.baseflow.tooling.AllTables
import io.mockk.*
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.io.ByteArrayOutputStream
import java.util.UUID
import java.util.concurrent.CompletableFuture
import kotlin.io.encoding.Base64
import kotlin.test.*

class EnkelvoudigInformatieObjectServiceTest {
    private lateinit var service: EnkelvoudigInformatieObjectService
    private lateinit var mockStorageService: StorageService
    private lateinit var mockAuditTrailService: AuditTrailService

    @BeforeTest
    fun setup() {
        BlobStorageRegistrar.resetForTesting()
        Database.connect(
            "jdbc:h2:mem:test_eio;DB_CLOSE_DELAY=-1;",
            driver = "org.h2.Driver",
            user = "root",
            password = "",
        )
        transaction {
            // Create all tables
            AllTables.createMissing()
        }
        val openZaakConfig = OpenZaakConfig(validationEnabled = false)
        mockStorageService = mockk<StorageService>()
        every { mockStorageService.uploadFile(any<String>(), any<ByteArray>(), anyNullable()) } returns Unit
        every { mockStorageService.uploadFile(any<String>(), any<java.io.InputStream>(), any<Long>(), anyNullable()) } returns Unit
        every { mockStorageService.deleteFiles(any(), anyNullable()) } returns Unit
        val auditContext = AuditContext()
        mockAuditTrailService = mockk<AuditTrailService>()
        every { mockAuditTrailService.removeAuditTrailsForResource(any()) } returns Unit
        service = EnkelvoudigInformatieObjectService(
            storageService = mockStorageService,
            ApplicationConfig,
            CatalogusService(openZaakConfig),
            mockAuditTrailService,
            auditContext,
            BestandsDeelService(),
        )
    }

    @AfterTest
    fun teardown() {
        BlobStorageRegistrar.resetForTesting()
        transaction {
            // Drop in reverse order of dependencies
            SchemaUtils.drop(*AllTables.tables.reversedArray())
        }
    }

    @Test
    fun `create should persist and return correct data`() = runBlocking {
        val req = generateTestDocument(taal = "dut", bestandsnaam = "doc.pdf").copy(
            vertrouwelijkheidaanduiding = Vertrouwelijkheidaanduiding.INTERN,
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
        val updateReq =
            generateTestDocument(
                taal = "eng",
                bestandsnaam = "doc2.pdf",
                informatieobjecttype = "https://example.com/api/v1/informatieobjecttypen/new-type",
            )
        val updated = service.update(UUID.fromString(created.id), updateReq)
        assertNotNull(updated)
        assertEquals(created.id, updated.id)
        assertEquals("eng", updated.taal)
        assertEquals("doc2.pdf", updated.bestandsnaam)
        assertEquals(updateReq.informatieobjecttype, updated.informatieobjecttype)
        assertEquals(2, updated.versie)
    }

    @Test
    fun `getById should return null for unknown id`() = runBlocking {
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
    fun `unlock should return null for unknown id`() = runBlocking {
        val res = service.unlock(UUID.randomUUID(), "some-token")
        assertNull(res)
    }

    @Test
    fun `unlock with bestandsdelen merges parts uploads merged file and removes parts`() = runBlocking {
        // Use a small trigger size so chunking kicks in for our test file
        val smallChunkConfig = object : BestandsDeelConfig() {
            override val triggerSizeBytes: Long = 1L
            override val chunkSizeBytes: Long = 100L
        }
        val auditContext = AuditContext()
        val serviceWithChunking = EnkelvoudigInformatieObjectService(
            storageService = mockStorageService,
            ApplicationConfig,
            CatalogusService(OpenZaakConfig(validationEnabled = false)),
            mockAuditTrailService,
            auditContext,
            BestandsDeelService(smallChunkConfig),
        )

        val chunkBytes1 = ByteArray(100) { it.toByte() }
        val chunkBytes2 = ByteArray(50) { (it + 100).toByte() }
        val totalSize = (chunkBytes1.size + chunkBytes2.size).toLong()

        val req = generateTestDocument(bestandsnaam = "big.pdf").copy(
            bestandsomvang = totalSize,
            inhoud = null,
            formaat = "application/pdf",
        )
        val created = serviceWithChunking.create(req)
        val id = UUID.fromString(created.id)

        // When chunking is triggered, the EIO is auto-locked on create; retrieve the token from DB
        val token = transaction {
            EIORecordEntity.findById(id)!!.lockToken!!
        }

        // Simulate uploading both parts
        val latestVersion = transaction {
            EIORecordEntity.findById(id)!!.versions.maxByOrNull { it.versie }!!
        }
        val parts = transaction {
            BestandsDeelEntity
                .find { BestandsDelen.versionId eq latestVersion.id }
                .sortedBy { it.volgnummer }
        }
        assertEquals(2, parts.size)

        // Mark parts as voltooid and set up download stubs
        transaction {
            parts[0].voltooid = true
            parts[1].voltooid = true
        }

        val part1Key = bestandsDeelStorageKey(id, 1, parts[0].id.value)
        val part2Key = bestandsDeelStorageKey(id, 1, parts[1].id.value)
        every { mockStorageService.downloadFileTo(eq(part1Key), any(), anyNullable()) } answers {
            val out = secondArg<java.io.OutputStream>()
            out.write(chunkBytes1)
            CompletableFuture.completedFuture(null)
        }
        every { mockStorageService.downloadFileTo(eq(part2Key), any(), anyNullable()) } answers {
            val out = secondArg<java.io.OutputStream>()
            out.write(chunkBytes2)
            CompletableFuture.completedFuture(null)
        }

        val mergedKey = "$id/1/big.pdf"
        val mergedBytesSlot = mutableListOf<ByteArray>()
        every { mockStorageService.uploadFile(eq(mergedKey), any<java.io.InputStream>(), any<Long>(), anyNullable()) } answers {
            // Read the stream eagerly so we can assert on its contents later.
            val captured = secondArg<java.io.InputStream>().readBytes()
            mergedBytesSlot.add(captured)
            Unit
        }
        every { mockStorageService.deleteFiles(any(), anyNullable()) } returns Unit

        val unlockRes = serviceWithChunking.unlock(id, token)
        assertTrue(unlockRes is UnlockResult.Success)

        // Verify merged content was uploaded via the streaming overload
        verify { mockStorageService.uploadFile(eq(mergedKey), any<java.io.InputStream>(), any<Long>(), anyNullable()) }
        val merged = mergedBytesSlot.first()
        assertEquals(totalSize.toInt(), merged.size)
        assertContentEquals(chunkBytes1 + chunkBytes2, merged)

        // Lock should be cleared
        transaction {
            val rec = EIORecordEntity.findById(id)
            assertNotNull(rec)
            assertNull(rec.lockToken)
        }

        // bestandsdelen rows should be removed
        transaction {
            val remaining = BestandsDeelEntity
                .find { BestandsDelen.versionId eq latestVersion.id }
                .count()
            assertEquals(0L, remaining)
        }
    }

    @Test
    fun `unlock should throw when not all bestandsdelen are completed`() = runBlocking {
        val smallChunkConfig = object : BestandsDeelConfig() {
            override val triggerSizeBytes: Long = 1L
            override val chunkSizeBytes: Long = 100L
        }
        val auditContext = AuditContext()
        val serviceWithChunking = EnkelvoudigInformatieObjectService(
            storageService = mockStorageService,
            ApplicationConfig,
            CatalogusService(OpenZaakConfig(validationEnabled = false)),
            mockAuditTrailService,
            auditContext,
            BestandsDeelService(smallChunkConfig),
        )

        val req = generateTestDocument(bestandsnaam = "big.pdf").copy(
            bestandsomvang = 150L,
            inhoud = null,
            formaat = "application/pdf",
        )
        val created = serviceWithChunking.create(req)
        val id = UUID.fromString(created.id)

        // When chunking is triggered, the EIO is auto-locked on create; retrieve the token from DB
        val token = transaction {
            EIORecordEntity.findById(id)!!.lockToken!!
        }

        // Leave bestandsdelen as voltooid = false (default)
        val exception = assertFailsWith<IllegalStateException> {
            serviceWithChunking.unlock(id, token)
        }
        assertEquals("Not all parts are marked as completed", exception.message)
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
        verify(exactly = 0) { mockStorageService.deleteFiles(any(), anyNullable()) }
        verify(exactly = 0) { mockAuditTrailService.removeAuditTrailsForResource(any()) }
    }

    @Test
    fun `delete should return Locked when record has lockToken`() = runBlocking {
        val created = service.create(generateTestDocument(withContent = true))
        val id = UUID.fromString(created.id)
        // lock it
        service.lock(id)
        val res = service.delete(id)
        assertTrue(res is DeleteResult.Locked)
        verify(exactly = 0) { mockStorageService.deleteFiles(any(), anyNullable()) }
        verify(exactly = 0) { mockAuditTrailService.removeAuditTrailsForResource(any()) }
    }

    @Test
    fun `delete should return Success when record exists and is not locked`() = runBlocking {
        val created = service.create(generateTestDocument(withContent = true))
        val id = UUID.fromString(created.id)
        val res = service.delete(id)
        assertTrue(res is DeleteResult.Success)
        // and now it should not exist
        assertFalse(service.exists(id))
        verify { mockStorageService.deleteFiles(match { it.contains("$id/1/test.pdf") }, null) }
        verify { mockAuditTrailService.removeAuditTrailsForResource(id) }
    }

    @Test
    fun `create should derive bestandsomvang from content if not provided`() = runBlocking {
        val content = "Hello World"
        val base64Content = java.util.Base64.getEncoder().encodeToString(content.toByteArray())
        val req = generateTestDocument().copy(
            inhoud = base64Content,
            bestandsomvang = null,
            formaat = "text/plain",
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
            formaat = "text/plain",
        )
        val resp = service.create(req)
        assertEquals(100L, resp.bestandsomvang)
    }

    @Test
    fun `create should null bestandsomvang if not provided and no content`() = runBlocking {
        val req = generateTestDocument().copy(
            inhoud = null,
            bestandsomvang = null,
            link = "https://example.com/file",
        )
        val resp = service.create(req)
        assertEquals(null, resp.bestandsomvang)
    }

    @Test
    fun `create should inherit vertrouwelijkheidaanduiding from informatieobjecttype if not provided`() = runBlocking {
        // We need a custom service with a mocked CatalogusService for this
        val mockCatalogusService = mockk<CatalogusService>()
        coEvery { mockCatalogusService.validateInformatieobjecttype(any()) } returns InformatieObjectType(
            url = "https://example.com/api/v1/informatieobjecttypen/1",
            omschrijving = "Mock Type",
            vertrouwelijkheidaanduiding = "geheim",
        )

        val auditContext = AuditContext()
        val customService = EnkelvoudigInformatieObjectService(
            mockk<StorageService>(relaxed = true),
            ApplicationConfig,
            mockCatalogusService,
            AuditTrailService(auditContext),
            auditContext,
            BestandsDeelService(),
        )

        val req = generateTestDocument().copy(vertrouwelijkheidaanduiding = null)
        val resp = customService.create(req)

        assertEquals(Vertrouwelijkheidaanduiding.GEHEIM, resp.vertrouwelijkheidaanduiding)
        coVerify { mockCatalogusService.validateInformatieobjecttype(any()) }
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
            creatiedatum = LocalDate(2025, 1, 1),
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
            creatiedatum = LocalDate(2025, 1, 1),
            auteur = "auteur",
            titel = "titel",
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
            creatiedatum = LocalDate(2025, 1, 1),
            auteur = "auteur",
            titel = "titel",
            informatieobjecttype = TestDataFactory.VALID_INFORMATIEOBJECTTYPE_URL,
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
            informatieobjecttype = TestDataFactory.VALID_INFORMATIEOBJECTTYPE_URL,
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
            creatiedatum = LocalDate(2025, 1, 1),
            titel = "titel",
            informatieobjecttype = TestDataFactory.VALID_INFORMATIEOBJECTTYPE_URL,
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
            creatiedatum = LocalDate(2025, 1, 1),
            auteur = "auteur",
            titel = "titel",
            informatieobjecttype = TestDataFactory.VALID_INFORMATIEOBJECTTYPE_URL,
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
            EnkelvoudigInformatieObjectRequest(
                ondertekening = Ondertekening(
                    soort = OndertekeningSoort.DIGITAAL,
                    datum = LocalDate(2025, 1, 1),
                ),
                status = EnkelvoudigInformatieObjectStatus.IN_BEWERKING,
            )
        }
        assertEquals(
            "Ondertekening mag niet worden opgegeven voor status 'in bewerking' of 'ter vaststelling'",
            inBewerkingException.message,
        )

        val terVastStellingException = assertFailsWith<IllegalArgumentException> {
            EnkelvoudigInformatieObjectRequest(
                ondertekening = Ondertekening(
                    soort = OndertekeningSoort.PKI,
                    datum = LocalDate(2025, 1, 1),
                ),
                status = EnkelvoudigInformatieObjectStatus.TER_VASTSTELLING,
            )
        }
        assertEquals(
            "Ondertekening mag niet worden opgegeven voor status 'in bewerking' of 'ter vaststelling'",
            terVastStellingException.message,
        )

        // check successful with another state
        val successfulRequest = EnkelvoudigInformatieObjectRequest(
            ondertekening = Ondertekening(
                soort = OndertekeningSoort.PKI,
                datum = LocalDate(2025, 1, 1),
            ),
            status = EnkelvoudigInformatieObjectStatus.DEFINITIEF,
        )

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
            inhoud = pdfContent,
        )
        val resp = service.create(request)
        assertEquals("application/pdf", resp.formaat)
    }

    @Test
    fun `formaat moet opgegeven zijn als het formaat niet bepaald kan worden`() = runBlocking {
        var request = generateTestDocument()
        request = request.copy(inhoud = "dGVzdA==", formaat = null)
        val exception = assertFailsWith<IllegalArgumentException> {
            service.create(request)
        }
        assertEquals(
            "Unable to determine file format from content. Please specify the 'formaat' field in the request.",
            exception.message,
        )
    }

    @Test
    fun `update file location if content has changed`() = runBlocking {
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

        val requestWithUpdatedContent = req.copy(
            inhoud = PDF_CONTENT_ALT,
            formaat = "application/pdf",
            bestandsomvang = 620L,
        )
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

    // ── bestandsRepository routing ────────────────────────────────────────────

    @Test
    fun `streamByBestandsnaam passes named repoName to downloadFileTo`() {
        every { mockStorageService.downloadFileTo(any(), any(), eq("archive-repo")) } returns CompletableFuture.completedFuture(null)
        service.streamByBestandsnaam("path/to/file.pdf", ByteArrayOutputStream(), "archive-repo")
        verify { mockStorageService.downloadFileTo("path/to/file.pdf", any(), "archive-repo") }
    }

    @Test
    fun `streamByBestandsnaam uses default provider (null) when repoName is blank`() {
        every { mockStorageService.downloadFileTo(any(), any(), null) } returns CompletableFuture.completedFuture(null)
        service.streamByBestandsnaam("path/to/file.pdf", ByteArrayOutputStream(), "")
        verify { mockStorageService.downloadFileTo("path/to/file.pdf", any(), null) }
    }

    @Test
    fun `create resolves default repository name for upload and persistence`() = runBlocking {
        val defaultProvider = mockk<BlobStorageProvider>()
        every { defaultProvider.name } returns "default-repo"
        BlobStorageRegistrar.registerForTesting(defaultProvider, isDefault = true)

        val req = generateTestDocument(withContent = true)
        val response = service.create(req)

        verify { mockStorageService.uploadFile(any(), any(), "default-repo") }
        transaction {
            val latest = EIORecordEntity.findById(UUID.fromString(response.id))!!.versions.maxByOrNull { it.versie }!!
            assertEquals("default-repo", latest.bestandsRepository)
        }
    }

    @Test
    fun `update inherits bestandsRepository from the previous version when no new file is uploaded`() = runBlocking {
        val created = service.create(generateTestDocument())
        val id = UUID.fromString(created.id)

        transaction {
            EIORecordEntity.findById(id)!!.versions.maxByOrNull { it.versie }!!.bestandsRepository = "repo-a"
        }

        service.update(id, generateTestDocument())

        transaction {
            val v2 = EIORecordEntity.findById(id)!!.versions.maxByOrNull { it.versie }!!
            assertEquals(2, v2.versie)
            assertEquals("repo-a", v2.bestandsRepository)
        }
    }

    @Test
    fun `update with new content uploads to and persists previous version repository`() = runBlocking {
        val created = service.create(generateTestDocument(withContent = true))
        val id = UUID.fromString(created.id)

        transaction {
            EIORecordEntity.findById(id)!!.versions.maxByOrNull { it.versie }!!.bestandsRepository = "repo-a"
        }

        service.update(id, generateTestDocument(withContent = true))

        verify { mockStorageService.uploadFile(any(), any(), "repo-a") }
        transaction {
            val v2 = EIORecordEntity.findById(id)!!.versions.maxByOrNull { it.versie }!!
            assertEquals(2, v2.versie)
            assertEquals("repo-a", v2.bestandsRepository)
        }
    }

    @Test
    fun `delete calls deleteFiles once per distinct bestandsRepository`() = runBlocking {
        val created = service.create(generateTestDocument(withContent = true))
        val id = UUID.fromString(created.id)

        transaction {
            EIORecordEntity.findById(id)!!.versions.first { it.versie == 1 }.bestandsRepository = "repo-a"
        }

        service.update(id, generateTestDocument(withContent = true))
        transaction {
            EIORecordEntity.findById(id)!!.versions.first { it.versie == 2 }.bestandsRepository = "repo-b"
        }

        service.delete(id)

        verify { mockStorageService.deleteFiles(any(), "repo-a") }
        verify { mockStorageService.deleteFiles(any(), "repo-b") }
        verify(exactly = 2) { mockStorageService.deleteFiles(any(), anyNullable()) }
    }

    @Test
    fun `delete deduplicates keys per repository before batch delete`() = runBlocking {
        val created = service.create(generateTestDocument(withContent = true))
        val id = UUID.fromString(created.id)

        transaction {
            EIORecordEntity.findById(id)!!.versions.first { it.versie == 1 }.bestandsRepository = "repo-a"
        }

        service.update(id, generateTestDocument(withContent = false))
        transaction {
            EIORecordEntity.findById(id)!!.versions.first { it.versie == 2 }.bestandsRepository = "repo-a"
        }

        service.delete(id)

        val keysSlot = mutableListOf<List<String>>()
        verify(exactly = 1) { mockStorageService.deleteFiles(capture(keysSlot), "repo-a") }
        assertEquals(1, keysSlot.size)
        assertEquals(keysSlot.first().distinct().size, keysSlot.first().size)
    }

    @Test
    fun `getAll totalCount should be correct when ordering and pagination are applied`() = runBlocking {
        // Create 5 distinct records
        val titles = listOf("Aardbei", "Banaan", "Citroen", "Dadel", "Esdoorn")
        titles.forEach { titel ->
            service.create(generateTestDocument(titel = titel))
        }

        // Fetch page 1 with pageSize=2, ordered by titel ascending
        val (page1Results, totalCount) = service.getAll(
            QueryEnkelvoudigeInformatieObjectenFilter(page = 1, pageSize = 2, ordering = listOf(EIOOrdering.TITEL_ASC)),
        )

        // totalCount must reflect all 5 records, not just the 2 on this page
        assertEquals(5L, totalCount)
        assertEquals(2, page1Results.size)
        // First page should contain the two alphabetically first titles
        assertEquals("Aardbei", page1Results[0].titel)
        assertEquals("Banaan", page1Results[1].titel)

        // Fetch page 2 and verify count is still consistent
        val (page2Results, totalCount2) = service.getAll(
            QueryEnkelvoudigeInformatieObjectenFilter(page = 2, pageSize = 2, ordering = listOf(EIOOrdering.TITEL_ASC)),
        )
        assertEquals(5L, totalCount2)
        assertEquals(2, page2Results.size)
        assertEquals("Citroen", page2Results[0].titel)
        assertEquals("Dadel", page2Results[1].titel)

        // Fetch page 3 (last, partial page)
        val (page3Results, totalCount3) = service.getAll(
            QueryEnkelvoudigeInformatieObjectenFilter(page = 3, pageSize = 2, ordering = listOf(EIOOrdering.TITEL_ASC)),
        )
        assertEquals(5L, totalCount3)
        assertEquals(1, page3Results.size)
        assertEquals("Esdoorn", page3Results[0].titel)
    }

    @Test
    fun `trefwoorden with the same lowercase value share one trefwoord record`() = runBlocking {
        // Two documents with the same trefwoord, one in uppercase and one in lowercase
        val req1 = generateTestDocument().copy(trefwoorden = listOf("Ruimte", "duurzaam"))
        val req2 = generateTestDocument().copy(trefwoorden = listOf("RUIMTE", "DUURZAAM"))

        val resp1 = service.create(req1)
        val resp2 = service.create(req2)

        // Both responses should have lowercase trefwoorden (order not guaranteed)
        assertEquals(listOf("duurzaam", "ruimte"), resp1.trefwoorden.sorted())
        assertEquals(listOf("duurzaam", "ruimte"), resp2.trefwoorden.sorted())

        // The trefwoorden table should only have 2 unique records (not 4)
        val trefwoordCount = transaction {
            Trefwoorden.selectAll().count()
        }
        assertEquals(2L, trefwoordCount, "Expected 2 unique trefwoord records, shared across both documents")

        // The join table should have 4 rows (2 per document)
        val joinCount = transaction {
            EIOVersionTrefwoorden.selectAll().count()
        }
        assertEquals(4L, joinCount, "Expected 4 rows in the join table (2 trefwoorden × 2 documents)")
    }

    @Test
    fun `getAll should return only the latest version for records with multiple versions`() = runBlocking {
        // Create a record
        val reqV1 = generateTestDocument(taal = "dut", bestandsnaam = "doc-v1.pdf")
        val created = service.create(reqV1)
        val recordId = UUID.fromString(created.id)

        // Add two more versions
        val reqV2 = generateTestDocument(taal = "eng", bestandsnaam = "doc-v2.pdf")
        val v2 = service.update(recordId, reqV2)
        assertNotNull(v2)
        val reqV3 = generateTestDocument(taal = "ger", bestandsnaam = "doc-v3.pdf")
        val v3 = service.update(recordId, reqV3)
        assertNotNull(v3)

        // Call getAll
        val (results, _) = service.getAll(QueryEnkelvoudigeInformatieObjectenFilter())
        // There should be only one record (since we only created one)
        assertEquals(1, results.size)
        // The returned version should be the latest (3)
        val result = results.first()
        assertEquals(recordId.toString(), result.id)
        assertEquals(3, result.versie)
        assertEquals("ger", result.taal)
        assertEquals("doc-v3.pdf", result.bestandsnaam)
    }
}
