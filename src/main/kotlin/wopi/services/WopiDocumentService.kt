// SPDX-License-Identifier: EUPL-1.2
// Copyright (C) 2026 Gemeente Utrecht
package com.baseflow.wopi.services

import com.baseflow.shared.entities.EIORecordEntity
import com.baseflow.shared.entities.EIOVersionEntity
import com.baseflow.shared.entities.OIORecords
import com.baseflow.shared.entities.latestVersion
import com.baseflow.shared.services.EnkelvoudigInformatieObjectService
import com.baseflow.shared.services.IntegrityCalculationService
import com.baseflow.shared.services.StorageService
import com.baseflow.wopi.api.models.*
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.andWhere
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.slf4j.LoggerFactory
import java.io.InputStream
import java.io.OutputStream
import java.util.*
import kotlin.time.Clock

/**
 * Service for WOPI-specific document operations.
 *
 * Handles locking, unlocking, file content updates, renames, and file version retrieval
 * as defined by the WOPI protocol. Delegates general EIO reads and writes to
 * [EnkelvoudigInformatieObjectService].
 */
open class WopiDocumentService(private val eioService: EnkelvoudigInformatieObjectService, private val storageService: StorageService) {
    private val logger = LoggerFactory.getLogger(WopiDocumentService::class.java)

    /**
     * Locks a file for a WOPI client using [wopiClientLock] as the lock token.
     * Returns null when no record with [id] exists.
     */
    fun wopiLock(id: UUID, wopiClientLock: String): WopiLockResult? {
        return transaction {
            val record = EIORecordEntity.findById(id) ?: return@transaction null
            val storedLock = record.lockToken
            if (!storedLock.isNullOrEmpty()) {
                if (storedLock == wopiClientLock) {
                    return@transaction WopiLockResult.AlreadyLocked
                } else {
                    return@transaction WopiLockResult.LockMismatch(currentFileLock = WopiLockPayload(lock = storedLock))
                }
            }
            record.lockToken = wopiClientLock
            WopiLockResult.Success
        }
    }

    /**
     * Replaces an existing lock with a new one (WOPI UnlockAndRelock).
     * - If the file is not locked, returns [WopiUnlockAndRelockResult.NotLocked].
     * - If the stored lock does not match [oldLock], returns [WopiUnlockAndRelockResult.LockMismatch].
     * - On success, replaces the stored lock with [newLock] and returns [WopiUnlockAndRelockResult.Success].
     * Returns null when no record with [id] exists.
     */
    fun wopiUnlockAndRelock(id: UUID, oldLock: String, newLock: String): WopiUnlockAndRelockResult? {
        return transaction {
            val record = EIORecordEntity.findById(id) ?: return@transaction null
            val storedLock = record.lockToken
            if (storedLock.isNullOrEmpty()) {
                return@transaction WopiUnlockAndRelockResult.NotLocked
            }
            if (storedLock != oldLock) {
                return@transaction WopiUnlockAndRelockResult.LockMismatch(WopiLockPayload(lock = storedLock))
            }
            record.lockToken = newLock
            WopiUnlockAndRelockResult.Success
        }
    }

    /**
     * Unlocks a file for a WOPI client.
     * Returns null when no record with [id] exists.
     */
    fun wopiUnlock(id: UUID, wopiClientLock: String): WopiUnlockResult? {
        return transaction {
            val record = EIORecordEntity.findById(id) ?: return@transaction null
            val storedLock = record.lockToken ?: return@transaction WopiUnlockResult.NotLocked
            if (storedLock != wopiClientLock) {
                return@transaction WopiUnlockResult.LockMismatch(WopiLockPayload(lock = storedLock))
            }
            record.lockToken = null
            WopiUnlockResult.Success
        }
    }

