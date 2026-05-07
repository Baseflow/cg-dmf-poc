// SPDX-License-Identifier: EUPL-1.2
// Copyright (C) 2026 Gemeente Utrecht
package com.baseflow.services

import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.CompletableFuture

/**
 * Abstraction over blob storage backends (S3, Azure Blob Storage, etc.).
 * Each configured repository produces one provider instance.
 */
interface BlobStorageProvider {

    /** Human-readable name for this repository. */
    val name: String

    /** Upload [content] as [objectName] (key / blob name). */
    fun uploadFile(objectName: String, content: ByteArray)

    /**
     * Upload a stream of known [contentLength] bytes as [objectName].
     * The default implementation buffers into a ByteArray; backends may override for true streaming.
     */
    fun uploadFile(objectName: String, stream: InputStream, contentLength: Long) {
        uploadFile(objectName, stream.readBytes())
    }

    /** Stream the object identified by [objectName] directly to [output]. */
    fun downloadFileTo(objectName: String, output: OutputStream): CompletableFuture<Void>

    /**
     * Delete the object identified by [objectName].
     * Implementations that do not support deletion (or where deletion is not
     * needed) may no-op. Used for best-effort clean-up of health-check probe objects.
     */
    fun deleteFile(objectName: String) {}

    /**
     * Delete multiple objects in a single operation.
     * The default implementation calls [deleteFile] for each key sequentially.
     * Backends that support native batch deletion (e.g. S3 `DeleteObjects`) should override this.
     */
    fun deleteFiles(objectNames: List<String>) {
        objectNames.forEach { deleteFile(it) }
    }

    /** Quick connectivity / health check. Returns `true` when the backing store is reachable. */
    fun isHealthy(): Boolean
}
