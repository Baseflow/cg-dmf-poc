// SPDX-License-Identifier: EUPL-1.2
// Copyright (C) 2026 Gemeente Utrecht
package com.baseflow.api.settings.routes

import com.baseflow.api.models.settings.BlobStorageRepositorySettingsResponse
import com.baseflow.api.models.settings.CreateBlobStorageRepositorySettingsRequest
import com.baseflow.api.models.settings.UpdateBlobStorageRepositorySettingsRequest
import com.baseflow.entities.settings.BlobStorageRepositorySettingEntity
import com.baseflow.services.BlobStorageRegistrar
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.util.UUID
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Clock

class BlobStorageRepositorySettingsRoutesTest : SettingsTestBase("blob_storage_repo_settings") {

    @AfterTest
    fun afterTest() {
        BlobStorageRegistrar.resetForTesting()
    }

    private fun insertRepo(
        name: String,
        storageType: String = "S3",
        url: String = "http://localhost:9000",
        bucket: String = "docs",
        accessKey: String = "access-key",
        secretKey: String? = null,
        isDefault: Boolean = false,
        enabled: Boolean = true,
    ): UUID = transaction {
        BlobStorageRepositorySettingEntity.new {
            repoName = name
            this.storageType = storageType
            this.url = url
            this.bucket = bucket
            this.isDefault = isDefault
            this.enabled = enabled
            this.accessKey = accessKey
            this.secretKey = secretKey
            storageAccountName = null
            createdAt = Clock.System.now().toLocalDateTime(TimeZone.UTC)
            updatedAt = Clock.System.now().toLocalDateTime(TimeZone.UTC)
        }.id.value
    }

    // -----------------------------------------------------------------------
    // GET /settings/storage-repositories
    // -----------------------------------------------------------------------

    @Test
    fun `GET returns empty array when no repositories exist`() = testApplication {
        application { setup() }

        val response = client.get("/settings/storage-repositories")

        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(Json.decodeFromString<List<BlobStorageRepositorySettingsResponse>>(response.bodyAsText()).isEmpty())
    }

    @Test
    fun `GET returns all repositories`() = testApplication {
        application { setup() }
        insertRepo("repo-a")
        insertRepo("repo-b")

        val response = client.get("/settings/storage-repositories")

        assertEquals(HttpStatusCode.OK, response.status)
        val body = Json.decodeFromString<List<BlobStorageRepositorySettingsResponse>>(response.bodyAsText())
        assertEquals(2, body.size)
        assertTrue(body.map { it.name }.containsAll(listOf("repo-a", "repo-b")))
    }

    @Test
    fun `GET returns decrypted accessKey`() = testApplication {
        application { setup() }
        insertRepo("repo", accessKey = "my-access-key")

        val response = client.get("/settings/storage-repositories")

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(
            "my-access-key",
            Json.decodeFromString<List<BlobStorageRepositorySettingsResponse>>(response.bodyAsText()).first().accessKey,
        )
    }

    @Test
    fun `GET returns decrypted secretKey`() = testApplication {
        application { setup() }
        insertRepo("repo", secretKey = "my-secret-key")

        val response = client.get("/settings/storage-repositories")

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(
            "my-secret-key",
            Json.decodeFromString<List<BlobStorageRepositorySettingsResponse>>(response.bodyAsText()).first().secretKey,
        )
    }

    // -----------------------------------------------------------------------
    // POST /settings/storage-repositories
    // -----------------------------------------------------------------------

    @Test
    fun `POST creates repository and returns 201`() = testApplication {
        application { setup() }

        val response = client.post("/settings/storage-repositories") {
            contentType(ContentType.Application.Json)
            setBody(
                Json.encodeToString(
                    CreateBlobStorageRepositorySettingsRequest.serializer(),
                    CreateBlobStorageRepositorySettingsRequest(name = "new-repo", storageType = "S3", accessKey = "my-key"),
                ),
            )
        }

        assertEquals(HttpStatusCode.Created, response.status)
        val body = Json.decodeFromString<BlobStorageRepositorySettingsResponse>(response.bodyAsText())
        assertNotNull(body.id)
        assertEquals("new-repo", body.name)
        assertEquals("S3", body.storageType)
    }

