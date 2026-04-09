// SPDX-License-Identifier: EUPL-1.2
// Copyright (C) 2026 Gemeente Utrecht
package com.baseflow.services

import com.baseflow.api.middleware.AuditContext
import com.baseflow.config.ApplicationConfig
import com.baseflow.config.BestandsDeelConfig
import com.baseflow.config.OpenZaakConfig
import com.baseflow.testutils.TestDataFactory.generateTestDocument
import com.baseflow.tooling.AllTables
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.util.UUID
import kotlin.test.*

class BestandsDeelServiceTest {
    // ── splitIntoChunks ───────────────────────────────────────────────────────

    @Test
    fun `splitIntoChunks returns single chunk when file fits in one chunk`() {
        val config = testConfig(chunkSize = 3L * GB)
        val service = BestandsDeelService(config)
        val result = service.splitIntoChunks(1L * GB)
        assertEquals(listOf(1L * GB), result)
    }

    @Test
    fun `splitIntoChunks returns two chunks when file is exactly two chunk sizes`() {
        val config = testConfig(chunkSize = 3L * GB)
        val service = BestandsDeelService(config)
        val result = service.splitIntoChunks(6L * GB)
        assertEquals(listOf(3L * GB, 3L * GB), result)
    }

    @Test
    fun `splitIntoChunks last chunk is smaller when file is not a multiple of chunk size`() {
        val config = testConfig(chunkSize = 3L * GB)
        val service = BestandsDeelService(config)
        val result = service.splitIntoChunks(7L * GB)
        assertEquals(listOf(3L * GB, 3L * GB, 1L * GB), result)
    }

    @Test
    fun `splitIntoChunks sum of chunks equals total file size`() {
        val config = testConfig(chunkSize = 3L * GB)
        val service = BestandsDeelService(config)
        val total = 10_000_000_001L // awkward size
        val chunks = service.splitIntoChunks(total)
        assertEquals(total, chunks.sum())
    }

    @Test
    fun `splitIntoChunks handles file of exactly one byte`() {
        val config = testConfig(chunkSize = 3L * GB)
        val service = BestandsDeelService(config)
        assertEquals(listOf(1L), service.splitIntoChunks(1L))
    }

    // ── requiresChunking ─────────────────────────────────────────────────────

    @Test
    fun `requiresChunking returns false when bestandsomvang is null`() {
        val service = BestandsDeelService(testConfig(triggerSize = 4L * GB))
        assertFalse(service.requiresChunking(null))
    }

    @Test
    fun `requiresChunking returns false when bestandsomvang is exactly trigger size`() {
        val trigger = 4L * GB
        val service = BestandsDeelService(testConfig(triggerSize = trigger))
        assertFalse(service.requiresChunking(trigger))
    }

    @Test
    fun `requiresChunking returns true when bestandsomvang exceeds trigger size`() {
        val trigger = 4L * GB
        val service = BestandsDeelService(testConfig(triggerSize = trigger))
        assertTrue(service.requiresChunking(trigger + 1))
    }

    // ── integration: bestandsdelen created in DB ──────────────────────────────

    private lateinit var eioService: EnkelvoudigInformatieObjectService
    private lateinit var bestandsDeelService: BestandsDeelService

    @BeforeTest
    fun setupDb() {
        Database.connect(
            "jdbc:h2:mem:test_bestandsdelen;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
            driver = "org.h2.Driver",
            user = "root",
            password = "",
        )
        transaction { AllTables.createMissing() }

        // Use a very small trigger size so we can test chunking without giant files
        val smallConfig = testConfig(triggerSize = 10L, chunkSize = 4L)
        bestandsDeelService = BestandsDeelService(smallConfig)

        val mockStorageService = mockk<StorageService>()
        every { mockStorageService.uploadFile(any(), any()) } returns Unit
        val auditContext = AuditContext()
        eioService = EnkelvoudigInformatieObjectService(
            storageService = mockStorageService,
            applicationConfig = ApplicationConfig,
            catalogusService = CatalogusService(OpenZaakConfig(validationEnabled = false)),
            auditTrailService = AuditTrailService(auditContext),
            auditContext = auditContext,
            bestandsDeelService = bestandsDeelService,
        )
    }

    @AfterTest
    fun teardownDb() {
        transaction { SchemaUtils.drop(*AllTables.tables.reversedArray()) }
    }

