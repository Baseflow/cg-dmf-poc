// SPDX-License-Identifier: EUPL-1.2
// Copyright (C) 2026 Gemeente Utrecht
package com.baseflow.services

import com.baseflow.api.ApiUrlBuilder
import com.baseflow.api.models.BestandsDeelResponse
import com.baseflow.config.BestandsDeelConfig
import com.baseflow.config.RequestScope
import com.baseflow.entities.BestandsDeelEntity
import com.baseflow.entities.BestandsDelen
import com.baseflow.entities.EIOVersionEntity
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.koin.core.annotation.Scope
import org.koin.core.annotation.Scoped
import java.util.UUID

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
@Scope(RequestScope::class)
@Scoped
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
     * Marks a bestandsdeel as completed (voltooid = true).
     * Returns false when the provided [lockToken] does not match the stored lock.
     */
    fun markVoltooid(id: UUID, lockToken: String): MarkVoltooidResult = transaction {
        val part = BestandsDeelEntity.findById(id) ?: return@transaction MarkVoltooidResult.NotFound
        if (part.lock != lockToken) return@transaction MarkVoltooidResult.InvalidLock
        part.voltooid = true
        MarkVoltooidResult.Success(part.toResponse())
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

sealed class MarkVoltooidResult {
    data class Success(val response: BestandsDeelResponse) : MarkVoltooidResult()
    data object NotFound : MarkVoltooidResult()
    data object InvalidLock : MarkVoltooidResult()
}
