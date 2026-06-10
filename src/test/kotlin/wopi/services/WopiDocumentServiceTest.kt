// SPDX-License-Identifier: EUPL-1.2
// Copyright (C) 2026 Gemeente Utrecht
package wopi.services

import com.baseflow.shared.api.middleware.AuditContext
import com.baseflow.shared.config.ApplicationConfig
import com.baseflow.shared.config.OpenZaakConfig
import com.baseflow.shared.entities.EIORecordEntity
import com.baseflow.shared.entities.OIORecordEntity
import com.baseflow.shared.services.AuditTrailService
import com.baseflow.shared.services.BestandsDeelService
import com.baseflow.shared.services.BlobStorageRegistrar
import com.baseflow.shared.services.CatalogusService
import com.baseflow.shared.services.EnkelvoudigInformatieObjectService
import com.baseflow.shared.services.StorageService
import com.baseflow.shared.tooling.AllTables
import com.baseflow.testutils.TestDataFactory
import com.baseflow.wopi.api.models.WopiDeleteResult
import com.baseflow.wopi.api.models.WopiLockResult
import com.baseflow.wopi.api.models.WopiPutFileResult
import com.baseflow.wopi.api.models.WopiPutRelativeFileResult
import com.baseflow.wopi.api.models.WopiRenameResult
import com.baseflow.wopi.api.models.WopiUnlockAndRelockResult
import com.baseflow.wopi.api.models.WopiUnlockResult
import com.baseflow.wopi.services.WopiDocumentService
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.Base64
import java.util.UUID
import java.util.concurrent.CompletableFuture
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.time.Clock

class WopiDocumentServiceTest {

    private lateinit var service: WopiDocumentService
    private lateinit var eioService: EnkelvoudigInformatieObjectService
    private lateinit var mockStorageService: StorageService
    private lateinit var mockAuditTrailService: AuditTrailService

    @BeforeTest
    fun setup() {
        BlobStorageRegistrar.resetForTesting()
        Database.connect(
            "jdbc:h2:mem:test_wopi;DB_CLOSE_DELAY=-1;",
            driver = "org.h2.Driver",
            user = "root",
            password = "",
        )
        transaction { AllTables.createMissing() }

        mockStorageService = mockk<StorageService>()
        every { mockStorageService.uploadFile(any<String>(), any<ByteArray>(), anyNullable()) } answers
            { secondArg<ByteArray>().size.toLong() }
        every {
            mockStorageService.uploadFile(
                any<String>(),
                any<InputStream>(),
                any<Long>(),
                anyNullable(),
            )
        } answers {
            secondArg<InputStream>().copyTo(OutputStream.nullOutputStream())
            thirdArg<Long>()
        }
        every { mockStorageService.deleteFiles(any(), anyNullable()) } just Runs
        every {
            mockStorageService.downloadFileTo(
                any(),
                any(),
                anyNullable(),
            )
        } returns CompletableFuture.completedFuture(null)

        mockAuditTrailService = mockk<AuditTrailService>()
        every { mockAuditTrailService.removeAuditTrailsForResource(any()) } returns Unit

        eioService = EnkelvoudigInformatieObjectService(
            storageService = mockStorageService,
            applicationConfig = ApplicationConfig,
            catalogusService = CatalogusService(OpenZaakConfig(validationEnabled = false)),
            auditTrailService = mockAuditTrailService,
            auditContext = AuditContext(),
            bestandsDeelService = BestandsDeelService(),
        )
        service = WopiDocumentService(eioService, mockStorageService)
    }

