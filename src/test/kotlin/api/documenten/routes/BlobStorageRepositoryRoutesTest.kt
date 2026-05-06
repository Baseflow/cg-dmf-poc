// SPDX-License-Identifier: EUPL-1.2
// Copyright (C) 2026 Gemeente Utrecht
package com.baseflow.api.documenten.routes

import com.baseflow.api.models.BlobStorageRepositoryResponse
import com.baseflow.api.models.CreateBlobStorageRepositoryRequest
import com.baseflow.api.models.SetDefaultRepositoryRequest
import com.baseflow.api.models.UpdateBlobStorageRepositoryRequest
import com.baseflow.entities.BlobStorageRepositories
import com.baseflow.entities.BlobStorageRepositoryEntity
import com.baseflow.services.BlobStorageProvider
import com.baseflow.services.BlobStorageRegistrar
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import io.mockk.every
import io.mockk.mockk
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

class BlobStorageRepositoryRoutesTest : TestBase("blob_storage_routes") {

    // -------------------------------------------------------------------------
    // Fixtures
    // -------------------------------------------------------------------------

    private val repoAlpha = stubProvider("alpha")
    private val repoBeta = stubProvider("beta")

    /**
     * Creates a minimal [BlobStorageProvider] stub whose [name] is [name].
     * No actual blob-storage calls are made in these tests.
     */
    private fun stubProvider(name: String): BlobStorageProvider = mockk<BlobStorageProvider>(relaxed = true).also {
        every { it.name } returns name
        every { it.uploadFile(any(), any()) } returns Unit
        every { it.downloadFileTo(any(), any()) } returns CompletableFuture.completedFuture(null)
        every { it.isHealthy() } returns true
    }

    /**
     * Inserts a row into [BlobStorageRepositories] directly so the GET endpoints
     * can read it back.  Returns the UUID of the inserted row.
     */
    private fun insertRepo(
        name: String,
        type: String = "S3",
        url: String = "http://localhost:9000",
        bucket: String = "docs",
        isDefault: Boolean = false,
    ): UUID = transaction {
        BlobStorageRepositoryEntity.new {
            repoName = name
            storageType = type
            this.url = url
            accessKey = "access"
            secretKey = "secret"
            this.bucket = bucket
            region = "eu-west-1"
            disableChecksums = false
            disableChunkedEncoding = false
            extraProperties = "{}"
            this.isDefault = isDefault
        }.id.value
    }

    @BeforeTest
    override fun beforeTest() {
        super.beforeTest()
        // Start each test with a clean in-memory registrar state
        BlobStorageRegistrar.resetForTesting()
    }

    @AfterTest
    fun afterTest() {
        BlobStorageRegistrar.resetForTesting()
    }

    // -------------------------------------------------------------------------
    // GET /admin/storage-repositories
    // -------------------------------------------------------------------------

    @Test
    fun `GET list returns empty array when no repositories exist`() = testApplication {
        application { setup() }

        val response = client.get("/admin/storage-repositories")

        assertEquals(HttpStatusCode.OK, response.status)
        val body = Json.decodeFromString<List<BlobStorageRepositoryResponse>>(response.bodyAsText())
        assertTrue(body.isEmpty())
    }

    @Test
    fun `GET list returns all repositories`() = testApplication {
        application { setup() }
        insertRepo("alpha")
        insertRepo("beta")

        val response = client.get("/admin/storage-repositories")

        assertEquals(HttpStatusCode.OK, response.status)
        val body = Json.decodeFromString<List<BlobStorageRepositoryResponse>>(response.bodyAsText())
        assertEquals(2, body.size)
        val names = body.map { it.name }
        assertTrue(names.contains("alpha"))
        assertTrue(names.contains("beta"))
    }

    @Test
    fun `GET list does not expose raw secrets`() = testApplication {
        application { setup() }
        insertRepo("alpha")

        val response = client.get("/admin/storage-repositories")

        assertEquals(HttpStatusCode.OK, response.status)
        val body = Json.decodeFromString<List<BlobStorageRepositoryResponse>>(response.bodyAsText())
        val repo = body.first()
        // Secrets are masked: first 4 + asterisks + last 4 chars, never the raw plaintext value
        assertFalse(repo.accessKeyMasked.contains("access"))
        assertFalse(repo.secretKeyMasked.contains("secret"))
        // Middle portion must be replaced with asterisks
        assertTrue(repo.accessKeyMasked.contains("****"))
        assertTrue(repo.secretKeyMasked.contains("****"))
        // First and last 4 chars are visible; everything in between is '*'
        val accessMiddle = repo.accessKeyMasked.drop(4).dropLast(4)
        val secretMiddle = repo.secretKeyMasked.drop(4).dropLast(4)
        assertTrue(accessMiddle.all { it == '*' })
        assertTrue(secretMiddle.all { it == '*' })
    }

