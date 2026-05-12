// SPDX-License-Identifier: EUPL-1.2
// Copyright (C) 2026 Gemeente Utrecht
package com.baseflow.api.wopi.wopi

import com.baseflow.api.wopi.models.WopiDeleteResult
import com.baseflow.api.wopi.models.WopiLockPayload
import com.baseflow.api.wopi.models.WopiLockResult
import com.baseflow.api.wopi.models.WopiPutFileResult
import com.baseflow.api.wopi.models.WopiRenameResult
import com.baseflow.api.wopi.models.WopiUnlockResult
import com.baseflow.config.RequestScope
import com.baseflow.entities.EIORecordEntity
import com.baseflow.entities.latestVersion
import com.baseflow.services.EnkelvoudigInformatieObjectService
import com.baseflow.services.StorageService
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.koin.core.annotation.Scope
import org.koin.core.annotation.Scoped
import java.io.OutputStream
import java.util.UUID

/**
 * Service for WOPI-specific document operations.
 *
 * Handles locking, unlocking, file content updates, renames, and file version retrieval
 * as defined by the WOPI protocol. Delegates general EIO reads and writes to
 * [EnkelvoudigInformatieObjectService].
 */
@Scope(RequestScope::class)
@Scoped
class WopiDocumentService(private val eioService: EnkelvoudigInformatieObjectService, private val storageService: StorageService) {

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

    /**
     * Deletes a file per WOPI DeleteFile spec.
     * If the file is locked, returns [WopiDeleteResult.Locked] — the caller must respond 409
     * with the current lock token without deleting the file.
     */
    fun wopiDeleteFile(id: UUID): WopiDeleteResult {
        return transaction {
            val record = EIORecordEntity.findById(id) ?: return@transaction WopiDeleteResult.NotFound
            val currentLock = record.lockToken
            if (!currentLock.isNullOrEmpty()) {
                return@transaction WopiDeleteResult.Locked(currentLock)
            }
            record.delete()
            WopiDeleteResult.Success
        }
    }
}

/** Projection of the latest [com.baseflow.entities.EIOVersionEntity] needed for WOPI file streaming. */
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
