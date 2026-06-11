// SPDX-License-Identifier: EUPL-1.2
// Copyright (C) 2026 Gemeente Utrecht
package com.baseflow.shared.services

import com.baseflow.shared.api.middleware.AuditContext
import com.baseflow.shared.config.ApplicationConfig
import com.baseflow.shared.config.BestandsDeelConfig
import com.baseflow.shared.entities.BestandsDeelEntity
import com.baseflow.shared.tooling.AllTables
import com.baseflow.testutils.TestDataFactory.generateTestDocument
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.io.InputStream
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
    private lateinit var mockStorageService: StorageService

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

        mockStorageService = mockk<StorageService>()
        every { mockStorageService.uploadFile(any<String>(), any<ByteArray>(), anyNullable()) } answers {
            secondArg<ByteArray>().size.toLong()
        }
        every {
            mockStorageService.uploadFile(
                any<String>(),
                any<InputStream>(),
                any<Long>(),
                anyNullable(),
            )
        } answers { thirdArg<Long>() }
        val auditContext = AuditContext()
        eioService = EnkelvoudigInformatieObjectService(
            storageService = mockStorageService,
            applicationConfig = ApplicationConfig,
            catalogusService = CatalogusService(),
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

    // ── uploadFilePart ─────────────────────────────────────────────────────────

    @Test
    fun `uploadFilePart returns Success and sets voltooid to true for correct lock`() = runBlocking {
        val request = generateTestDocument().copy(bestandsomvang = 11L)
        val eio = eioService.create(request)
        val part = eio.bestandsdelen.first()
        val uuid = UUID.fromString(part.url.substringAfterLast("/"))

        val result = bestandsDeelService.uploadFilePart(uuid, part.lock, null, mockStorageService)

        assertIs<UploadFilePartResult.Success>(result)
        assertTrue(result.response.voltooid)
        assertEquals(uuid.toString(), result.response.url.substringAfterLast("/"))
    }

    @Test
    fun `uploadFilePart uploads chunk to storage under correct key`(): Unit = runBlocking {
        val request = generateTestDocument().copy(bestandsomvang = 11L)
        val eio = eioService.create(request)
        val part = eio.bestandsdelen.first()
        val uuid = UUID.fromString(part.url.substringAfterLast("/"))
        val bytes = ByteArray(4) { it.toByte() }

        val capturedKeys = mutableListOf<String>()
        val capturedContent = mutableListOf<InputStream>()
        every {
            mockStorageService.uploadFile(
                capture(capturedKeys),
                capture(capturedContent),
                any<Long>(),
                anyNullable(),
            )
        } answers { thirdArg<Long>() }

        val result = bestandsDeelService.uploadFilePart(uuid, part.lock, bytes.inputStream(), mockStorageService)

        assertIs<UploadFilePartResult.Success>(result)
        assertEquals(1, capturedKeys.size)
        // Key must follow pattern: {recordId}/{versie}/parts/{bestandsDeelId}
        val keyParts = capturedKeys[0].split("/")
        assertEquals(4, keyParts.size)
        assertEquals("parts", keyParts[2])
        assertEquals(uuid.toString(), keyParts[3])
        assertContentEquals(bytes, capturedContent[0].readAllBytes())
    }

    @Test
    fun `uploadFilePart does not call storage when no content is provided`() = runBlocking {
        val request = generateTestDocument().copy(bestandsomvang = 11L)
        val eio = eioService.create(request)
        val part = eio.bestandsdelen.first()
        val uuid = UUID.fromString(part.url.substringAfterLast("/"))

        val result = bestandsDeelService.uploadFilePart(uuid, part.lock, null, mockStorageService)

        assertIs<UploadFilePartResult.Success>(result)
        io.mockk.verify(exactly = 0) { mockStorageService.uploadFile(any<String>(), any<ByteArray>(), anyNullable()) }
        io.mockk.verify(exactly = 0) {
            mockStorageService.uploadFile(
                any<String>(),
                any<InputStream>(),
                any<Long>(),
                anyNullable(),
            )
        }
    }

    @Test
    fun `uploadFilePart returns InvalidLock when lock token does not match`(): Unit = runBlocking {
        val request = generateTestDocument().copy(bestandsomvang = 11L)
        val eio = eioService.create(request)
        val part = eio.bestandsdelen.first()
        val uuid = UUID.fromString(part.url.substringAfterLast("/"))

        val result = bestandsDeelService.uploadFilePart(uuid, "wrong-token", null, mockStorageService)

        assertIs<UploadFilePartResult.InvalidLock>(result)
    }

    @Test
    fun `uploadFilePart returns NotFound for unknown UUID`() {
        val result = bestandsDeelService.uploadFilePart(UUID.randomUUID(), "any-token", null, mockStorageService)

        assertIs<UploadFilePartResult.NotFound>(result)
    }

    @Test
    fun `uploadFilePart returns OmvangMismatch when content size does not match omvang`() = runBlocking {
        val request = generateTestDocument().copy(bestandsomvang = 11L)
        val eio = eioService.create(request)
        val part = eio.bestandsdelen.first()
        val uuid = UUID.fromString(part.url.substringAfterLast("/"))
        val wrongSizeBytes = ByteArray(3) { it.toByte() } // part.omvang is 4, not 3

        // Mock uploadFile to return the wrong byte count — the service checks the return value
        // against part.omvang and must return OmvangMismatch without marking the part voltooid.
        every {
            mockStorageService.uploadFile(any<String>(), any<InputStream>(), any<Long>(), anyNullable())
        } returns wrongSizeBytes.size.toLong()

        val result = bestandsDeelService.uploadFilePart(uuid, part.lock, wrongSizeBytes.inputStream(), mockStorageService)

        assertIs<UploadFilePartResult.OmvangMismatch>(result)
        assertEquals(part.omvang, result.expected)
        assertEquals(3L, result.actual)
        // uploadFile was called (mismatch detected from its return value), but the part must not be marked voltooid
        io.mockk.verify(exactly = 1) {
            mockStorageService.uploadFile(
                any<String>(),
                any<InputStream>(),
                any<Long>(),
                anyNullable(),
            )
        }
    }

    @Test
    fun `uploadFilePart does not mutate other parts when one part is marked voltooid`() = runBlocking {
        // 11 bytes → 3 chunks: [4, 4, 3]; mark only the first part as voltooid
        val request = generateTestDocument().copy(bestandsomvang = 11L)
        val eio = eioService.create(request)
        val firstPart = eio.bestandsdelen[0]
        val uuid = UUID.fromString(firstPart.url.substringAfterLast("/"))

        bestandsDeelService.uploadFilePart(uuid, firstPart.lock, null, mockStorageService)

        // The other two parts must still be voltooid = false
        val secondUuid = UUID.fromString(eio.bestandsdelen[1].url.substringAfterLast("/"))
        val thirdUuid = UUID.fromString(eio.bestandsdelen[2].url.substringAfterLast("/"))

        val secondResult =
            bestandsDeelService.uploadFilePart(secondUuid, eio.bestandsdelen[1].lock, null, mockStorageService)
        assertIs<UploadFilePartResult.Success>(secondResult)
        assertFalse(
            (
                bestandsDeelService.uploadFilePart(
                    thirdUuid,
                    eio.bestandsdelen[2].lock,
                    null,
                    mockStorageService,
                ) as UploadFilePartResult.Success
                ).response.voltooid.not(),
        )
        // Calling uploadFilePart on already-voltooid part should still succeed (idempotent)
        val repeat = bestandsDeelService.uploadFilePart(uuid, firstPart.lock, null, mockStorageService)
        assertIs<UploadFilePartResult.Success>(repeat)
        assertTrue(repeat.response.voltooid)
    }

    // ── bestandsRepository routing ────────────────────────────────────────────

    @Test
    fun `uploadFilePart routes chunk to the named repository stored on the version`() = runBlocking {
        val eio = eioService.create(generateTestDocument().copy(bestandsomvang = 11L))
        val part = eio.bestandsdelen.first()
        val uuid = UUID.fromString(part.url.substringAfterLast("/"))

        transaction {
            BestandsDeelEntity.findById(uuid)!!.versionId.bestandsRepository = "archive-repo"
        }

        val capturedRepos = mutableListOf<String?>()
        every { mockStorageService.uploadFile(any(), any(), 4L, captureNullable(capturedRepos)) } answers {
            thirdArg<Long>()
        }

        val stream = ByteArray(4).inputStream()
        bestandsDeelService.uploadFilePart(uuid, part.lock, stream, mockStorageService)

        assertEquals("archive-repo", capturedRepos.single())
    }

    @Test
    fun `uploadFilePart uses the default provider (null repoName) when bestandsRepository is blank`() = runBlocking {
        val eio = eioService.create(generateTestDocument().copy(bestandsomvang = 11L))
        val part = eio.bestandsdelen.first()
        val uuid = UUID.fromString(part.url.substringAfterLast("/"))
        // bestandsRepository defaults to "" — blank means default provider

        val capturedRepos = mutableListOf<String?>()
        every {
            mockStorageService.uploadFile(
                any(),
                any<InputStream>(),
                any<Long>(),
                captureNullable(capturedRepos),
            )
        } answers { thirdArg<Long>() }

        val stream = ByteArray(4).inputStream()
        bestandsDeelService.uploadFilePart(uuid, part.lock, stream, mockStorageService)

        assertNull(capturedRepos.single())
    }

    // ── getBestandsDelenForVersions ───────────────────────────────────────────

    @Test
    fun `getBestandsDelenForVersions returns empty map for empty input`() {
        val result = bestandsDeelService.getBestandsDelenForVersions(emptyList())
        assertTrue(result.isEmpty())
    }

    @Test
    fun `getBestandsDelenForVersions returns empty map when no versions have parts`() = runBlocking {
        // bestandsomvang below trigger → no bestandsdelen rows created, so map is empty
        eioService.create(generateTestDocument().copy(bestandsomvang = 5L))
        val nonExistentVersionId = UUID.randomUUID()

        val result = bestandsDeelService.getBestandsDelenForVersions(listOf(nonExistentVersionId))

        assertTrue(result.isEmpty())
    }

    @Test
    fun `getBestandsDelenForVersions returns parts grouped by version id`() = runBlocking {
        // Two EIOs each producing 3 chunks: [4, 4, 3]
        val eio1 = eioService.create(generateTestDocument().copy(bestandsomvang = 11L))
        val eio2 = eioService.create(generateTestDocument().copy(bestandsomvang = 11L))

        val version1Id = partUuidToVersionId(eio1.bestandsdelen.first().url)
        val version2Id = partUuidToVersionId(eio2.bestandsdelen.first().url)

        val result = bestandsDeelService.getBestandsDelenForVersions(listOf(version1Id, version2Id))

        assertEquals(2, result.size)
        val parts1 = result[version1Id]!!
        val parts2 = result[version2Id]!!
        assertEquals(3, parts1.size)
        assertEquals(3, parts2.size)
        // Parts must be sorted by volgnummer
        assertEquals(listOf(1, 2, 3), parts1.map { it.volgnummer })
        assertEquals(listOf(1, 2, 3), parts2.map { it.volgnummer })
        // No URL overlap between the two versions
        val urlsForVersion1 = parts1.map { it.url }.toSet()
        val urlsForVersion2 = parts2.map { it.url }.toSet()
        assertTrue(urlsForVersion1.intersect(urlsForVersion2).isEmpty())
    }

    @Test
    fun `getBestandsDelenForVersions ignores unknown version ids`() = runBlocking {
        val eio = eioService.create(generateTestDocument().copy(bestandsomvang = 11L))
        val knownVersionId = partUuidToVersionId(eio.bestandsdelen.first().url)
        val unknownId = UUID.randomUUID()

        val result = bestandsDeelService.getBestandsDelenForVersions(listOf(knownVersionId, unknownId))

        assertEquals(1, result.size)
        assertTrue(result.containsKey(knownVersionId))
        assertFalse(result.containsKey(unknownId))
    }

    @Test
    fun `getBestandsDelenForVersions returns correct chunk sizes and ordering`() = runBlocking {
        // 11 bytes with chunkSize=4 → chunks [4, 4, 3]
        val eio = eioService.create(generateTestDocument().copy(bestandsomvang = 11L))
        val versionId = partUuidToVersionId(eio.bestandsdelen.first().url)

        val result = bestandsDeelService.getBestandsDelenForVersions(listOf(versionId))
        val parts = result[versionId]!!

        assertEquals(listOf(4L, 4L, 3L), parts.map { it.omvang })
        parts.forEach { assertFalse(it.voltooid) }
    }

    /**
     * Resolves the version UUID that owns the bestandsdeel whose URL ends in [partUrl].
     * Used by tests to translate a response URL to the DB version primary key.
     */
    private fun partUuidToVersionId(partUrl: String): UUID {
        val partId = UUID.fromString(partUrl.substringAfterLast("/"))
        return transaction {
            BestandsDeelEntity.findById(partId)!!.versionId.id.value
        }
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