    // -------------------------------------------------------------------------
    // GET /admin/storage-repositories/{id}
    // -------------------------------------------------------------------------

    @Test
    fun `GET by id returns 200 with repository details`() = testApplication {
        application { setup() }
        val id = insertRepo("alpha", url = "http://minio:9000", bucket = "mybucket")

        val response = client.get("/admin/storage-repositories/$id")

        assertEquals(HttpStatusCode.OK, response.status)
        val body = Json.decodeFromString<BlobStorageRepositoryResponse>(response.bodyAsText())
        assertEquals(id.toString(), body.id)
        assertEquals("alpha", body.name)
        assertEquals("S3", body.storageType)
        assertEquals("http://minio:9000", body.url)
        assertEquals("mybucket", body.bucket)
    }

    @Test
    fun `GET by id returns 404 for unknown id`() = testApplication {
        application { setup() }

        val response = client.get("/admin/storage-repositories/${UUID.randomUUID()}")

        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    @Test
    fun `GET by id returns 400 for malformed UUID`() = testApplication {
        application { setup() }

        val response = client.get("/admin/storage-repositories/not-a-uuid")

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    // -------------------------------------------------------------------------
    // GET /admin/storage-repositories/default
    // -------------------------------------------------------------------------

    @Test
    fun `GET default returns 404 when no provider registered`() = testApplication {
        application { setup() }

        val response = client.get("/admin/storage-repositories/default")

        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    @Test
    fun `GET default returns the repository marked as default`() = testApplication {
        application { setup() }
        insertRepo("alpha", isDefault = false)
        insertRepo("beta", isDefault = true)
        BlobStorageRegistrar.registerForTesting(repoAlpha)
        BlobStorageRegistrar.registerForTesting(repoBeta, isDefault = true)

        val response = client.get("/admin/storage-repositories/default")

        assertEquals(HttpStatusCode.OK, response.status)
        val body = Json.decodeFromString<BlobStorageRepositoryResponse>(response.bodyAsText())
        assertEquals("beta", body.name)
        assertTrue(body.isDefault)
    }

    @Test
    fun `GET default returns first registered provider when multiple exist`() = testApplication {
        application { setup() }
        insertRepo("alpha", isDefault = false)
        insertRepo("beta", isDefault = false)
        // registerForTesting sets first as default automatically
        BlobStorageRegistrar.registerForTesting(repoAlpha)
        BlobStorageRegistrar.registerForTesting(repoBeta)

        val response = client.get("/admin/storage-repositories/default")

        assertEquals(HttpStatusCode.OK, response.status)
        val body = Json.decodeFromString<BlobStorageRepositoryResponse>(response.bodyAsText())
        assertEquals("alpha", body.name)
    }

    // -------------------------------------------------------------------------
    // PUT /admin/storage-repositories/default
    // -------------------------------------------------------------------------

    @Test
    fun `PUT default changes the active default and persists it`() = testApplication {
        application { setup() }
        insertRepo("alpha", isDefault = true)
        insertRepo("beta", isDefault = false)
        BlobStorageRegistrar.registerForTesting(repoAlpha, isDefault = true)
        BlobStorageRegistrar.registerForTesting(repoBeta)

        val requestBody =
            Json.encodeToString(SetDefaultRepositoryRequest.serializer(), SetDefaultRepositoryRequest("beta"))
        val response = client.put("/admin/storage-repositories/default") {
            contentType(ContentType.Application.Json)
            setBody(requestBody)
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val body = Json.decodeFromString<BlobStorageRepositoryResponse>(response.bodyAsText())
        assertEquals("beta", body.name)
        assertTrue(body.isDefault)

        // Verify the registrar in-memory state changed
        assertEquals("beta", BlobStorageRegistrar.defaultProvider()?.name)

        // Verify DB was updated: beta is now default, alpha is not
        transaction {
            val betaEntity = BlobStorageRepositoryEntity.all().first { it.repoName == "beta" }
            val alphaEntity = BlobStorageRepositoryEntity.all().first { it.repoName == "alpha" }
            assertTrue(betaEntity.isDefault)
            assertFalse(alphaEntity.isDefault)
        }
    }

    @Test
    fun `PUT default returns 400 for unknown repository name`() = testApplication {
        application { setup() }
        insertRepo("alpha")
        BlobStorageRegistrar.registerForTesting(repoAlpha, isDefault = true)

        val requestBody =
            Json.encodeToString(SetDefaultRepositoryRequest.serializer(), SetDefaultRepositoryRequest("nonexistent"))
        val response = client.put("/admin/storage-repositories/default") {
            contentType(ContentType.Application.Json)
            setBody(requestBody)
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `PUT default returns 400 for blank name`() = testApplication {
        application { setup() }

        val requestBody = Json.encodeToString(SetDefaultRepositoryRequest.serializer(), SetDefaultRepositoryRequest(""))
        val response = client.put("/admin/storage-repositories/default") {
            contentType(ContentType.Application.Json)
            setBody(requestBody)
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `PUT default returns 400 when body is missing`() = testApplication {
        application { setup() }

        val response = client.put("/admin/storage-repositories/default") {
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

        val response = client.get("/admin/storage-repositories/$id")

        assertEquals(HttpStatusCode.OK, response.status)
        val body = Json.decodeFromString<BlobStorageRepositoryResponse>(response.bodyAsText())
        assertNotNull(body.id)
        assertNotNull(body.name)
        assertNotNull(body.storageType)
        assertNotNull(body.url)
        assertNotNull(body.accessKeyMasked)
        assertNotNull(body.secretKeyMasked)
        assertNotNull(body.bucket)
        assertNotNull(body.extraProperties)
        assertNotNull(body.createdAt)
        assertNotNull(body.updatedAt)
    }

    // -------------------------------------------------------------------------
    // POST /admin/storage-repositories
    // -------------------------------------------------------------------------

    @Test
    fun `POST creates a new repository and returns 201`() = testApplication {
        application { setup() }

        val request = CreateBlobStorageRepositoryRequest(
            name = "new-repo",
            storageType = "S3",
            url = "http://localhost:9000",
            accessKey = "minioadmin",
            secretKey = "minioadmin",
            bucket = "docs",
            region = "eu-west-1",
        )
        val response = client.post("/admin/storage-repositories") {
            contentType(ContentType.Application.Json)
            setBody(Json.encodeToString(CreateBlobStorageRepositoryRequest.serializer(), request))
        }

        assertEquals(HttpStatusCode.Created, response.status)
        val body = Json.decodeFromString<BlobStorageRepositoryResponse>(response.bodyAsText())
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

        val request = CreateBlobStorageRepositoryRequest(
            name = "persisted-repo",
            storageType = "S3",
            url = "http://localhost:9000",
            accessKey = "key",
            secretKey = "secret",
            bucket = "bucket",
        )
        client.post("/admin/storage-repositories") {
            contentType(ContentType.Application.Json)
            setBody(Json.encodeToString(CreateBlobStorageRepositoryRequest.serializer(), request))
        }

        val entity = transaction {
            BlobStorageRepositoryEntity.all().firstOrNull { it.repoName == "persisted-repo" }
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

        val request = CreateBlobStorageRepositoryRequest(
            name = "new-default",
            storageType = "S3",
            url = "http://localhost:9000",
            accessKey = "key",
            secretKey = "secret",
            bucket = "bucket",
            isDefault = true,
        )
        val response = client.post("/admin/storage-repositories") {
            contentType(ContentType.Application.Json)
            setBody(Json.encodeToString(CreateBlobStorageRepositoryRequest.serializer(), request))
        }

        assertEquals(HttpStatusCode.Created, response.status)
        val body = Json.decodeFromString<BlobStorageRepositoryResponse>(response.bodyAsText())
        assertTrue(body.isDefault)

        // The previously-default repo should no longer be default
        transaction {
            val old = BlobStorageRepositoryEntity.all().first { it.repoName == "existing" }
            assertFalse(old.isDefault)
        }
    }

    @Test
    fun `POST returns 400 for blank name`() = testApplication {
        application { setup() }

        val request = CreateBlobStorageRepositoryRequest(
            name = "",
            storageType = "S3",
            url = "http://localhost:9000",
            accessKey = "key",
            secretKey = "secret",
            bucket = "bucket",
        )
        val response = client.post("/admin/storage-repositories") {
            contentType(ContentType.Application.Json)
            setBody(Json.encodeToString(CreateBlobStorageRepositoryRequest.serializer(), request))
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `POST returns 400 for unknown storageType`() = testApplication {
        application { setup() }

        val response = client.post("/admin/storage-repositories") {
            contentType(ContentType.Application.Json)
            setBody("""{"name":"x","storageType":"UNKNOWN","url":"http://x","accessKey":"k","secretKey":"s","bucket":"b"}""")
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `POST returns 409 when name already exists`() = testApplication {
        application { setup() }
        insertRepo("duplicate-repo")

        val request = CreateBlobStorageRepositoryRequest(
            name = "duplicate-repo",
            storageType = "S3",
            url = "http://localhost:9000",
            accessKey = "key",
            secretKey = "secret",
            bucket = "bucket",
        )
        val response = client.post("/admin/storage-repositories") {
            contentType(ContentType.Application.Json)
            setBody(Json.encodeToString(CreateBlobStorageRepositoryRequest.serializer(), request))
        }

        assertEquals(HttpStatusCode.Conflict, response.status)
    }

    @Test
    fun `POST stores extraProperties and returns them as a map`() = testApplication {
        application { setup() }

        val request = CreateBlobStorageRepositoryRequest(
            name = "repo-with-extras",
            storageType = "S3",
            url = "http://localhost:9000",
            accessKey = "minioadmin",
            secretKey = "minioadmin",
            bucket = "container",
            extraProperties = mapOf("CUSTOM_KEY" to "custom-value", "ANOTHER" to "42"),
        )
        val response = client.post("/admin/storage-repositories") {
            contentType(ContentType.Application.Json)
            setBody(Json.encodeToString(CreateBlobStorageRepositoryRequest.serializer(), request))
        }

        assertEquals(HttpStatusCode.Created, response.status)
        val body = Json.decodeFromString<BlobStorageRepositoryResponse>(response.bodyAsText())
        assertEquals("custom-value", body.extraProperties["CUSTOM_KEY"])
        assertEquals("42", body.extraProperties["ANOTHER"])
    }

    // -------------------------------------------------------------------------
    // PATCH /admin/storage-repositories/{id}
    // -------------------------------------------------------------------------

    @Test
    fun `PATCH updates name and returns 200`() = testApplication {
        application { setup() }
        val id = insertRepo("original-name")
        BlobStorageRegistrar.registerForTesting(repoAlpha)

        val request = UpdateBlobStorageRepositoryRequest(name = "renamed-repo")
        val response = client.patch("/admin/storage-repositories/$id") {
            contentType(ContentType.Application.Json)
            setBody(Json.encodeToString(UpdateBlobStorageRepositoryRequest.serializer(), request))
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val body = Json.decodeFromString<BlobStorageRepositoryResponse>(response.bodyAsText())
        assertEquals("renamed-repo", body.name)
        assertEquals(id.toString(), body.id)
    }

    @Test
    fun `PATCH updates only the supplied fields`() = testApplication {
        application { setup() }
        val id = insertRepo("patch-repo", url = "http://old:9000", bucket = "old-bucket")
        BlobStorageRegistrar.registerForTesting(repoAlpha)

        val request = UpdateBlobStorageRepositoryRequest(url = "http://new:9000")
        val response = client.patch("/admin/storage-repositories/$id") {
            contentType(ContentType.Application.Json)
            setBody(Json.encodeToString(UpdateBlobStorageRepositoryRequest.serializer(), request))
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val body = Json.decodeFromString<BlobStorageRepositoryResponse>(response.bodyAsText())
        assertEquals("http://new:9000", body.url)
        // Unchanged fields stay the same
        assertEquals("old-bucket", body.bucket)
        assertEquals("patch-repo", body.name)
    }

    @Test
    fun `PATCH sets isDefault and clears existing default`() = testApplication {
        application { setup() }
        val idAlpha = insertRepo("alpha", isDefault = true)
        val idBeta = insertRepo("beta", isDefault = false)
        BlobStorageRegistrar.registerForTesting(repoAlpha, isDefault = true)
        BlobStorageRegistrar.registerForTesting(repoBeta)

        val request = UpdateBlobStorageRepositoryRequest(isDefault = true)
        val response = client.patch("/admin/storage-repositories/$idBeta") {
            contentType(ContentType.Application.Json)
            setBody(Json.encodeToString(UpdateBlobStorageRepositoryRequest.serializer(), request))
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val body = Json.decodeFromString<BlobStorageRepositoryResponse>(response.bodyAsText())
        assertTrue(body.isDefault)

        transaction {
            assertFalse(BlobStorageRepositoryEntity.findById(idAlpha)!!.isDefault)
            assertTrue(BlobStorageRepositoryEntity.findById(idBeta)!!.isDefault)
        }
    }

    @Test
    fun `PATCH returns 404 for unknown id`() = testApplication {
        application { setup() }

        val request = UpdateBlobStorageRepositoryRequest(name = "irrelevant")
        val response = client.patch("/admin/storage-repositories/${UUID.randomUUID()}") {
            contentType(ContentType.Application.Json)
            setBody(Json.encodeToString(UpdateBlobStorageRepositoryRequest.serializer(), request))
        }

        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    @Test
    fun `PATCH returns 400 for malformed UUID`() = testApplication {
        application { setup() }

        val request = UpdateBlobStorageRepositoryRequest(name = "irrelevant")
        val response = client.patch("/admin/storage-repositories/not-a-uuid") {
            contentType(ContentType.Application.Json)
            setBody(Json.encodeToString(UpdateBlobStorageRepositoryRequest.serializer(), request))
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `PATCH returns 409 when renaming to an existing name`() = testApplication {
        application { setup() }
        insertRepo("repo-a")
        val idB = insertRepo("repo-b")
        BlobStorageRegistrar.registerForTesting(repoAlpha)
        BlobStorageRegistrar.registerForTesting(repoBeta)

        val request = UpdateBlobStorageRepositoryRequest(name = "repo-a")
        val response = client.patch("/admin/storage-repositories/$idB") {
            contentType(ContentType.Application.Json)
            setBody(Json.encodeToString(UpdateBlobStorageRepositoryRequest.serializer(), request))
        }

        assertEquals(HttpStatusCode.Conflict, response.status)
    }

    @Test
    fun `PATCH updates extraProperties`() = testApplication {
        application { setup() }
        val id = insertRepo("extra-repo")
        BlobStorageRegistrar.registerForTesting(repoAlpha)

        val request = UpdateBlobStorageRepositoryRequest(extraProperties = mapOf("CONTAINER_NAME" to "updated"))
        val response = client.patch("/admin/storage-repositories/$id") {
            contentType(ContentType.Application.Json)
            setBody(Json.encodeToString(UpdateBlobStorageRepositoryRequest.serializer(), request))
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val body = Json.decodeFromString<BlobStorageRepositoryResponse>(response.bodyAsText())
        assertEquals("updated", body.extraProperties["CONTAINER_NAME"])
    }

    // -------------------------------------------------------------------------
    // DELETE /admin/storage-repositories/{id}
    // -------------------------------------------------------------------------

    @Test
    fun `DELETE removes the repository and returns 204`() = testApplication {
        application { setup() }
        val id = insertRepo("to-delete")
        BlobStorageRegistrar.registerForTesting(repoAlpha)

        val response = client.delete("/admin/storage-repositories/$id")

        assertEquals(HttpStatusCode.NoContent, response.status)

        val entity = transaction { BlobStorageRepositoryEntity.findById(id) }
        assertEquals(null, entity)
    }

    @Test
    fun `DELETE returns 404 for unknown id`() = testApplication {
        application { setup() }

        val response = client.delete("/admin/storage-repositories/${UUID.randomUUID()}")

        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    @Test
    fun `DELETE returns 400 for malformed UUID`() = testApplication {
        application { setup() }

        val response = client.delete("/admin/storage-repositories/not-a-uuid")

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `DELETE unregisters the provider from BlobStorageRegistrar`() = testApplication {
        application { setup() }
        val id = insertRepo("alpha")
        BlobStorageRegistrar.registerForTesting(repoAlpha)

        client.delete("/admin/storage-repositories/$id")

        assertEquals(null, BlobStorageRegistrar.providerByName("alpha"))
    }
}