    @Test
    fun `POST returns 400 when name is blank`() = testApplication {
        application { setup() }

        val response = client.post("/settings/storage-repositories") {
            contentType(ContentType.Application.Json)
            setBody(
                Json.encodeToString(
                    CreateBlobStorageRepositorySettingsRequest.serializer(),
                    CreateBlobStorageRepositorySettingsRequest(name = "", storageType = "S3", accessKey = "key"),
                ),
            )
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `POST returns 400 when storageType is blank`() = testApplication {
        application { setup() }

        val response = client.post("/settings/storage-repositories") {
            contentType(ContentType.Application.Json)
            setBody(
                Json.encodeToString(
                    CreateBlobStorageRepositorySettingsRequest.serializer(),
                    CreateBlobStorageRepositorySettingsRequest(name = "repo", storageType = "", accessKey = "key"),
                ),
            )
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `POST returns 400 when accessKey is blank`() = testApplication {
        application { setup() }

        val response = client.post("/settings/storage-repositories") {
            contentType(ContentType.Application.Json)
            setBody(
                Json.encodeToString(
                    CreateBlobStorageRepositorySettingsRequest.serializer(),
                    CreateBlobStorageRepositorySettingsRequest(name = "repo", storageType = "S3", accessKey = ""),
                ),
            )
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `POST returns 400 for malformed JSON`() = testApplication {
        application { setup() }

        val response = client.post("/settings/storage-repositories") {
            contentType(ContentType.Application.Json)
            setBody("not-json")
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `POST returns 409 when name already exists`() = testApplication {
        application { setup() }
        insertRepo("duplicate-repo")

        val response = client.post("/settings/storage-repositories") {
            contentType(ContentType.Application.Json)
            setBody(
                Json.encodeToString(
                    CreateBlobStorageRepositorySettingsRequest.serializer(),
                    CreateBlobStorageRepositorySettingsRequest(name = "duplicate-repo", storageType = "S3", accessKey = "key"),
                ),
            )
        }

        assertEquals(HttpStatusCode.Conflict, response.status)
    }

    // -----------------------------------------------------------------------
    // PUT /settings/storage-repositories/{id}
    // -----------------------------------------------------------------------

    @Test
    fun `PUT updates repository and returns 200`() = testApplication {
        application { setup() }
        val id = insertRepo("original-repo")

        val response = client.put("/settings/storage-repositories/$id") {
            contentType(ContentType.Application.Json)
            setBody(
                Json.encodeToString(
                    UpdateBlobStorageRepositorySettingsRequest.serializer(),
                    UpdateBlobStorageRepositorySettingsRequest(name = "updated-repo", storageType = "Azure Blob Storage"),
                ),
            )
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val body = Json.decodeFromString<BlobStorageRepositorySettingsResponse>(response.bodyAsText())
        assertEquals("updated-repo", body.name)
        assertEquals("Azure Blob Storage", body.storageType)
    }

    @Test
    fun `PUT persists non-secret changes verified by subsequent GET`() = testApplication {
        application { setup() }
        val id = insertRepo("original-repo", url = "http://old-url", bucket = "old-bucket")

        client.put("/settings/storage-repositories/$id") {
            contentType(ContentType.Application.Json)
            setBody(
                Json.encodeToString(
                    UpdateBlobStorageRepositorySettingsRequest.serializer(),
                    UpdateBlobStorageRepositorySettingsRequest(
                        name = "updated-repo",
                        storageType = "Azure Blob Storage",
                        url = "http://new-url",
                        bucket = "new-bucket",
                        isDefault = true,
                        enabled = false,
                    ),
                ),
            )
        }

        val getResponse = client.get("/settings/storage-repositories")
        assertEquals(HttpStatusCode.OK, getResponse.status)
        // BlobStorageRepositorySettingsResponse has nullable fields with = null defaults, so default Json works fine
        val body = Json.decodeFromString<List<BlobStorageRepositorySettingsResponse>>(getResponse.bodyAsText())
            .single { it.id == id.toString() }
        assertEquals("updated-repo", body.name)
        assertEquals("Azure Blob Storage", body.storageType)
        assertEquals("http://new-url", body.url)
        assertEquals("new-bucket", body.bucket)
        assertTrue(body.isDefault)
        assertFalse(body.enabled)
    }

    @Test
    fun `PUT returns 400 for invalid UUID`() = testApplication {
        application { setup() }

        val response = client.put("/settings/storage-repositories/not-a-uuid") {
            contentType(ContentType.Application.Json)
            setBody(
                Json.encodeToString(
                    UpdateBlobStorageRepositorySettingsRequest.serializer(),
                    UpdateBlobStorageRepositorySettingsRequest(name = "repo", storageType = "S3"),
                ),
            )
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `PUT returns 400 when name is blank`() = testApplication {
        application { setup() }
        val id = insertRepo("repo")

        val response = client.put("/settings/storage-repositories/$id") {
            contentType(ContentType.Application.Json)
            setBody(
                Json.encodeToString(
                    UpdateBlobStorageRepositorySettingsRequest.serializer(),
                    UpdateBlobStorageRepositorySettingsRequest(name = "", storageType = "S3"),
                ),
            )
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `PUT returns 400 when storageType is blank`() = testApplication {
        application { setup() }
        val id = insertRepo("repo")

        val response = client.put("/settings/storage-repositories/$id") {
            contentType(ContentType.Application.Json)
            setBody(
                Json.encodeToString(
                    UpdateBlobStorageRepositorySettingsRequest.serializer(),
                    UpdateBlobStorageRepositorySettingsRequest(name = "repo", storageType = ""),
                ),
            )
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `PUT returns 404 when repository does not exist`() = testApplication {
        application { setup() }

        val response = client.put("/settings/storage-repositories/${UUID.randomUUID()}") {
            contentType(ContentType.Application.Json)
            setBody(
                Json.encodeToString(
                    UpdateBlobStorageRepositorySettingsRequest.serializer(),
                    UpdateBlobStorageRepositorySettingsRequest(name = "repo", storageType = "S3"),
                ),
            )
        }

        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    @Test
    fun `PUT returns 409 when renaming to an existing name`() = testApplication {
        application { setup() }
        insertRepo("repo-a")
        val idB = insertRepo("repo-b")

        val response = client.put("/settings/storage-repositories/$idB") {
            contentType(ContentType.Application.Json)
            setBody(
                Json.encodeToString(
                    UpdateBlobStorageRepositorySettingsRequest.serializer(),
                    UpdateBlobStorageRepositorySettingsRequest(name = "repo-a", storageType = "S3"),
                ),
            )
        }

        assertEquals(HttpStatusCode.Conflict, response.status)
    }

    @Test
    fun `PUT does not overwrite accessKey when omitted`() = testApplication {
        application { setup() }
        val id = insertRepo("repo", accessKey = "original-key")

        val response = client.put("/settings/storage-repositories/$id") {
            contentType(ContentType.Application.Json)
            setBody(
                Json.encodeToString(
                    UpdateBlobStorageRepositorySettingsRequest.serializer(),
                    UpdateBlobStorageRepositorySettingsRequest(name = "repo", storageType = "S3", accessKey = null),
                ),
            )
        }
        assertEquals(HttpStatusCode.OK, response.status)

        transaction {
            val entity = BlobStorageRepositorySettingEntity.findById(id)!!
            assertNotNull(entity.accessKey)
            assertEquals("original-key", entity.accessKey)
        }
    }

    @Test
    fun `PUT does not overwrite secretKey when omitted`() = testApplication {
        application { setup() }
        val id = insertRepo("repo", secretKey = "original-secret")

        val response = client.put("/settings/storage-repositories/$id") {
            contentType(ContentType.Application.Json)
            setBody(
                Json.encodeToString(
                    UpdateBlobStorageRepositorySettingsRequest.serializer(),
                    UpdateBlobStorageRepositorySettingsRequest(name = "repo", storageType = "S3", secretKey = null),
                ),
            )
        }
        assertEquals(HttpStatusCode.OK, response.status)

        transaction {
            val entity = BlobStorageRepositorySettingEntity.findById(id)!!
            assertNotNull(entity.secretKey)
            assertEquals("original-secret", entity.secretKey)
        }
    }

    // -----------------------------------------------------------------------
    // DELETE /settings/storage-repositories/{id}
    // -----------------------------------------------------------------------

    @Test
    fun `DELETE removes repository and returns 204`() = testApplication {
        application { setup() }
        val id = insertRepo("to-delete")

        val response = client.delete("/settings/storage-repositories/$id")

        assertEquals(HttpStatusCode.NoContent, response.status)
        transaction { assertEquals(null, BlobStorageRepositorySettingEntity.findById(id)) }
    }

    @Test
    fun `DELETE returns 400 for invalid UUID`() = testApplication {
        application { setup() }

        val response = client.delete("/settings/storage-repositories/not-a-uuid")

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `DELETE returns 404 when repository does not exist`() = testApplication {
        application { setup() }

        val response = client.delete("/settings/storage-repositories/${UUID.randomUUID()}")

        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    @Test
    fun `PUT can recover a repository with a new secret`() = testApplication {
        application { setup() }
        val id = insertRepo("corrupted-repo", accessKey = "some-key", secretKey = "some-secret")

        val response = client.put("/settings/storage-repositories/$id") {
            contentType(ContentType.Application.Json)
            setBody(
                Json.encodeToString(
                    UpdateBlobStorageRepositorySettingsRequest.serializer(),
                    UpdateBlobStorageRepositorySettingsRequest(
                        name = "recovered-repo",
                        storageType = "S3",
                        accessKey = "new-working-key",
                        secretKey = "new-working-secret",
                    ),
                ),
            )
        }
        assertEquals(HttpStatusCode.OK, response.status)

        transaction {
            val entity = BlobStorageRepositorySettingEntity.findById(id)!!
            assertEquals("new-working-key", entity.accessKey)
            assertEquals("new-working-secret", entity.secretKey)
            assertEquals("recovered-repo", entity.repoName)
        }
    }
}