    /**
     * Writes raw bytes to a file (WOPI PutFile), enforcing lock semantics.
     * - If no lock is supplied and the file already has content, returns [WopiPutFileResult.LockRequired].
     * - If a lock is supplied but does not match the stored lock, returns [WopiPutFileResult.LockMismatch].
     * - On success, creates a new version and returns [WopiPutFileResult.Success].
     */
    suspend fun wopiPutFile(id: UUID, bytes: ByteArray, lockValue: String?): WopiPutFileResult {
        val currentFile = eioService.getById(id) ?: return WopiPutFileResult.NotFound
        val lockMismatch: String? = when {
            lockValue == null && (currentFile.bestandsomvang ?: 0L) > 0L -> ""
            lockValue != null && lockValue != currentFile.lock -> currentFile.lock
            else -> null
        }

        if (lockMismatch != null) {
            return WopiPutFileResult.LockMismatch(lockMismatch)
        }
        val response = eioService.updateWithBytes(id = id, bytes = bytes) ?: return WopiPutFileResult.NotFound
        return WopiPutFileResult.Success(response)
    }

    /**
     * Renames a file for WOPI, enforcing lock semantics.
     * If the file is locked, [lockValue] must match the stored lock token.
     */
    fun wopiRenameFile(id: UUID, newFileName: String, lockValue: String?): WopiRenameResult {
        return transaction {
            val record = EIORecordEntity.findById(id) ?: return@transaction WopiRenameResult.NotFound
            val currentLock = record.lockToken
            if (!currentLock.isNullOrEmpty()) {
                if (lockValue.isNullOrEmpty() || lockValue != currentLock) {
                    return@transaction WopiRenameResult.LockMismatch(currentLock)
                }
            }
            val version = record.latestVersion() ?: return@transaction WopiRenameResult.NotFound
            version.bestandsnaam = newFileName
            WopiRenameResult.Success
        }
    }

    /**
     * Returns the data needed to stream the latest version of a file.
     * Returns null when the record or its latest version does not exist.
     */
    fun wopiGetFileVersion(id: UUID): WopiFileVersion? = transaction {
        val record = EIORecordEntity.findById(id) ?: return@transaction null
        val version = record.versions.maxByOrNull { it.versie } ?: return@transaction null
        WopiFileVersion(
            bestandsLocatie = version.bestandsLocatie,
            bestandsRepository = version.bestandsRepository.takeUnless { it.isBlank() },
            bestandsomvang = version.bestandsomvang ?: 0L,
            bestandsnaam = version.bestandsnaam,
            titel = version.titel,
            formaat = version.formaat,
            versie = version.versie,
            recordId = record.id.value,
        )
    }

    /** Streams the file bytes identified by [bestandsnaam] (storage object key) to [output]. */
    fun streamByBestandsnaam(bestandsnaam: String, output: OutputStream, repoName: String? = null) {
        storageService.downloadFileTo(bestandsnaam, output, repoName?.takeUnless { it.isBlank() }).join()
    }

    private data class SourceMeta(
        val bronOrganisatie: String,
        val informatieobjectType: String,
        val taal: String,
        val auteur: String,
        val creatieDatum: LocalDate,
        val bestandsRepository: String,
        val vertrouwlijkheidsAanduiding: String,
        val status: String,
        val beschrijving: String,
        val indicatieGebruiksrecht: Boolean,
    )