    @Test
    fun `create EIO with large bestandsomvang returns populated bestandsdelen list`() = runBlocking {
        // 11 bytes > trigger of 10 → should produce 3 chunks of [4, 4, 3]
        val request = generateTestDocument().copy(bestandsomvang = 11L)
        val response = eioService.create(request)

        assertEquals(3, response.bestandsdelen.size)
        assertEquals(1, response.bestandsdelen[0].volgnummer)
        assertEquals(4L, response.bestandsdelen[0].omvang)
        assertEquals(2, response.bestandsdelen[1].volgnummer)
        assertEquals(4L, response.bestandsdelen[1].omvang)
        assertEquals(3, response.bestandsdelen[2].volgnummer)
        assertEquals(3L, response.bestandsdelen[2].omvang)
        assertTrue(response.locked, "Record should be locked when bestandsdelen are created")
        response.bestandsdelen.forEach { assertFalse(it.voltooid, "New parts should not be marked voltooid") }
    }

    @Test
    fun `create EIO with small bestandsomvang returns empty bestandsdelen list`() = runBlocking {
        val request = generateTestDocument().copy(bestandsomvang = 5L) // below trigger of 10
        val response = eioService.create(request)
        assertTrue(response.bestandsdelen.isEmpty())
    }

    // ── markVoltooid ─────────────────────────────────────────────────────────

    @Test
    fun `markVoltooid returns Success and sets voltooid to true for correct lock`() = runBlocking {
        val request = generateTestDocument().copy(bestandsomvang = 11L)
        val eio = eioService.create(request)
        val part = eio.bestandsdelen.first()
        val uuid = UUID.fromString(part.url.substringAfterLast("/"))

        val result = bestandsDeelService.markVoltooid(uuid, part.lock)

        assertIs<MarkVoltooidResult.Success>(result)
        assertTrue(result.response.voltooid)
        assertEquals(uuid.toString(), result.response.url.substringAfterLast("/"))
    }

    @Test
    fun `markVoltooid returns InvalidLock when lock token does not match`(): Unit = runBlocking {
        val request = generateTestDocument().copy(bestandsomvang = 11L)
        val eio = eioService.create(request)
        val part = eio.bestandsdelen.first()
        val uuid = UUID.fromString(part.url.substringAfterLast("/"))

        val result = bestandsDeelService.markVoltooid(uuid, "wrong-token")

        assertIs<MarkVoltooidResult.InvalidLock>(result)
    }

    @Test
    fun `markVoltooid returns NotFound for unknown UUID`() {
        val result = bestandsDeelService.markVoltooid(UUID.randomUUID(), "any-token")

        assertIs<MarkVoltooidResult.NotFound>(result)
    }

    @Test
    fun `markVoltooid does not mutate other parts when one part is marked voltooid`() = runBlocking {
        // 11 bytes → 3 chunks: [4, 4, 3]; mark only the first part as voltooid
        val request = generateTestDocument().copy(bestandsomvang = 11L)
        val eio = eioService.create(request)
        val firstPart = eio.bestandsdelen[0]
        val uuid = UUID.fromString(firstPart.url.substringAfterLast("/"))

        bestandsDeelService.markVoltooid(uuid, firstPart.lock)

        // The other two parts must still be voltooid = false
        val secondUuid = UUID.fromString(eio.bestandsdelen[1].url.substringAfterLast("/"))
        val thirdUuid = UUID.fromString(eio.bestandsdelen[2].url.substringAfterLast("/"))

        val secondResult = bestandsDeelService.markVoltooid(secondUuid, eio.bestandsdelen[1].lock)
        assertIs<MarkVoltooidResult.Success>(secondResult)
        assertFalse(
            (
                bestandsDeelService.markVoltooid(
                    thirdUuid,
                    eio.bestandsdelen[2].lock,
                ) as MarkVoltooidResult.Success
                ).response.voltooid.not(),
        )
        // Calling markVoltooid on already-voltooid part should still succeed (idempotent)
        val repeat = bestandsDeelService.markVoltooid(uuid, firstPart.lock)
        assertIs<MarkVoltooidResult.Success>(repeat)
        assertTrue(repeat.response.voltooid)
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private companion object {
        const val GB = 1024L * 1024 * 1024

        fun testConfig(triggerSize: Long = 4L * GB, chunkSize: Long = 3L * GB): BestandsDeelConfig = object : BestandsDeelConfig() {
            override val triggerSizeBytes = triggerSize
            override val chunkSizeBytes = chunkSize
        }
    }
}
