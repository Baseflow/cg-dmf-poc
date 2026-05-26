// SPDX-License-Identifier: EUPL-1.2
// Copyright (C) 2026 Gemeente Utrecht
package com.baseflow.api.documenten.routes

import com.baseflow.api.models.settings.BlobStorageRepositorySettingsResponse
import com.baseflow.api.models.settings.CreateBlobStorageRepositorySettingsRequest
import com.baseflow.api.models.settings.SetDefaultRepositorySettingsRequest
import com.baseflow.api.models.settings.UpdateBlobStorageRepositorySettingsRequest
import com.baseflow.entities.settings.BlobStorageRepositorySettingEntity
import com.baseflow.services.BlobStorageProvider
import com.baseflow.services.BlobStorageRegistrar
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import io.mockk.every
import io.mockk.mockk
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.util.UUID
import java.util.concurrent.CompletableFuture
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Clock

class BlobStorageRepositoryRoutesTest : TestBase("blob_storage_routes") {

    // -------------------------------------------------------------------------
    // Fixtures
    // -------------------------------------------------------------------------

    private val repoAlpha = stubProvider("alpha")
    private val repoBeta = stubProvider("beta")

    private fun stubProvider(name: String): BlobStorageProvider = mockk<BlobStorageProvider>(relaxed = true).also {
        every { it.name } returns name
        every { it.uploadFile(any(), any()) } returns Unit
        every { it.downloadFileTo(any(), any()) } returns CompletableFuture.completedFuture(null)
        every { it.isHealthy() } returns true
    }

    private fun insertRepo(
        name: String,
        type: String = "S3",
        url: String = "http://localhost:9000",
        bucket: String = "docs",
        isDefault: Boolean = false,
        enabled: Boolean = true,
    ): UUID = transaction {
        BlobStorageRepositorySettingEntity.new {
            repoName = name
            storageType = type
            this.url = url
            accessKey = "access"
            secretKey = "secret"
            this.bucket = bucket
            region = "eu-west-1"
            extraProperties = "{}"
            this.isDefault = isDefault
            this.enabled = enabled
            createdAt = Clock.System.now().toLocalDateTime(TimeZone.UTC)
            updatedAt = Clock.System.now().toLocalDateTime(TimeZone.UTC)
        }.id.value
    }

    @BeforeTest
    override fun beforeTest() {
        super.beforeTest()
        BlobStorageRegistrar.resetForTesting()
    }

    @AfterTest
    fun afterTest() {
        BlobStorageRegistrar.resetForTesting()
    }

    // -------------------------------------------------------------------------
    // GET /settings/storage-repositories
    // -------------------------------------------------------------------------

    @Test
    fun `GET list returns empty array when no repositories exist`() = testApplication {
        application { setup() }

        val response = client.get("/settings/storage-repositories")

        assertEquals(HttpStatusCode.OK, response.status)
        val body = Json.decodeFromString<List<BlobStorageRepositorySettingsResponse>>(response.bodyAsText())
        assertTrue(body.isEmpty())
    }

    @Test
    fun `GET list returns all repositories`() = testApplication {
        application { setup() }
        insertRepo("alpha")
        insertRepo("beta")

        val response = client.get("/settings/storage-repositories")

        assertEquals(HttpStatusCode.OK, response.status)
        val body = Json.decodeFromString<List<BlobStorageRepositorySettingsResponse>>(response.bodyAsText())
        assertEquals(2, body.size)
        val names = body.map { it.name }
        assertTrue(names.contains("alpha"))
        assertTrue(names.contains("beta"))
    }

    @Test
    fun `GET list returns decrypted credentials`() = testApplication {
        application { setup() }
        insertRepo("alpha")

        val response = client.get("/settings/storage-repositories")

        assertEquals(HttpStatusCode.OK, response.status)
        val body = Json.decodeFromString<List<BlobStorageRepositorySettingsResponse>>(response.bodyAsText())
        val repo = body.first()
        assertEquals("access", repo.accessKey)
        assertEquals("secret", repo.secretKey)
    }

    // -------------------------------------------------------------------------
    // GET /settings/storage-repositories/{id}
    // -------------------------------------------------------------------------

    @Test
    fun `GET by id returns 200 with repository details`() = testApplication {
        application { setup() }
        val id = insertRepo("alpha", url = "http://minio:9000", bucket = "mybucket")

        val response = client.get("/settings/storage-repositories/$id")

        assertEquals(HttpStatusCode.OK, response.status)
        val body = Json.decodeFromString<BlobStorageRepositorySettingsResponse>(response.bodyAsText())
        assertEquals(id.toString(), body.id)
        assertEquals("alpha", body.name)
        assertEquals("S3", body.storageType)
        assertEquals("http://minio:9000", body.url)
        assertEquals("mybucket", body.bucket)
    }

