// SPDX-License-Identifier: EUPL-1.2
// Copyright (C) 2026 Gemeente Utrecht
package com.baseflow.services

import java.io.OutputStream
import java.util.concurrent.CompletableFuture

/**
 * Abstraction over the document storage backend.
 *
 * Implementations:
 *  - [S3StorageService]    — S3-compatible object storage (MinIO, AWS S3, …)
 *  - [AzureStorageService] — Azure Blob Storage
 *
 * The active implementation is chosen at startup via the `STORAGE_BACKEND`
 * environment variable (`s3` or `azure`, default `s3`).
 */
interface IStorageService {

    /**
     * Uploads [content] to the backend under the given [objectName] / blob name.
     */
    fun uploadFile(objectName: String, content: ByteArray)

    /**
     * Streams the object / blob identified by [objectName] into [output].
     *
     * Returns a [CompletableFuture] that completes when the download is done
     * or fails with an exception if an error occurs.
     */
    fun downloadFileTo(objectName: String, output: OutputStream): CompletableFuture<Void>

    /**
     * Performs read and write health probes against the backend.
     *
     * Returns a [StorageStatus] describing the outcome.
     */
    fun checkHealth(): StorageStatus
}

