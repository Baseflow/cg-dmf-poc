// SPDX-License-Identifier: EUPL-1.2
// Copyright (C) 2025-2026 Gemeente Utrecht
package com.baseflow.services

import com.baseflow.EIORecordEntity
import com.baseflow.config.ApplicationConfig
import com.baseflow.config.OpenZaakConfig
import com.baseflow.testutils.TestDataFactory.generateTestDocument
import com.baseflow.services.models.DeleteResult
import com.baseflow.services.models.LockResult
import com.baseflow.services.models.UnlockResult
import com.baseflow.api.models.Vertrouwelijkheidaanduiding
import com.baseflow.tooling.AllTables
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import io.mockk.*
import kotlin.test.*
import java.util.UUID

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
        service = EnkelvoudigInformatieObjectService(storageService = mockStorageService, ApplicationConfig, OpenZaakService(openZaakConfig))
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
        assertEquals(created.id, found!!.id)
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
        assertEquals(created.id, updated!!.id)
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
            assertEquals(token, rec!!.lockToken)
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
            assertNull(rec!!.lockToken)
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

        val res = service.unlock(id, token + "-wrong")
        assertTrue(res is UnlockResult.InvalidLock)

        transaction {
            val rec = EIORecordEntity.findById(id)
            assertNotNull(rec)
            assertEquals(token, rec!!.lockToken)
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
    fun `create should default bestandsomvang to 0 if not provided and no content`() = runBlocking {
        val req = generateTestDocument().copy(
            inhoud = null,
            bestandsomvang = null,
            link = "https://example.com/file"
        )
        val resp = service.create(req)
        assertEquals(0L, resp.bestandsomvang)
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

        val customService = EnkelvoudigInformatieObjectService(StorageService(), ApplicationConfig, mockOpenZaakService)

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
}