    /**
     * Creates a new EIO as a copy of [sourceId] with the provided [inputStream] and [targetFileName]
     * (WOPI PutRelativeFile).
     *
     * - Create a new EIO record cloned from the source metadata and return
     *   [WopiPutRelativeFileResult.Success].
     */
    fun wopiPutRelativeFile(
        sourceId: UUID,
        targetFileName: String,
        inputStream: InputStream,
        contentLength: Long,
    ): WopiPutRelativeFileResult {
        val sourceMeta = transaction {
            val record = EIORecordEntity.findById(sourceId) ?: return@transaction null
            val version = record.latestVersion() ?: return@transaction null
            SourceMeta(
                bronOrganisatie = version.bronOrganisatie,
                informatieobjectType = version.informatieobject_type,
                taal = version.taal,
                auteur = version.auteur,
                creatieDatum = version.creatieDatum,
                bestandsRepository = version.bestandsRepository,
                vertrouwlijkheidsAanduiding = version.vertrouwlijkheidsAanduiding,
                status = version.status,
                beschrijving = version.beschrijving,
                indicatieGebruiksrecht = version.indicatieGebruiksrecht,
            )
        } ?: return WopiPutRelativeFileResult.SourceNotFound

        val (fileType, readableStream) = StorageService.detectFileFormat(inputStream)

        val newId = UUID.randomUUID()
        val bestandsLocatie = "$newId/1/$targetFileName"
        val repoName = sourceMeta.bestandsRepository.takeUnless { it.isBlank() }

        // Upload first so we can compute the SHA-256 hash in a single streaming pass.
        // Always close the inputStream afterwards; on upload failure let the exception propagate.
        val integrityResult = inputStream.use {
            val (_, result) = IntegrityCalculationService.withIntegrity(readableStream, "SHA_256") { digestStream ->
                storageService.uploadFile(bestandsLocatie, digestStream, contentLength, repoName)
            }
            result
        }

        // Persist the DB record. On failure, clean up the orphaned blob so storage stays consistent.
        try {
            transaction {
                val newRecord = EIORecordEntity.new(newId) {}
                EIOVersionEntity.new {
                    recordId = newRecord
                    versie = 1
                    bronOrganisatie = sourceMeta.bronOrganisatie
                    informatieobject_type = sourceMeta.informatieobjectType
                    taal = sourceMeta.taal
                    bestandsnaam = targetFileName
                    titel = targetFileName
                    auteur = sourceMeta.auteur
                    creatieDatum = sourceMeta.creatieDatum
                    beginRegistratie = Clock.System.now()
                    formaat = fileType
                    this.bestandsomvang = contentLength
                    this.bestandsLocatie = bestandsLocatie
                    bestandsRepository = sourceMeta.bestandsRepository
                    vertrouwlijkheidsAanduiding = sourceMeta.vertrouwlijkheidsAanduiding
                    status = sourceMeta.status
                    beschrijving = sourceMeta.beschrijving
                    indicatieGebruiksrecht = sourceMeta.indicatieGebruiksrecht
                    integriteitAlgoritme = integrityResult.algorithm
                    integriteitWaarde = integrityResult.hash
                    integriteitsDatum = Clock.System.now().toLocalDateTime(TimeZone.UTC).date
                }
            }
        } catch (dbEx: Exception) {
            runCatching { storageService.deleteFiles(listOf(bestandsLocatie), repoName) }
                .onFailure { cleanupEx ->
                    logger.warn(
                        "wopiPutRelativeFile: DB persist failed for {} and blob cleanup also failed: {}",
                        bestandsLocatie,
                        cleanupEx.message,
                        cleanupEx,
                    )
                }
            throw dbEx
        }

        return WopiPutRelativeFileResult.Success(newId, targetFileName)
    }

    /**
     * Deletes a file per WOPI DeleteFile spec.
     * - If the file is locked, returns [WopiDeleteResult.Locked] without touching the file.
     * - If the EIO has attached ObjectInformatieObject relations, returns [WopiDeleteResult.HasReferences].
     * - On success, deletes all associated blobs from storage and returns [WopiDeleteResult.Success].
     */
    fun wopiDeleteFile(id: UUID): WopiDeleteResult {
        val fileLocationsByRepo = mutableMapOf<String, MutableSet<String>>()
        val result = transaction {
            val record = EIORecordEntity.findById(id) ?: return@transaction WopiDeleteResult.NotFound

            val currentLock = record.lockToken
            if (!currentLock.isNullOrEmpty()) {
                return@transaction WopiDeleteResult.Locked(currentLock)
            }

            val hasReferences = !OIORecords
                .selectAll()
                .andWhere { OIORecords.informatieobject eq id }
                .limit(1)
                .empty()
            if (hasReferences) {
                return@transaction WopiDeleteResult.HasReferences
            }

            record.versions.forEach { version ->
                if (version.bestandsLocatie.isNotBlank()) {
                    fileLocationsByRepo
                        .getOrPut(version.bestandsRepository) { mutableSetOf() }
                        .add(version.bestandsLocatie)
                }
            }

            record.delete()
            WopiDeleteResult.Success
        }
        if (result == WopiDeleteResult.Success) {
            fileLocationsByRepo.forEach { (repo, keys) ->
                storageService.deleteFiles(keys.toList(), repoName = repo.takeUnless { it.isBlank() })
            }
        }
        return result
    }
}

/** Projection of the latest [EIOVersionEntity] needed for WOPI file streaming. */
data class WopiFileVersion(
    val bestandsLocatie: String,
    val bestandsRepository: String?,
    val bestandsomvang: Long,
    val bestandsnaam: String,
    val titel: String,
    val formaat: String?,
    val versie: Int,
    val recordId: UUID,
)