    @Test
    fun `GET by id returns 404 for unknown id`() = testApplication {
        application { setup() }

        val response = client.get("/settings/storage-repositories/${UUID.randomUUID()}")

        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    @Test
    fun `GET by id returns 400 for malformed UUID`() = testApplication {
        application { setup() }

        val response = client.get("/settings/storage-repositories/not-a-uuid")

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    // -------------------------------------------------------------------------
    // GET /settings/storage-repositories/default
    // -------------------------------------------------------------------------

    @Test
    fun `GET default returns 404 when no provider registered`() = testApplication {
        application { setup() }

        val response = client.get("/settings/storage-repositories/default")

        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    @Test
    fun `GET default returns the repository marked as default`() = testApplication {
        application { setup() }
        insertRepo("alpha", isDefault = false)
        insertRepo("beta", isDefault = true)
        BlobStorageRegistrar.registerForTesting(repoAlpha)
        BlobStorageRegistrar.registerForTesting(repoBeta, isDefault = true)

        val response = client.get("/settings/storage-repositories/default")

        assertEquals(HttpStatusCode.OK, response.status)
        val body = Json.decodeFromString<BlobStorageRepositorySettingsResponse>(response.bodyAsText())
        assertEquals("beta", body.name)
        assertTrue(body.isDefault)
    }

    @Test
    fun `GET default returns first registered provider when multiple exist`() = testApplication {
        application { setup() }
        insertRepo("alpha", isDefault = false)
        insertRepo("beta", isDefault = false)
        BlobStorageRegistrar.registerForTesting(repoAlpha)
        BlobStorageRegistrar.registerForTesting(repoBeta)

        val response = client.get("/settings/storage-repositories/default")

        assertEquals(HttpStatusCode.OK, response.status)
        val body = Json.decodeFromString<BlobStorageRepositorySettingsResponse>(response.bodyAsText())
        assertEquals("alpha", body.name)
    }

    // -------------------------------------------------------------------------
    // PUT /settings/storage-repositories/default
    // -------------------------------------------------------------------------

    @Test
    fun `PUT default changes the active default and persists it`() = testApplication {
        application { setup() }
        insertRepo("alpha", isDefault = true)
        insertRepo("beta", isDefault = false)
        BlobStorageRegistrar.registerForTesting(repoAlpha, isDefault = true)
        BlobStorageRegistrar.registerForTesting(repoBeta)

        val requestBody = Json.encodeToString(
            SetDefaultRepositorySettingsRequest.serializer(),
            SetDefaultRepositorySettingsRequest("beta"),
        )
        val response = client.put("/settings/storage-repositories/default") {
            contentType(ContentType.Application.Json)
            setBody(requestBody)
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val body = Json.decodeFromString<BlobStorageRepositorySettingsResponse>(response.bodyAsText())
        assertEquals("beta", body.name)
        assertTrue(body.isDefault)

        assertEquals("beta", BlobStorageRegistrar.defaultProvider()?.name)

        transaction {
            val betaEntity = BlobStorageRepositorySettingEntity.all().first { it.repoName == "beta" }
            val alphaEntity = BlobStorageRepositorySettingEntity.all().first { it.repoName == "alpha" }
            assertTrue(betaEntity.isDefault)
            assertFalse(alphaEntity.isDefault)
        }
    }

    @Test
    fun `PUT default returns 400 for unknown repository name`() = testApplication {
        application { setup() }
        insertRepo("alpha")
        BlobStorageRegistrar.registerForTesting(repoAlpha, isDefault = true)

        val requestBody = Json.encodeToString(
            SetDefaultRepositorySettingsRequest.serializer(),
            SetDefaultRepositorySettingsRequest("nonexistent"),
        )
        val response = client.put("/settings/storage-repositories/default") {
            contentType(ContentType.Application.Json)
            setBody(requestBody)
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `PUT default returns 400 for blank name`() = testApplication {
        application { setup() }

        val requestBody = Json.encodeToString(
            SetDefaultRepositorySettingsRequest.serializer(),
            SetDefaultRepositorySettingsRequest(""),
        )
        val response = client.put("/settings/storage-repositories/default") {
            contentType(ContentType.Application.Json)
            setBody(requestBody)
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `PUT default returns 400 when body is missing`() = testApplication {
        application { setup() }

        val response = client.put("/settings/storage-repositories/default") {
            contentType(ContentType.Application.Json)
            setBody("{}")
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    // -------------------------------------------------------------------------
    // Response shape invariants
    // -------------------------------------------------------------------------

    @Test
    fun `repository response includes all expected fields`() = testApplication {
        application { setup() }
        val id = insertRepo("alpha", type = "S3", url = "http://localhost:9000", bucket = "docs")

        val response = client.get("/settings/storage-repositories/$id")

        assertEquals(HttpStatusCode.OK, response.status)
        val body = Json.decodeFromString<BlobStorageRepositorySettingsResponse>(response.bodyAsText())
        assertNotNull(body.id)
        assertNotNull(body.name)
        assertNotNull(body.storageType)
        assertNotNull(body.url)
        assertNotNull(body.bucket)
        assertNotNull(body.createdAt)
        assertNotNull(body.updatedAt)
    }

    // -------------------------------------------------------------------------
    // POST /settings/storage-repositories
    // -------------------------------------------------------------------------

    @Test
    fun `POST creates a new repository and returns 201`() = testApplication {
        application { setup() }

        val request = CreateBlobStorageRepositorySettingsRequest(
            name = "new-repo",
            storageType = "S3",
            url = "http://localhost:9000",
            accessKey = "minioadmin",
            secretKey = "minioadmin",
            bucket = "docs",
            region = "eu-west-1",
        )
        val response = client.post("/settings/storage-repositories") {
            contentType(ContentType.Application.Json)
            setBody(Json.encodeToString(CreateBlobStorageRepositorySettingsRequest.serializer(), request))
        }

        assertEquals(HttpStatusCode.Created, response.status)
        val body = Json.decodeFromString<BlobStorageRepositorySettingsResponse>(response.bodyAsText())
        assertNotNull(body.id)
        assertEquals("new-repo", body.name)
        assertEquals("S3", body.storageType)
        assertEquals("http://localhost:9000", body.url)
        assertEquals("docs", body.bucket)
        assertEquals("eu-west-1", body.region)
        assertFalse(body.isDefault)
    }

    @Test
    fun `POST persists the new repository in the database`() = testApplication {
        application { setup() }

        val request = CreateBlobStorageRepositorySettingsRequest(
            name = "persisted-repo",
            storageType = "S3",
            url = "http://localhost:9000",
            accessKey = "key",
            secretKey = "secret",
            bucket = "bucket",
        )
        client.post("/settings/storage-repositories") {
            contentType(ContentType.Application.Json)
            setBody(Json.encodeToString(CreateBlobStorageRepositorySettingsRequest.serializer(), request))
        }

        val entity = transaction {
            BlobStorageRepositorySettingEntity.all().firstOrNull { it.repoName == "persisted-repo" }
        }
        assertNotNull(entity)
        assertEquals("S3", entity.storageType)
        assertEquals("bucket", entity.bucket)
    }

    @Test
    fun `POST with isDefault=true marks it as default`() = testApplication {
        application { setup() }
        insertRepo("existing", isDefault = true)
        BlobStorageRegistrar.registerForTesting(repoAlpha, isDefault = true)

        val request = CreateBlobStorageRepositorySettingsRequest(
            name = "new-default",
            storageType = "S3",
            url = "http://localhost:9000",
            accessKey = "key",
            secretKey = "secret",
            bucket = "bucket",
            isDefault = true,
        )
        val response = client.post("/settings/storage-repositories") {
            contentType(ContentType.Application.Json)
            setBody(Json.encodeToString(CreateBlobStorageRepositorySettingsRequest.serializer(), request))
        }

        assertEquals(HttpStatusCode.Created, response.status)
        val body = Json.decodeFromString<BlobStorageRepositorySettingsResponse>(response.bodyAsText())
        assertTrue(body.isDefault)

        transaction {
            val old = BlobStorageRepositorySettingEntity.all().first { it.repoName == "existing" }
            assertFalse(old.isDefault)
        }
    }

    @Test
    fun `POST returns 400 for blank name`() = testApplication {
        application { setup() }

        val request = CreateBlobStorageRepositorySettingsRequest(
            name = "",
            storageType = "S3",
            url = "http://localhost:9000",
            accessKey = "key",
            secretKey = "secret",
            bucket = "bucket",
        )
        val response = client.post("/settings/storage-repositories") {
            contentType(ContentType.Application.Json)
            setBody(Json.encodeToString(CreateBlobStorageRepositorySettingsRequest.serializer(), request))
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `POST returns 400 for unknown storageType`() = testApplication {
        application { setup() }

        val response = client.post("/settings/storage-repositories") {
            contentType(ContentType.Application.Json)
            setBody("""{"name":"x","storageType":"UNKNOWN","url":"http://x","accessKey":"k","secretKey":"s","bucket":"b"}""")
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `POST returns 409 when name already exists`() = testApplication {
        application { setup() }
        insertRepo("duplicate-repo")

        val request = CreateBlobStorageRepositorySettingsRequest(
            name = "duplicate-repo",
            storageType = "S3",
            url = "http://localhost:9000",
            accessKey = "key",
            secretKey = "secret",
            bucket = "bucket",
        )
        val response = client.post("/settings/storage-repositories") {
            contentType(ContentType.Application.Json)
            setBody(Json.encodeToString(CreateBlobStorageRepositorySettingsRequest.serializer(), request))
        }

        assertEquals(HttpStatusCode.Conflict, response.status)
    }

    @Test
    fun `POST stores extraProperties and returns them as a map`() = testApplication {
        application { setup() }

        val request = CreateBlobStorageRepositorySettingsRequest(
            name = "repo-with-extras",
            storageType = "S3",
            url = "http://localhost:9000",
            accessKey = "minioadmin",
            secretKey = "minioadmin",
            bucket = "container",
            extraProperties = mapOf("CUSTOM_KEY" to "custom-value", "ANOTHER" to "42"),
        )
        val response = client.post("/settings/storage-repositories") {
            contentType(ContentType.Application.Json)
            setBody(Json.encodeToString(CreateBlobStorageRepositorySettingsRequest.serializer(), request))
        }

        assertEquals(HttpStatusCode.Created, response.status)
        val body = Json.decodeFromString<BlobStorageRepositorySettingsResponse>(response.bodyAsText())
        assertEquals("custom-value", body.extraProperties["CUSTOM_KEY"])
        assertEquals("42", body.extraProperties["ANOTHER"])
    }

    // -------------------------------------------------------------------------
    // PUT /settings/storage-repositories/{id}
    // -------------------------------------------------------------------------

    @Test
    fun `PUT updates name and returns 200`() = testApplication {
        application { setup() }
        val id = insertRepo("original-name")
        BlobStorageRegistrar.registerForTesting(repoAlpha)

        val request = UpdateBlobStorageRepositorySettingsRequest(
            name = "renamed-repo",
            storageType = "S3",
            url = "http://localhost:9000",
            bucket = "docs",
            accessKey = "access",
        )
        val response = client.put("/settings/storage-repositories/$id") {
            contentType(ContentType.Application.Json)
            setBody(Json.encodeToString(UpdateBlobStorageRepositorySettingsRequest.serializer(), request))
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val body = Json.decodeFromString<BlobStorageRepositorySettingsResponse>(response.bodyAsText())
        assertEquals("renamed-repo", body.name)
        assertEquals(id.toString(), body.id)
    }

    @Test
    fun `PUT updates url and returns 200`() = testApplication {
        application { setup() }
        val id = insertRepo("my-repo", url = "http://old:9000", bucket = "old-bucket")
        BlobStorageRegistrar.registerForTesting(repoAlpha)

        val request = UpdateBlobStorageRepositorySettingsRequest(
            name = "my-repo",
            storageType = "S3",
            url = "http://new:9000",
            bucket = "old-bucket",
        )
        val response = client.put("/settings/storage-repositories/$id") {
            contentType(ContentType.Application.Json)
            setBody(Json.encodeToString(UpdateBlobStorageRepositorySettingsRequest.serializer(), request))
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val body = Json.decodeFromString<BlobStorageRepositorySettingsResponse>(response.bodyAsText())
        assertEquals("http://new:9000", body.url)
        assertEquals("old-bucket", body.bucket)
        assertEquals("my-repo", body.name)
    }

    @Test
    fun `PUT sets isDefault and clears existing default`() = testApplication {
        application { setup() }
        val idAlpha = insertRepo("alpha", isDefault = true)
        val idBeta = insertRepo("beta", isDefault = false)
        BlobStorageRegistrar.registerForTesting(repoAlpha, isDefault = true)
        BlobStorageRegistrar.registerForTesting(repoBeta)

        val request = UpdateBlobStorageRepositorySettingsRequest(
            name = "beta",
            storageType = "S3",
            url = "http://localhost:9000",
            bucket = "docs",
            isDefault = true,
        )
        val response = client.put("/settings/storage-repositories/$idBeta") {
            contentType(ContentType.Application.Json)
            setBody(Json.encodeToString(UpdateBlobStorageRepositorySettingsRequest.serializer(), request))
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val body = Json.decodeFromString<BlobStorageRepositorySettingsResponse>(response.bodyAsText())
        assertTrue(body.isDefault)

        transaction {
            assertFalse(BlobStorageRepositorySettingEntity.findById(idAlpha)!!.isDefault)
            assertTrue(BlobStorageRepositorySettingEntity.findById(idBeta)!!.isDefault)
        }
    }

    @Test
    fun `PUT returns 404 for unknown id`() = testApplication {
        application { setup() }

        val request = UpdateBlobStorageRepositorySettingsRequest(
            name = "irrelevant",
            storageType = "S3",
            url = "http://localhost:9000",
        )
        val response = client.put("/settings/storage-repositories/${UUID.randomUUID()}") {
            contentType(ContentType.Application.Json)
            setBody(Json.encodeToString(UpdateBlobStorageRepositorySettingsRequest.serializer(), request))
        }

        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    @Test
    fun `PUT returns 400 for malformed UUID`() = testApplication {
        application { setup() }

        val request = UpdateBlobStorageRepositorySettingsRequest(
            name = "irrelevant",
            storageType = "S3",
            url = "http://localhost:9000",
        )
        val response = client.put("/settings/storage-repositories/not-a-uuid") {
            contentType(ContentType.Application.Json)
            setBody(Json.encodeToString(UpdateBlobStorageRepositorySettingsRequest.serializer(), request))
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `PUT returns 409 when renaming to an existing name`() = testApplication {
        application { setup() }
        insertRepo("repo-a")
        val idB = insertRepo("repo-b")
        BlobStorageRegistrar.registerForTesting(repoAlpha)
        BlobStorageRegistrar.registerForTesting(repoBeta)

        val request = UpdateBlobStorageRepositorySettingsRequest(
            name = "repo-a",
            storageType = "S3",
            url = "http://localhost:9000",
        )
        val response = client.put("/settings/storage-repositories/$idB") {
            contentType(ContentType.Application.Json)
            setBody(Json.encodeToString(UpdateBlobStorageRepositorySettingsRequest.serializer(), request))
        }

        assertEquals(HttpStatusCode.Conflict, response.status)
    }

    @Test
    fun `PUT updates extraProperties`() = testApplication {
        application { setup() }
        val id = insertRepo("extra-repo")
        BlobStorageRegistrar.registerForTesting(repoAlpha)

        val request = UpdateBlobStorageRepositorySettingsRequest(
            name = "extra-repo",
            storageType = "S3",
            url = "http://localhost:9000",
            bucket = "docs",
            extraProperties = mapOf("CONTAINER_NAME" to "updated"),
        )
        val response = client.put("/settings/storage-repositories/$id") {
            contentType(ContentType.Application.Json)
            setBody(Json.encodeToString(UpdateBlobStorageRepositorySettingsRequest.serializer(), request))
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val body = Json.decodeFromString<BlobStorageRepositorySettingsResponse>(response.bodyAsText())
        assertEquals("updated", body.extraProperties["CONTAINER_NAME"])
    }

    // -------------------------------------------------------------------------
    // DELETE /settings/storage-repositories/{id}
    // -------------------------------------------------------------------------

    @Test
    fun `DELETE removes the repository and returns 204`() = testApplication {
        application { setup() }
        val id = insertRepo("to-delete")
        BlobStorageRegistrar.registerForTesting(repoAlpha)

        val response = client.delete("/settings/storage-repositories/$id")

        assertEquals(HttpStatusCode.NoContent, response.status)

        val entity = transaction { BlobStorageRepositorySettingEntity.findById(id) }
        assertEquals(null, entity)
    }

    @Test
    fun `DELETE returns 404 for unknown id`() = testApplication {
        application { setup() }

        val response = client.delete("/settings/storage-repositories/${UUID.randomUUID()}")

        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    @Test
    fun `DELETE returns 400 for malformed UUID`() = testApplication {
        application { setup() }

        val response = client.delete("/settings/storage-repositories/not-a-uuid")

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `DELETE unregisters the provider from BlobStorageRegistrar`() = testApplication {
        application { setup() }
        val id = insertRepo("alpha")
        BlobStorageRegistrar.registerForTesting(repoAlpha)

        client.delete("/settings/storage-repositories/$id")

        assertEquals(null, BlobStorageRegistrar.providerByName("alpha"))
    }
}
