// SPDX-License-Identifier: EUPL-1.2
// Copyright (C) 2026 Gemeente Utrecht
package com.baseflow.api.settings.routes

import com.baseflow.api.models.settings.BlobStorageRepositorySettingsResponse
import com.baseflow.api.models.settings.CreateBlobStorageRepositorySettingsRequest
import com.baseflow.api.models.settings.SetDefaultRepositorySettingsRequest
import com.baseflow.api.models.settings.UpdateBlobStorageRepositorySettingsRequest
import com.baseflow.api.models.badRequest
import com.baseflow.api.models.conflict
import com.baseflow.api.models.notFound
import com.baseflow.api.models.respondProblem
import com.baseflow.config.SecretCrypto
import com.baseflow.entities.settings.BlobStorageRepositorySettingEntity
import com.baseflow.entities.settings.BlobStorageRepositorySettingsTable
import com.baseflow.services.BlobStorageRegistrar
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.util.UUID

/**
 * Setting routes for managing blob storage repositories.
 *
 * Mounted at `/settings/storage-repositories`.
 *
 * Endpoints:
 * - `GET    /`          — list all repositories
 * - `POST   /`          — create a repository
 * - `GET    /{id}`      — get a single repository by UUID
 * - `PUT    /{id}`      — update a repository
 * - `DELETE /{id}`      — delete a repository
 * - `GET    /default`   — get the currently active default repository
 * - `PUT    /default`   — set the default repository
 */
fun Route.blobStorageRepositorySettingsRoutes() {
    route("/storage-repositories") {
        get {
            val repos = transaction {
                BlobStorageRepositorySettingEntity.all().map { it.toResponse() }
            }
            call.respond(repos)
        }
    }
}

private fun String.maskHash(): String = if (length <= 8) "****" else "${take(4)}${"*".repeat(length - 8)}${takeLast(4)}"

private fun BlobStorageRepositorySettingEntity.toResponse() = BlobStorageRepositorySettingsResponse(
    id = id.value.toString(),
    name = repoName,
    storageType = storageType,
    url = url,
    accessKeyHash = accessKeyHash.maskHash(),
    secretKeyHash = secretKeyHash.maskHash(),
    bucket = bucket,
    region = region,
    disableChecksums = disableChecksums,
    disableChunkedEncoding = disableChunkedEncoding,
    extraProperties = extraProperties,
    isDefault = isDefault,
    enabled = enabled,
    createdAt = createdAt.toString(),
    updatedAt = updatedAt.toString(),
    accessKey = accessKeyEncrypted?.let { SecretCrypto.decrypt(it) },
    secretKey = secretKeyEncrypted?.let { SecretCrypto.decrypt(it) },
    storageAccountName = storageAccountName,
)