    @AfterTest
    fun teardown() {
        BlobStorageRegistrar.resetForTesting()
        transaction { SchemaUtils.drop(*AllTables.tables.reversedArray()) }
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    private fun createEio(withContent: Boolean = true): UUID = runBlocking {
        val req = TestDataFactory.generateTestDocument(withContent = withContent)
        UUID.fromString(eioService.create(req).id)
    }

    /** Directly inserts an OIORecordEntity linked to the given EIO so we can test reference checks. */
    private fun attachOioToEio(eioId: UUID) = transaction {
        val record = EIORecordEntity.findById(eioId)!!
        val version = record.versions.maxByOrNull { it.versie }!!
        OIORecordEntity.new {
            informatieobject = record
            informatieobjectVersie = version
            subjectObject = "https://example.com/zaken/api/v1/zaken/${UUID.randomUUID()}"
            subjectType = "zaak"
            createdAt = Clock.System.now().toLocalDateTime(TimeZone.UTC)
        }
    }

    // ── wopiUnlockAndRelock ──────────────────────────────────────────────────────────────

    @Test
    fun `wopiUnlockAndRelock - returns null for unknown id`() {
        val result = service.wopiUnlockAndRelock(UUID.randomUUID(), "old-lock", "new-lock")
        assertNull(result)
    }

    @Test
    fun `wopiUnlockAndRelock - succeeds on locked file`() {
        val id = createEio()
        val lockResult = service.wopiLock(id, "old-lock")
        assertIs<WopiLockResult.Success>(lockResult)
        val result = service.wopiUnlockAndRelock(id, "old-lock", "new-lock")
        assertIs<WopiUnlockAndRelockResult.Success>(result)
    }

    @Test
    fun `wopiUnlockAndRelock - returns NotLocked when lock is missing for the file`() {
        val id = createEio()
        val result = service.wopiUnlockAndRelock(id, "old-lock", "new-lock")
        assertIs<WopiUnlockAndRelockResult.NotLocked>(result)
    }

    @Test
    fun `wopiUnlockAndRelock - returns LockMismatch when different lock is supplied`() {
        val id = createEio()
        service.wopiLock(id, "any-lock")
        val result = service.wopiUnlockAndRelock(id, "old-lock", "new-lock")
        assertIs<WopiUnlockAndRelockResult.LockMismatch>(result)
        assertEquals("any-lock", result.currentFileLock.lock)
    }

    // ── wopiLock ──────────────────────────────────────────────────────────────

    @Test
    fun `wopiLock - returns null for unknown id`() {
        val result = service.wopiLock(UUID.randomUUID(), "lock-1")
        assertNull(result)
    }

    @Test
    fun `wopiLock - succeeds on unlocked file`() {
        val id = createEio()
        val result = service.wopiLock(id, "lock-abc")
        assertIs<WopiLockResult.Success>(result)
    }

    @Test
    fun `wopiLock - returns AlreadyLocked when same lock is used again`() {
        val id = createEio()
        service.wopiLock(id, "lock-abc")
        val result = service.wopiLock(id, "lock-abc")
        assertIs<WopiLockResult.AlreadyLocked>(result)
    }

    @Test
    fun `wopiLock - returns LockMismatch when different lock is supplied`() {
        val id = createEio()
        service.wopiLock(id, "lock-abc")
        val result = service.wopiLock(id, "lock-xyz")
        assertIs<WopiLockResult.LockMismatch>(result)
        assertEquals("lock-abc", result.currentFileLock.lock)
    }

    // ── wopiUnlock ────────────────────────────────────────────────────────────

    @Test
    fun `wopiUnlock - returns null for unknown id`() {
        val result = service.wopiUnlock(UUID.randomUUID(), "lock-1")
        assertNull(result)
    }

    @Test
    fun `wopiUnlock - returns NotLocked when file is not locked`() {
        val id = createEio()
        val result = service.wopiUnlock(id, "any-lock")
        assertIs<WopiUnlockResult.NotLocked>(result)
    }

    @Test
    fun `wopiUnlock - returns LockMismatch when wrong lock is supplied`() {
        val id = createEio()
        service.wopiLock(id, "lock-abc")
        val result = service.wopiUnlock(id, "lock-wrong")
        assertIs<WopiUnlockResult.LockMismatch>(result)
        assertEquals("lock-abc", result.currentFileLock.lock)
    }

    @Test
    fun `wopiUnlock - succeeds with matching lock`() {
        val id = createEio()
        service.wopiLock(id, "lock-abc")
        val result = service.wopiUnlock(id, "lock-abc")
        assertIs<WopiUnlockResult.Success>(result)
    }

    @Test
    fun `wopiUnlock - file can be locked again after successful unlock`() {
        val id = createEio()
        service.wopiLock(id, "lock-abc")
        service.wopiUnlock(id, "lock-abc")
        val result = service.wopiLock(id, "lock-new")
        assertIs<WopiLockResult.Success>(result)
    }

    // ── wopiPutFile ───────────────────────────────────────────────────────────

    @Test
    fun `wopiPutFile - returns NotFound for unknown id`(): Unit = runBlocking {
        val result = service.wopiPutFile(UUID.randomUUID(), byteArrayOf(1, 2), null)
        assertIs<WopiPutFileResult.NotFound>(result)
    }

    @Test
    fun `wopiPutFile - returns LockMismatch when file has content and no lock supplied`() = runBlocking {
        val id = createEio(withContent = true)
        val result = service.wopiPutFile(id, byteArrayOf(1, 2), lockValue = null)
        assertIs<WopiPutFileResult.LockMismatch>(result)
        assertEquals("", result.currentLock)
    }

    @Test
    fun `wopiPutFile - returns LockMismatch when wrong lock is supplied`() = runBlocking {
        val id = createEio(withContent = true)
        service.wopiLock(id, "lock-abc")
        val result = service.wopiPutFile(id, byteArrayOf(1, 2), lockValue = "lock-wrong")
        assertIs<WopiPutFileResult.LockMismatch>(result)
        assertEquals("lock-abc", result.currentLock)
    }

    @Test
    fun `wopiPutFile - succeeds with matching lock`() = runBlocking {
        val id = createEio(withContent = true)
        service.wopiLock(id, "lock-abc")
        val bytes = TestDataFactory.PDF_CONTENT.let {
            Base64.getDecoder().decode(it)
        }
        val result = service.wopiPutFile(id, bytes, lockValue = "lock-abc")
        assertIs<WopiPutFileResult.Success>(result)
        assertEquals(bytes.size.toLong(), result.response.bestandsomvang)
    }

    @Test
    fun `wopiPutFile - increments version on success`() = runBlocking {
        val id = createEio(withContent = true)
        val before = eioService.getById(id)
        assertNotNull(before)
        service.wopiLock(id, "lock-abc")
        val bytes = Base64.getDecoder().decode(TestDataFactory.PDF_CONTENT)
        val result = service.wopiPutFile(id, bytes, lockValue = "lock-abc")
        assertIs<WopiPutFileResult.Success>(result)
        assertEquals(before.versie + 1, result.response.versie)
    }

    // ── wopiRenameFile ────────────────────────────────────────────────────────

    @Test
    fun `wopiRenameFile - returns NotFound for unknown id`() {
        val result = service.wopiRenameFile(UUID.randomUUID(), "new.docx", null)
        assertIs<WopiRenameResult.NotFound>(result)
    }

    @Test
    fun `wopiRenameFile - succeeds on unlocked file without lock`() {
        val id = createEio()
        val result = service.wopiRenameFile(id, "renamed.docx", lockValue = null)
        assertIs<WopiRenameResult.Success>(result)
    }

    @Test
    fun `wopiRenameFile - returns LockMismatch when file is locked and no lock supplied`() {
        val id = createEio()
        service.wopiLock(id, "lock-abc")
        val result = service.wopiRenameFile(id, "renamed.docx", lockValue = null)
        assertIs<WopiRenameResult.LockMismatch>(result)
        assertEquals("lock-abc", result.currentLock)
    }

    @Test
    fun `wopiRenameFile - returns LockMismatch when wrong lock supplied`() {
        val id = createEio()
        service.wopiLock(id, "lock-abc")
        val result = service.wopiRenameFile(id, "renamed.docx", lockValue = "lock-wrong")
        assertIs<WopiRenameResult.LockMismatch>(result)
        assertEquals("lock-abc", result.currentLock)
    }

    @Test
    fun `wopiRenameFile - succeeds with matching lock`() {
        val id = createEio()
        service.wopiLock(id, "lock-abc")
        val result = service.wopiRenameFile(id, "renamed.docx", lockValue = "lock-abc")
        assertIs<WopiRenameResult.Success>(result)
    }

    @Test
    fun `wopiRenameFile - persists new filename`() = runBlocking {
        val id = createEio()
        service.wopiRenameFile(id, "new-name.docx", lockValue = null)
        val eio = eioService.getById(id)
        assertEquals("new-name.docx", eio?.bestandsnaam)
    }

    // ── wopiGetFileVersion ────────────────────────────────────────────────────

    @Test
    fun `wopiGetFileVersion - returns null for unknown id`() {
        val result = service.wopiGetFileVersion(UUID.randomUUID())
        assertNull(result)
    }

    @Test
    fun `wopiGetFileVersion - returns correct version details`() = runBlocking {
        val req = TestDataFactory.generateTestDocument(bestandsnaam = "test.pdf", withContent = true)
        val created = eioService.create(req)
        val id = UUID.fromString(created.id)

        val result = service.wopiGetFileVersion(id)
        assertNotNull(result)
        assertEquals("test.pdf", result.bestandsnaam)
        assertEquals(1, result.versie)
        assertEquals(id, result.recordId)
    }

    @Test
    fun `wopiGetFileVersion - returns latest version after update`() = runBlocking {
        val id = createEio(withContent = true)
        service.wopiLock(id, "lock-abc")
        val bytes = Base64.getDecoder().decode(TestDataFactory.PDF_CONTENT_ALT)
        service.wopiPutFile(id, bytes, lockValue = "lock-abc")

        val result = service.wopiGetFileVersion(id)
        assertNotNull(result)
        assertEquals(2, result.versie)
    }

    // ── streamByBestandsnaam ──────────────────────────────────────────────────

    @Test
    fun `streamByBestandsnaam - delegates to storage service`() {
        val output = ByteArrayOutputStream()
        service.streamByBestandsnaam("some/path/file.pdf", output, repoName = null)
        // mockStorageService.downloadFileTo verified as called — io.mockk will fail if not invoked
        verify { mockStorageService.downloadFileTo("some/path/file.pdf", output, null) }
    }

    // ── wopiDeleteFile ────────────────────────────────────────────────────────

    @Test
    fun `wopiDeleteFile - returns NotFound for unknown id`() {
        val result = service.wopiDeleteFile(UUID.randomUUID())
        assertIs<WopiDeleteResult.NotFound>(result)
    }

    @Test
    fun `wopiDeleteFile - does not call deleteFiles when not found`() {
        service.wopiDeleteFile(UUID.randomUUID())
        verify(exactly = 0) { mockStorageService.deleteFiles(any(), anyNullable()) }
    }

    @Test
    fun `wopiDeleteFile - returns Locked when file is locked`() {
        val id = createEio()
        service.wopiLock(id, "lock-abc")
        val result = service.wopiDeleteFile(id)
        assertIs<WopiDeleteResult.Locked>(result)
        assertEquals("lock-abc", result.currentLock)
    }

    @Test
    fun `wopiDeleteFile - does not delete file when locked`(): Unit = runBlocking {
        val id = createEio()
        service.wopiLock(id, "lock-abc")
        service.wopiDeleteFile(id)
        assertNotNull(eioService.getById(id))
    }

    @Test
    fun `wopiDeleteFile - does not call deleteFiles when locked`() {
        val id = createEio()
        service.wopiLock(id, "lock-abc")
        service.wopiDeleteFile(id)
        verify(exactly = 0) { mockStorageService.deleteFiles(any(), anyNullable()) }
    }

    @Test
    fun `wopiDeleteFile - returns HasReferences when EIO has attached OIO relations`() {
        val id = createEio()
        attachOioToEio(id)
        val result = service.wopiDeleteFile(id)
        assertIs<WopiDeleteResult.HasReferences>(result)
    }

    @Test
    fun `wopiDeleteFile - does not delete file when OIO references exist`(): Unit = runBlocking {
        val id = createEio()
        attachOioToEio(id)
        service.wopiDeleteFile(id)
        assertNotNull(eioService.getById(id))
    }

    @Test
    fun `wopiDeleteFile - does not call deleteFiles when OIO references exist`() {
        val id = createEio()
        attachOioToEio(id)
        service.wopiDeleteFile(id)
        verify(exactly = 0) { mockStorageService.deleteFiles(any(), anyNullable()) }
    }

    @Test
    fun `wopiDeleteFile - succeeds on unlocked file without references`() {
        val id = createEio()
        val result = service.wopiDeleteFile(id)
        assertIs<WopiDeleteResult.Success>(result)
    }

    @Test
    fun `wopiDeleteFile - file no longer exists after successful delete`() = runBlocking {
        val id = createEio()
        service.wopiDeleteFile(id)
        assertNull(eioService.getById(id))
    }

    @Test
    fun `wopiDeleteFile - calls deleteFiles with blob key on success`() = runBlocking {
        val created = eioService.create(TestDataFactory.generateTestDocument(withContent = true))
        val id = UUID.fromString(created.id)

        service.wopiDeleteFile(id)

        verify { mockStorageService.deleteFiles(match { it.contains("$id/1/test.pdf") }, null) }
    }

    @Test
    fun `wopiDeleteFile - does not call deleteFiles for EIO without blob content`() {
        val id = createEio(withContent = false)
        service.wopiDeleteFile(id)
        verify(exactly = 0) { mockStorageService.deleteFiles(any(), anyNullable()) }
    }

    @Test
    fun `wopiDeleteFile - calls deleteFiles for each distinct bestandsRepository`() = runBlocking {
        val created = eioService.create(TestDataFactory.generateTestDocument(withContent = true))
        val id = UUID.fromString(created.id)

        // Manually set the version to use a named repo so we can assert on the repo name
        transaction {
            EIORecordEntity.findById(id)!!.versions.maxByOrNull { it.versie }!!.bestandsRepository = "repo-a"
        }

        service.wopiDeleteFile(id)

        verify { mockStorageService.deleteFiles(any(), "repo-a") }
        verify(exactly = 1) { mockStorageService.deleteFiles(any(), anyNullable()) }
    }

    @Test
    fun `wopiDeleteFile - succeeds after unlocking a previously locked file`() {
        val id = createEio()
        service.wopiLock(id, "lock-abc")
        service.wopiUnlock(id, "lock-abc")
        val result = service.wopiDeleteFile(id)
        assertIs<WopiDeleteResult.Success>(result)
    }

    // ── wopiPutRelativeFile ───────────────────────────────────────────────────

    @Test
    fun `wopiPutRelativeFile - returns SourceNotFound for unknown source id`() {
        val bytes = Base64.getDecoder().decode(TestDataFactory.PDF_CONTENT)
        val result =
            service.wopiPutRelativeFile(UUID.randomUUID(), "copy.pdf", bytes.inputStream(), bytes.size.toLong())
        assertIs<WopiPutRelativeFileResult.SourceNotFound>(result)
    }

    @Test
    fun `wopiPutRelativeFile - returns Success with the target file name`() = runBlocking {
        val sourceId = createEio(withContent = true)
        val bytes = Base64.getDecoder().decode(TestDataFactory.PDF_CONTENT)

        val result = service.wopiPutRelativeFile(sourceId, "copy.pdf", bytes.inputStream(), bytes.size.toLong())

        assertIs<WopiPutRelativeFileResult.Success>(result)
        assertEquals("copy.pdf", result.resolvedName)
    }

    @Test
    fun `wopiPutRelativeFile - creates a new distinct EIO record`() = runBlocking {
        val sourceId = createEio(withContent = true)
        val bytes = Base64.getDecoder().decode(TestDataFactory.PDF_CONTENT)

        val result = service.wopiPutRelativeFile(sourceId, "copy.pdf", bytes.inputStream(), bytes.size.toLong())

        assertIs<WopiPutRelativeFileResult.Success>(result)
        assertNotNull(eioService.getById(result.fileId))
        assert(result.fileId != sourceId) { "New EIO should have a different UUID than the source" }
    }

    @Test
    fun `wopiPutRelativeFile - new EIO inherits source metadata`() = runBlocking {
        val req = TestDataFactory.generateTestDocument(taal = "nld", auteur = "TestAuteur", withContent = true)
        val sourceId = UUID.fromString(eioService.create(req).id)
        val bytes = Base64.getDecoder().decode(TestDataFactory.PDF_CONTENT)

        val result = service.wopiPutRelativeFile(sourceId, "copy.pdf", bytes.inputStream(), bytes.size.toLong())

        assertIs<WopiPutRelativeFileResult.Success>(result)
        val newEio = eioService.getById(result.fileId)
        assertNotNull(newEio)
        assertEquals("nld", newEio.taal)
        assertEquals("TestAuteur", newEio.auteur)
        assertEquals(req.bronorganisatie, newEio.bronorganisatie)
        assertEquals(req.informatieobjecttype, newEio.informatieobjecttype)
    }

    @Test
    fun `wopiPutRelativeFile - new EIO has version 1`() = runBlocking {
        val sourceId = createEio(withContent = true)
        val bytes = Base64.getDecoder().decode(TestDataFactory.PDF_CONTENT)

        val result = service.wopiPutRelativeFile(sourceId, "copy.pdf", bytes.inputStream(), bytes.size.toLong())

        assertIs<WopiPutRelativeFileResult.Success>(result)
        val newEio = eioService.getById(result.fileId)
        assertNotNull(newEio)
        assertEquals(1, newEio.versie)
    }

    @Test
    fun `wopiPutRelativeFile - new EIO has correct bestandsnaam and bestandsomvang`() = runBlocking {
        val sourceId = createEio(withContent = true)
        val bytes = Base64.getDecoder().decode(TestDataFactory.PDF_CONTENT)

        val result = service.wopiPutRelativeFile(sourceId, "renamed.pdf", bytes.inputStream(), bytes.size.toLong())

        assertIs<WopiPutRelativeFileResult.Success>(result)
        val newEio = eioService.getById(result.fileId)
        assertNotNull(newEio)
        assertEquals("renamed.pdf", newEio.bestandsnaam)
        assertEquals(bytes.size.toLong(), newEio.bestandsomvang)
    }

    @Test
    fun `wopiPutRelativeFile - uploads bytes to storage service`() = runBlocking {
        val sourceId = createEio(withContent = true)
        val bytes = Base64.getDecoder().decode(TestDataFactory.PDF_CONTENT)

        val result = service.wopiPutRelativeFile(sourceId, "upload.pdf", bytes.inputStream(), bytes.size.toLong())

        assertIs<WopiPutRelativeFileResult.Success>(result)
        verify {
            mockStorageService.uploadFile(
                match { it.contains("upload.pdf") },
                any<InputStream>(),
                any<Long>(),
                anyNullable(),
            )
        }
    }

    @Test
    fun `wopiPutRelativeFile - source EIO is not modified`() = runBlocking {
        val sourceId = createEio(withContent = true)
        val sourceBefore = eioService.getById(sourceId)
        assertNotNull(sourceBefore)
        val bytes = Base64.getDecoder().decode(TestDataFactory.PDF_CONTENT)

        service.wopiPutRelativeFile(sourceId, "copy.pdf", bytes.inputStream(), bytes.size.toLong())

        val sourceAfter = eioService.getById(sourceId)
        assertNotNull(sourceAfter)
        assertEquals(sourceBefore.versie, sourceAfter.versie)
        assertEquals(sourceBefore.bestandsnaam, sourceAfter.bestandsnaam)
    }
}
