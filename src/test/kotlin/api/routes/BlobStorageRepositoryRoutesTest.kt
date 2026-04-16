// SPDX-License-Identifier: EUPL-1.2
// Copyright (C) 2026 Gemeente Utrecht
package com.baseflow.api.routes

import com.baseflow.api.models.BlobStorageRepositoryResponse
import com.baseflow.api.models.SetDefaultRepositoryRequest
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
            accessKeyHash = BlobStorageRegistrar.sha256("access")
            secretKeyHash = BlobStorageRegistrar.sha256("secret")
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
        // Hashes are masked: first 4 + asterisks + last 4 chars, never the raw plaintext value
        assertFalse(repo.accessKeyHash.contains("access"))
        assertFalse(repo.secretKeyHash.contains("secret"))
        // Middle portion must be replaced with asterisks
        assertTrue(repo.accessKeyHash.contains("****"))
        assertTrue(repo.secretKeyHash.contains("****"))
        // First and last 4 chars are visible hex digits; everything in between is '*'
        val accessMiddle = repo.accessKeyHash.drop(4).dropLast(4)
        val secretMiddle = repo.secretKeyHash.drop(4).dropLast(4)
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
        assertNotNull(body.accessKeyHash)
        assertNotNull(body.secretKeyHash)
        assertNotNull(body.bucket)
        assertNotNull(body.extraProperties)
        assertNotNull(body.createdAt)
        assertNotNull(body.updatedAt)
    }
}
