// SPDX-License-Identifier: EUPL-1.2
// Copyright (C) 2026 Gemeente Utrecht
package com.baseflow.services

import com.baseflow.api.ApiUrlBuilder
import com.baseflow.api.models.BestandsDeelResponse
import com.baseflow.config.BestandsDeelConfig
import com.baseflow.entities.BestandsDeelEntity
import com.baseflow.entities.BestandsDelen
import com.baseflow.entities.EIOVersionEntity
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.io.InputStream
import java.util.UUID

/** S3 object key for a bestandsdeel chunk. */
internal fun bestandsDeelStorageKey(recordId: UUID, versie: Int, bestandsDeelId: UUID): String = "$recordId/$versie/parts/$bestandsDeelId"

/**
 * Service responsible for managing bestandsdelen (file parts) used in the
 * chunked-upload workflow for large files.
 *
 * When an [EIOVersionEntity] is created with a `bestandsomvang` that exceeds
 * [BestandsDeelConfig.triggerSizeBytes], this service creates the corresponding
 * set of [BestandsDeelEntity] rows and returns them as [BestandsDeelResponse] objects.
 *
 * The upload of each individual part is handled by the `PUT /bestandsdelen/{uuid}` endpoint.
 */
class BestandsDeelService(private val config: BestandsDeelConfig = BestandsDeelConfig.Default) {

    /**
     * Returns true when [bestandsomvang] exceeds the configured trigger size.
     */
    fun requiresChunking(bestandsomvang: Long?): Boolean = bestandsomvang != null && bestandsomvang > config.triggerSizeBytes

    /**
     * Creates [BestandsDeelEntity] rows for [version] based on [bestandsomvang] and
     * returns the corresponding response objects.
     *
     * The caller is responsible for calling this inside a database transaction.
     *
     * @param version    The [EIOVersionEntity] for which parts must be created.
     * @param bestandsomvang Total byte size of the file.
     * @param lockToken  The lock token of the parent EIORecord (stored on each part).
     * @return List of [BestandsDeelResponse] ordered by [BestandsDeelEntity.volgnummer].
     */
    fun createBestandsDelen(version: EIOVersionEntity, bestandsomvang: Long, lockToken: String): List<BestandsDeelResponse> {
        val chunks = splitIntoChunks(bestandsomvang)
        return chunks.mapIndexed { index, chunkSize ->
            val part = BestandsDeelEntity.new {
                versionId = version
                volgnummer = index + 1
                omvang = chunkSize
                voltooid = false
                lock = lockToken
            }
            part.toResponse()
        }
    }

    /**
     * Retrieves all bestandsdelen for an [EIOVersionEntity] as response objects.
     */
    fun getBestandsDelen(version: EIOVersionEntity): List<BestandsDeelResponse> = transaction {
        BestandsDeelEntity
            .find { BestandsDelen.versionId eq version.id }
            .sortedBy { it.volgnummer }
            .map { it.toResponse() }
    }

    /**
     * Retrieves bestandsdelen for a batch of version IDs in a single
     * `WHERE version_id IN (...)` query, then groups the results in-memory.
     *
     * Use this instead of calling [getBestandsDelen] in a loop to avoid the
     * N+1 query pattern on list endpoints.
     *
     * @return A map from version UUID to its (sorted) list of [BestandsDeelResponse] objects.
     *         Versions that have no parts are absent from the map.
     */
    fun getBestandsDelenForVersions(versionIds: List<UUID>): Map<UUID, List<BestandsDeelResponse>> {
        if (versionIds.isEmpty()) return emptyMap()
        return transaction {
            BestandsDeelEntity
                .find { BestandsDelen.versionId inList versionIds }
                .groupBy { it.versionId.id.value }
                .mapValues { (_, parts) ->
                    parts.sortedBy { it.volgnummer }.map { it.toResponse() }
                }
        }
    }

    /**
     * Uploads a file chunk to S3 and marks the bestandsdeel as completed (voltooid = true).
     *
     * The chunk is stored under the key `{recordId}/{versie}/parts/{bestandsDeelId}` so it
     * can later be reassembled in the correct order when the parent EIO is unlocked.
     *
     * @param id          UUID of the [BestandsDeelEntity] to update.
     * @param lockToken   Lock token that must match the one stored on the part.
     * @param inputStream     Raw bytes of the uploaded chunk (may be empty/null if no file was sent).
     * @param storageService  Service used to persist the chunk in S3.
     * @return [UploadFilePartResult.Success], [UploadFilePartResult.NotFound], [UploadFilePartResult.InvalidLock]
     *         or [UploadFilePartResult.OmvangMismatch].
     */
    fun uploadFilePart(id: UUID, lockToken: String, inputStream: InputStream?, storageService: StorageService): UploadFilePartResult =
        transaction {
            val part = BestandsDeelEntity.findById(id) ?: return@transaction UploadFilePartResult.NotFound
            if (part.lock != lockToken) return@transaction UploadFilePartResult.InvalidLock

            if (inputStream != null && inputStream.available() > 0) {
                // Pre-validate size using available() — reliable for ByteArrayInputStream
                // and HTTP streams where the Ktor engine sets the limit to Content-Length.
                val reported = inputStream.available().toLong()
                if (reported != part.omvang) {
                    return@transaction UploadFilePartResult.OmvangMismatch(expected = part.omvang, actual = reported)
                }

                val version = part.versionId
                val storageKey = bestandsDeelStorageKey(
                    recordId = version.recordId.id.value,
                    versie = version.versie,
                    bestandsDeelId = id,
                )
                val repoName = version.bestandsRepository.takeUnless { it.isBlank() }
                storageService.uploadFile(storageKey, inputStream, part.omvang, repoName)
            }

            part.voltooid = true
            UploadFilePartResult.Success(part.toResponse())
        }

    // ── helpers ──────────────────────────────────────────────────────────────

    /**
     * Splits [totalSize] into a list of chunk sizes where all chunks are at most
     * [BestandsDeelConfig.chunkSizeBytes] bytes and their sum equals [totalSize].
     */
    internal fun splitIntoChunks(totalSize: Long): List<Long> {
        val chunkSize = config.chunkSizeBytes
        require(chunkSize > 0) { "chunkSizeBytes must be > 0" }
        val chunks = mutableListOf<Long>()
        var remaining = totalSize
        while (remaining > 0) {
            val current = minOf(remaining, chunkSize)
            chunks.add(current)
            remaining -= current
        }
        return chunks
    }

    private fun BestandsDeelEntity.toResponse(): BestandsDeelResponse = BestandsDeelResponse(
        url = ApiUrlBuilder.absolute("bestandsdelen", this.id.value.toString()),
        volgnummer = this.volgnummer,
        omvang = this.omvang,
        voltooid = this.voltooid,
        lock = this.lock,
    )
}

sealed class UploadFilePartResult {
    data class Success(val response: BestandsDeelResponse) : UploadFilePartResult()
    data object NotFound : UploadFilePartResult()
    data object InvalidLock : UploadFilePartResult()
    data class OmvangMismatch(val expected: Long, val actual: Long) : UploadFilePartResult()
}
