// SPDX-License-Identifier: EUPL-1.2
// Copyright (C) 2025-2026 Gemeente Utrecht
package com.baseflow.documenten.api.routes

import com.baseflow.shared.api.DOCUMENTEN_API_BASE_PATH
import com.baseflow.shared.api.DOCUMENTEN_API_VERSION
import com.baseflow.shared.api.models.*
import com.baseflow.shared.entities.BestandsDeelEntity
import com.baseflow.shared.entities.BestandsDelen
import com.baseflow.shared.entities.EIORecordEntity
import com.baseflow.shared.services.bestandsDeelStorageKey
import com.baseflow.testutils.TestDataFactory.generateTestDocument
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import io.mockk.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.io.InputStream
import java.io.OutputStream
import java.util.*
import java.util.concurrent.CompletableFuture
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class EnkelvoudigInformatieObjectenRoutesTest : TestBase("eio_routes") {
    companion object {
        private const val API_BASE = DOCUMENTEN_API_BASE_PATH
        private val RESOURCE_SEGMENT = ResourceSegments.ENKELVOUDIG_INFORMATIE_OBJECTEN.value
    }

    @Serializable
    private data class LockPayload(val lock: String)

    @Test
    fun `test POST enkelvoudiginformatieobjecten with taal and bestandsnaam`() = testApplication {
        application { setup() }

        val request = generateTestDocument(taal = "dut", bestandsnaam = "test.pdf")
        val response = client.post("$API_BASE/$RESOURCE_SEGMENT") {
            contentType(ContentType.Application.Json)
            setBody(Json.encodeToString(request))
        }

        assertEquals(HttpStatusCode.Created, response.status)
        assertEquals(DOCUMENTEN_API_VERSION, response.headers["API-version"])
        assertContains(response.headers.names(), HttpHeaders.ETag)
        assertContains(response.headers.names(), HttpHeaders.Location)
        val responseBody = Json.decodeFromString<EnkelvoudigInformatieObjectResponse>(response.bodyAsText())
        assertEquals("dut", responseBody.taal)
        assertEquals("test.pdf", responseBody.bestandsnaam)
        assertEquals(1, responseBody.versie)
        assert(responseBody.id.isNotEmpty()) // UUID should be generated
        // Verify that the computed `url` field is present and points to this resource
        assertNotNull(responseBody.url)
        assertContains(responseBody.url, "/$RESOURCE_SEGMENT/${responseBody.id}")

        val locationHeader = response.headers[HttpHeaders.Location]
        assertNotNull(locationHeader)
        assertEquals(responseBody.url, locationHeader)
    }

    @Test
    fun `test GET list includes url in each result`() = testApplication {
        application { setup() }

        // Create two documents
        val req1 = generateTestDocument(taal = "dut", bestandsnaam = "doc1.pdf")
        val res1 = client.post("$API_BASE/$RESOURCE_SEGMENT") {
            contentType(ContentType.Application.Json)
            setBody(Json.encodeToString(req1))
        }
        assertEquals(HttpStatusCode.Created, res1.status)

        val req2 = generateTestDocument(taal = "eng", bestandsnaam = "doc2.pdf")
        val res2 = client.post("$API_BASE/$RESOURCE_SEGMENT") {
            contentType(ContentType.Application.Json)
            setBody(Json.encodeToString(req2))
        }
        assertEquals(HttpStatusCode.Created, res2.status)

        // Call GET list
        val listResponse = client.get("$API_BASE/$RESOURCE_SEGMENT")
        assertEquals(HttpStatusCode.OK, listResponse.status)

        // Parse JSON and verify that each result item has a non-null url
        val body = listResponse.bodyAsText()
        val json = Json.parseToJsonElement(body).jsonObject
        val results = json["results"]?.jsonArray ?: error("results array missing")
        assert(results.size >= 2)
        for (el in results) {
            val obj = el.jsonObject
            val id = obj["id"]?.jsonPrimitive?.content
            val url = obj["url"]?.jsonPrimitive?.content
            assertNotNull(id)
            assertNotNull(url, "url should be present for item $id")
            assertContains(url, "/$RESOURCE_SEGMENT/$id")
        }
    }

    @Test
    fun `test GET enkelvoudiginformatieobjecten with invalid UUID`() = testApplication {
        application { setup() }

        val response = client.get("$API_BASE/$RESOURCE_SEGMENT/invalid-uuid")

        assertEquals(HttpStatusCode.BadRequest, response.status)
        val ct = response.headers[HttpHeaders.ContentType]
        assertEquals("application/problem+json; charset=utf-8", ct)
        val problem = Json.parseToJsonElement(response.bodyAsText()).jsonObject
        assertEquals(400, problem["status"]?.jsonPrimitive?.content?.toInt())
        assertEquals("Bad Request", problem["title"]?.jsonPrimitive?.content.toString())
    }

    @Test
    fun `test GET enkelvoudiginformatieobjecten with missing UUID parameter`() = testApplication {
        application { setup() }

        val response = client.get("$API_BASE/$RESOURCE_SEGMENT/")

        assertEquals(HttpStatusCode.NotFound, response.status) // Ktor returns 404 for missing path parameter
    }

    @Test
    fun `test GET enkelvoudiginformatieobjecten with valid UUID after creation`() = testApplication {
        application { setup() }

        val request = generateTestDocument(taal = "dut", bestandsnaam = "test.pdf")
        val postResponse = client.post("$API_BASE/$RESOURCE_SEGMENT") {
            contentType(ContentType.Application.Json)
            setBody(Json.encodeToString(request))
        }
        assertEquals(HttpStatusCode.Created, postResponse.status)
        val created = Json.decodeFromString<EnkelvoudigInformatieObjectResponse>(postResponse.bodyAsText())
        val uuid = created.id

        val getResponse = client.get("$API_BASE/$RESOURCE_SEGMENT/$uuid")
        assertEquals(HttpStatusCode.OK, getResponse.status)
        assertEquals(DOCUMENTEN_API_VERSION, getResponse.headers["API-version"])
        assertContains(getResponse.headers.names(), HttpHeaders.ETag)
        val fetched = Json.decodeFromString<EnkelvoudigInformatieObjectResponse>(getResponse.bodyAsText())
        assertEquals(uuid, fetched.id)
        assertEquals("dut", fetched.taal)
        assertEquals("test.pdf", fetched.bestandsnaam)
        assertEquals(1, fetched.versie)
    }

    @Test
    fun `HEAD enkelvoudiginformatieobjecten returns 200 for existing resource`() = testApplication {
        application { setup() }

        // create
        val created = client.post("$API_BASE/$RESOURCE_SEGMENT") {
            contentType(ContentType.Application.Json)
            setBody(Json.encodeToString(generateTestDocument()))
        }
        assertEquals(HttpStatusCode.Created, created.status)
        val body = Json.decodeFromString<EnkelvoudigInformatieObjectResponse>(created.bodyAsText())

        val response = client.head("$API_BASE/$RESOURCE_SEGMENT/${body.id}")
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(DOCUMENTEN_API_VERSION, response.headers["API-version"])
        // TODO: Add ETag header assertions when ETag generation is implemented
    }

    @Test
    fun `HEAD enkelvoudiginformatieobjecten returns 404 for missing resource`() = testApplication {
        application { setup() }

        val resp = client.head("$API_BASE/$RESOURCE_SEGMENT/${UUID.randomUUID()}")
        assertEquals(HttpStatusCode.NotFound, resp.status)
    }

    @Test
    fun `HEAD enkelvoudiginformatieobjecten returns 400 for invalid UUID`() = testApplication {
        application { setup() }

        val resp = client.head("$API_BASE/$RESOURCE_SEGMENT/not-a-uuid")
        assertEquals(HttpStatusCode.BadRequest, resp.status)
    }

    @Test
    fun `DELETE enkelvoudiginformatieobjecten returns 204 and removes resource`() = testApplication {
        application { setup() }

        // create
        val created = client.post("$API_BASE/$RESOURCE_SEGMENT") {
            contentType(ContentType.Application.Json)
            setBody(Json.encodeToString(generateTestDocument()))
        }
        assertEquals(HttpStatusCode.Created, created.status)
        val body = Json.decodeFromString<EnkelvoudigInformatieObjectResponse>(created.bodyAsText())

        // delete
        val del = client.delete("$API_BASE/$RESOURCE_SEGMENT/${body.id}")
        assertEquals(HttpStatusCode.NoContent, del.status)

        // verify gone
        val getResp = client.get("$API_BASE/$RESOURCE_SEGMENT/${body.id}")
        assertEquals(HttpStatusCode.NotFound, getResp.status)
    }

    @Test
    fun `DELETE enkelvoudiginformatieobjecten returns 404 for missing resource`() = testApplication {
        application { setup() }

        val del = client.delete("$API_BASE/$RESOURCE_SEGMENT/${UUID.randomUUID()}")
        assertEquals(HttpStatusCode.NotFound, del.status)
    }

    @Test
    fun `DELETE enkelvoudiginformatieobjecten returns 400 for invalid UUID`() = testApplication {
        application { setup() }

        val del = client.delete("$API_BASE/$RESOURCE_SEGMENT/not-a-uuid")
        assertEquals(HttpStatusCode.BadRequest, del.status)
    }

    @Test
    fun `DELETE enkelvoudiginformatieobjecten returns 409 for locked resource`() = testApplication {
        application { setup() }

        // create
        val created = client.post("$API_BASE/$RESOURCE_SEGMENT") {
            contentType(ContentType.Application.Json)
            setBody(Json.encodeToString(generateTestDocument()))
        }
        assertEquals(HttpStatusCode.Created, created.status)
        val body = Json.decodeFromString<EnkelvoudigInformatieObjectResponse>(created.bodyAsText())

        // lock
        val lockResp = client.post("$API_BASE/$RESOURCE_SEGMENT/${body.id}/lock")
        assertEquals(HttpStatusCode.OK, lockResp.status)

        // attempt delete
        val del = client.delete("$API_BASE/$RESOURCE_SEGMENT/${body.id}")
        assertEquals(HttpStatusCode.Conflict, del.status)
        val ct = del.headers[HttpHeaders.ContentType]
        assertEquals("application/problem+json; charset=utf-8", ct)
        val problem = Json.parseToJsonElement(del.bodyAsText()).jsonObject
        assertEquals(409, problem["status"]?.jsonPrimitive?.content?.toInt())
        assertEquals("Conflict", problem["title"]?.jsonPrimitive?.content)
    }

    @Test
    fun `test GET enkelvoudiginformatieobjecten with valid UUID returns JSON UTF-8`() = testApplication {
        application { setup() }

        val request = generateTestDocument(taal = "dut", bestandsnaam = "test.pdf")
        val postResponse = client.post("$API_BASE/$RESOURCE_SEGMENT") {
            contentType(ContentType.Application.Json)
            setBody(Json.encodeToString(request))
        }
        assertEquals(HttpStatusCode.Created, postResponse.status)
        val created = Json.decodeFromString<EnkelvoudigInformatieObjectResponse>(postResponse.bodyAsText())
        val uuid = created.id

        val getResponse = client.get("$API_BASE/$RESOURCE_SEGMENT/$uuid")
        assertEquals(HttpStatusCode.OK, getResponse.status)
        val contentType = getResponse.headers[HttpHeaders.ContentType]
        assertEquals(ContentType.Application.Json.toString(), contentType)
        val fetched = Json.decodeFromString<EnkelvoudigInformatieObjectResponse>(getResponse.bodyAsText())
        assertEquals(uuid, fetched.id)
        assertEquals("dut", fetched.taal)
        assertEquals("test.pdf", fetched.bestandsnaam)
        assertEquals(1, fetched.versie)
    }

    @Test
    fun `lock returns 200 with lock token, second lock returns 409`() = testApplication {
        application { setup() }

        // Create an object first
        val createReq = generateTestDocument()
        val postResp = client.post("$API_BASE/$RESOURCE_SEGMENT") {
            contentType(ContentType.Application.Json)
            setBody(Json.encodeToString(createReq))
        }
        assertEquals(HttpStatusCode.Created, postResp.status)
        val created = Json.decodeFromString<EnkelvoudigInformatieObjectResponse>(postResp.bodyAsText())

        // Lock it
        val lockResp = client.post("$API_BASE/$RESOURCE_SEGMENT/${created.id}/lock")
        assertEquals(HttpStatusCode.OK, lockResp.status)
        val lockContentType = lockResp.headers[HttpHeaders.ContentType]
        assertEquals(ContentType.Application.Json.toString(), lockContentType)
        val payload = Json.decodeFromString<LockPayload>(lockResp.bodyAsText())
        assert(payload.lock.isNotBlank())

        // Lock again -> Conflict
        val secondLock = client.post("$API_BASE/$RESOURCE_SEGMENT/${created.id}/lock")
        assertEquals(HttpStatusCode.Conflict, secondLock.status)
    }

    @Test
    fun `unlock with correct token returns 204 and subsequent unlock returns 409`() = testApplication {
        application { setup() }

        // Create
        val createReq = generateTestDocument()
        val postResp = client.post("$API_BASE/$RESOURCE_SEGMENT") {
            contentType(ContentType.Application.Json)
            setBody(Json.encodeToString(createReq))
        }
        val created = Json.decodeFromString<EnkelvoudigInformatieObjectResponse>(postResp.bodyAsText())

        // Lock to obtain token
        val lockResp = client.post("$API_BASE/$RESOURCE_SEGMENT/${created.id}/lock")
        val payload = Json.decodeFromString<LockPayload>(lockResp.bodyAsText())

        // Unlock with correct token
        val unlockReq = UnlockEIORequest(lock = payload.lock)
        val unlockResp = client.post("$API_BASE/$RESOURCE_SEGMENT/${created.id}/unlock") {
            contentType(ContentType.Application.Json)
            setBody(Json.encodeToString(unlockReq))
        }
        val lockContentType = lockResp.headers[HttpHeaders.ContentType]
        assertEquals(ContentType.Application.Json.toString(), lockContentType)
        assertEquals(HttpStatusCode.NoContent, unlockResp.status)

        // Unlock again -> NotLocked (409)
        val secondUnlock = client.post("$API_BASE/$RESOURCE_SEGMENT/${created.id}/unlock") {
            contentType(ContentType.Application.Json)
            setBody(Json.encodeToString(unlockReq))
        }
        assertEquals(HttpStatusCode.Conflict, secondUnlock.status)
    }

    @Test
    fun `unlock returns 500 when merged bestandsdelen integrity does not match and keeps resource locked`() = testApplication {
        application { setup() }

        // Use a size that exceeds 1Kb so chunking kicks in.
        val totalSize = 1024L + 1

        val createReq = generateTestDocument(bestandsnaam = "big.pdf").copy(
            bestandsomvang = totalSize,
            inhoud = null,
            formaat = "application/pdf",
            // Keep generated integrity metadata as-is; merged bytes below intentionally differ.
        )

        val postResp = client.post("$API_BASE/$RESOURCE_SEGMENT") {
            contentType(ContentType.Application.Json)
            setBody(Json.encodeToString(createReq))
        }
        assertEquals(HttpStatusCode.Created, postResp.status)
        val created = Json.decodeFromString<EnkelvoudigInformatieObjectResponse>(postResp.bodyAsText())
        val id = UUID.fromString(created.id)
        val token = created.lock
        assertNotNull(token)

        val parts = transaction {
            val latestVersion = EIORecordEntity.findById(id)!!.versions.maxByOrNull { it.versie }!!
            BestandsDeelEntity
                .find { BestandsDelen.versionId eq latestVersion.id }
                .sortedBy { it.volgnummer }
                .also { list ->
                    list.forEach { it.voltooid = true }
                }
                .map {
                    bestandsDeelStorageKey(id, latestVersion.versie, it.id.value)
                }
        }
        // Number of parts depends on chunkSizeBytes; just verify chunking was triggered.
        assert(parts.size >= 2) { "Expected at least 2 bestandsdelen, got ${parts.size}" }

        // Mock each part download to return arbitrary bytes that differ from the declared integrity hash.
        every { mockStorageService.downloadFileTo(any(), any(), anyNullable()) } answers {
            val out = secondArg<OutputStream>()
            // Write a small arbitrary byte so the merged content won't match the integrity hash.
            out.write(byteArrayOf(0x42))
            CompletableFuture.completedFuture(null)
        }
        // Explicitly stub uploadFile and deleteFiles for the merge path so the test
        // fails due to integrity mismatch (not a missing MockK answer).
        every {
            mockStorageService.uploadFile(any<String>(), any<InputStream>(), any<Long>(), anyNullable())
        } answers {
            secondArg<InputStream>().copyTo(OutputStream.nullOutputStream())
            thirdArg<Long>()
        }
        every { mockStorageService.deleteFiles(any(), anyNullable()) } returns Unit

        val unlockResp = client.post("$API_BASE/$RESOURCE_SEGMENT/${created.id}/unlock") {
            contentType(ContentType.Application.Json)
            setBody(Json.encodeToString(UnlockEIORequest(lock = token)))
        }
        assertEquals(HttpStatusCode.InternalServerError, unlockResp.status)
        val problem = Json.parseToJsonElement(unlockResp.bodyAsText()).jsonObject
        assertEquals("Internal Server Error", problem["title"]?.jsonPrimitive?.content)
        assertContains(problem["detail"]?.jsonPrimitive?.content.orEmpty(), "Integrity check failed")

        // Verify that the orphaned merged blob was cleaned up on integrity failure.
        verify { mockStorageService.deleteFiles(any(), anyNullable()) }

        val getResp = client.get("$API_BASE/$RESOURCE_SEGMENT/${created.id}")
        assertEquals(HttpStatusCode.OK, getResp.status)
        val fetched = Json.decodeFromString<EnkelvoudigInformatieObjectResponse>(getResp.bodyAsText())
        assertEquals(true, fetched.locked)
    }

    @Test
    fun `unlock with invalid token returns 409 Conflict`() = testApplication {
        application { setup() }

        // Create and lock
        val createReq = generateTestDocument()
        val postResp = client.post("$API_BASE/$RESOURCE_SEGMENT") {
            contentType(ContentType.Application.Json)
            setBody(Json.encodeToString(createReq))
        }
        val created = Json.decodeFromString<EnkelvoudigInformatieObjectResponse>(postResp.bodyAsText())
        val lockResp = client.post("$API_BASE/$RESOURCE_SEGMENT/${created.id}/lock")
        val payload = Json.decodeFromString<LockPayload>(lockResp.bodyAsText())

        // Try unlock with wrong token
        val unlockReq = UnlockEIORequest(lock = payload.lock + "-wrong")
        val unlockResp = client.post("$API_BASE/$RESOURCE_SEGMENT/${created.id}/unlock") {
            contentType(ContentType.Application.Json)
            setBody(Json.encodeToString(unlockReq))
        }
        assertEquals(HttpStatusCode.Conflict, unlockResp.status)
    }

    @Test
    fun `lock and unlock with unknown UUID return 404`() = testApplication {
        application { setup() }

        val unknownId = "00000000-0000-0000-0000-000000000001"

        val lockResp = client.post("$API_BASE/$RESOURCE_SEGMENT/$unknownId/lock")
        assertEquals(HttpStatusCode.NotFound, lockResp.status)

        val unlockReq = UnlockEIORequest(lock = "any")
        val unlockResp = client.post("$API_BASE/$RESOURCE_SEGMENT/$unknownId/unlock") {
            contentType(ContentType.Application.Json)
            setBody(Json.encodeToString(unlockReq))
        }
        assertEquals(HttpStatusCode.NotFound, unlockResp.status)
    }

    @Test
    fun `lock and unlock with invalid UUID return 400`() = testApplication {
        application { setup() }

        val invalidId = "not-a-uuid"

        val lockResp = client.post("$API_BASE/$RESOURCE_SEGMENT/$invalidId/lock")
        assertEquals(HttpStatusCode.BadRequest, lockResp.status)

        val unlockReq = UnlockEIORequest(lock = "any")
        val unlockResp = client.post("$API_BASE/$RESOURCE_SEGMENT/$invalidId/unlock") {
            contentType(ContentType.Application.Json)
            setBody(Json.encodeToString(unlockReq))
        }
        assertEquals(HttpStatusCode.BadRequest, unlockResp.status)
    }

    @Test
    fun `test zoek endpoint with uuid_In`() = testApplication {
        application { setup() }

        // Create two documents
        val req1 = generateTestDocument(identificatie = "DOC-001")
        val res1 = client.post("$API_BASE/$RESOURCE_SEGMENT") {
            contentType(ContentType.Application.Json)
            setBody(Json.encodeToString(req1))
        }
        val doc1 = Json.decodeFromString<EnkelvoudigInformatieObjectResponse>(res1.bodyAsText())

        val req2 = generateTestDocument(identificatie = "DOC-002")
        val res2 = client.post("$API_BASE/$RESOURCE_SEGMENT") {
            contentType(ContentType.Application.Json)
            setBody(Json.encodeToString(req2))
        }
        val doc2 = Json.decodeFromString<EnkelvoudigInformatieObjectResponse>(res2.bodyAsText())

        val req3 = generateTestDocument(identificatie = "DOC-003")
        val res3 = client.post("$API_BASE/$RESOURCE_SEGMENT") {
            contentType(ContentType.Application.Json)
            setBody(Json.encodeToString(req3))
        }
        val doc3 = Json.decodeFromString<EnkelvoudigInformatieObjectResponse>(res3.bodyAsText())

        // Search for doc1 and doc2
        val zoekReq = EIOZoekRequest(uuidIn = listOf(doc1.id, doc2.id))
        val zoekResponse = client.post("$API_BASE/$RESOURCE_SEGMENT/_zoek") {
            contentType(ContentType.Application.Json)
            setBody(Json.encodeToString(zoekReq))
        }

        assertEquals(HttpStatusCode.OK, zoekResponse.status)
        val body = zoekResponse.bodyAsText()
        val json = Json.parseToJsonElement(body).jsonObject
        val results = json["results"]?.jsonArray ?: error("results array missing")

        assertEquals(2, results.size)
        val ids = results.map { it.jsonObject["id"]?.jsonPrimitive?.content }
        assertContains(ids, doc1.id)
        assertContains(ids, doc2.id)
        assert(!ids.contains(doc3.id))
    }

    @Test
    fun `test GET list paging`() = testApplication {
        application { setup() }

        // Create 12 documents
        for (i in 1..12) {
            val req = generateTestDocument(identificatie = "DOC-%03d".format(i))
            client.post("$API_BASE/$RESOURCE_SEGMENT") {
                contentType(ContentType.Application.Json)
                setBody(Json.encodeToString(req))
            }
        }

        // Page 1
        val resp1 = client.get("$API_BASE/$RESOURCE_SEGMENT?page=1&pageSize=10")
        assertEquals(HttpStatusCode.OK, resp1.status)
        val body1 = Json.parseToJsonElement(resp1.bodyAsText()).jsonObject
        assertEquals(12, body1["count"]?.jsonPrimitive?.content?.toInt())
        val results1 = body1["results"]?.jsonArray ?: error("results missing")
        assertEquals(10, results1.size)
        val next = body1["next"]?.jsonPrimitive?.content
        assertNotNull(next)
        assertContains(next, "page=2")
        assertContains(next, "pageSize=10")

        // Page 2
        val resp2 = client.get("$API_BASE/$RESOURCE_SEGMENT?page=2&pageSize=10")
        assertEquals(HttpStatusCode.OK, resp2.status)
        val body2 = Json.parseToJsonElement(resp2.bodyAsText()).jsonObject
        val results2 = body2["results"]?.jsonArray ?: error("results missing")
        assertEquals(2, results2.size)
        val previous = body2["previous"]?.jsonPrimitive?.content
        assertNotNull(previous)
        assertContains(previous, "page=1")
        assertContains(previous, "pageSize=10")

        // Verify different results
        val ids1 = results1.map { it.jsonObject["id"]?.jsonPrimitive?.content }.toSet()
        val ids2 = results2.map { it.jsonObject["id"]?.jsonPrimitive?.content }.toSet()
        val intersect = ids1.intersect(ids2)
        assertEquals(0, intersect.size, "Pages should not overlap")
    }

    @Test
    fun `test zoek endpoint paging`() = testApplication {
        application { setup() }

        val createdIds = mutableListOf<String>()
        // Create 12 documents
        for (i in 1..12) {
            val req = generateTestDocument(identificatie = "ZOEK-%03d".format(i))
            val res = client.post("$API_BASE/$RESOURCE_SEGMENT") {
                contentType(ContentType.Application.Json)
                setBody(Json.encodeToString(req))
            }
            val doc = Json.decodeFromString<EnkelvoudigInformatieObjectResponse>(res.bodyAsText())
            createdIds.add(doc.id)
        }

        // Search for all 12
        val zoekReq = EIOZoekRequest(uuidIn = createdIds)

        // Page 1
        val resp1 = client.post("$API_BASE/$RESOURCE_SEGMENT/_zoek?page=1&pageSize=10") {
            contentType(ContentType.Application.Json)
            setBody(Json.encodeToString(zoekReq))
        }
        assertEquals(HttpStatusCode.OK, resp1.status)
        val body1 = Json.parseToJsonElement(resp1.bodyAsText()).jsonObject
        assertEquals(12, body1["count"]?.jsonPrimitive?.content?.toInt())
        val results1 = body1["results"]?.jsonArray ?: error("results missing")
        assertEquals(10, results1.size)
        // Next link should be present
        val next = body1["next"]?.jsonPrimitive?.content
        assertNotNull(next)
        assertContains(next, "page=2")
        assertContains(next, "pageSize=10")

        // Page 2
        val resp2 = client.post("$API_BASE/$RESOURCE_SEGMENT/_zoek?page=2&pageSize=10") {
            contentType(ContentType.Application.Json)
            setBody(Json.encodeToString(zoekReq))
        }
        assertEquals(HttpStatusCode.OK, resp2.status)
        val body2 = Json.parseToJsonElement(resp2.bodyAsText()).jsonObject
        val results2 = body2["results"]?.jsonArray ?: error("results missing")
        assertEquals(2, results2.size)
        // Previous link should be present
        val previous = body2["previous"]?.jsonPrimitive?.content
        assertNotNull(previous)
        assertContains(previous, "page=1")
        assertContains(previous, "pageSize=10")
    }

    @Test
    fun `test GET list paging with custom pageSize`() = testApplication {
        application { setup() }

        // Create 12 documents
        for (i in 1..12) {
            val req = generateTestDocument(identificatie = "PAGE-%03d".format(i))
            client.post("$API_BASE/$RESOURCE_SEGMENT") {
                contentType(ContentType.Application.Json)
                setBody(Json.encodeToString(req))
            }
        }

        // Page 1 with pageSize 5
        val resp1 = client.get("$API_BASE/$RESOURCE_SEGMENT?page=1&pageSize=5")
        assertEquals(HttpStatusCode.OK, resp1.status)
        val body1 = Json.parseToJsonElement(resp1.bodyAsText()).jsonObject
        assertEquals(12, body1["count"]?.jsonPrimitive?.content?.toInt())
        val results1 = body1["results"]?.jsonArray ?: error("results missing")
        assertEquals(5, results1.size)
        val next = body1["next"]?.jsonPrimitive?.content
        assertNotNull(next)
        assertContains(next, "page=2")
        assertContains(next, "pageSize=5")

        // Page 3 (should have 2 items: 12 - 2*5 = 2)
        val resp3 = client.get("$API_BASE/$RESOURCE_SEGMENT?page=3&pageSize=5")
        assertEquals(HttpStatusCode.OK, resp3.status)
        val body3 = Json.parseToJsonElement(resp3.bodyAsText()).jsonObject
        val results3 = body3["results"]?.jsonArray ?: error("results missing")
        assertEquals(2, results3.size)
        val previous = body3["previous"]?.jsonPrimitive?.content
        assertNotNull(previous)
        assertContains(previous, "page=2")
        assertContains(previous, "pageSize=5")
    }

    @Test
    fun `test GET list paging preserves filters`() = testApplication {
        application { setup() }

        val bronOrganisatie = "999999999"
        // Create 12 documents with specific bronorganisatie
        for (i in 1..12) {
            val req = generateTestDocument(identificatie = "FILTER-%03d".format(i))
            val reqWithBron = req.copy(bronorganisatie = bronOrganisatie)
            client.post("$API_BASE/$RESOURCE_SEGMENT") {
                contentType(ContentType.Application.Json)
                setBody(Json.encodeToString(reqWithBron))
            }
        }

        // Page 1 with bronorganisatie filter
        val resp = client.get("$API_BASE/$RESOURCE_SEGMENT?page=1&pageSize=5&bronorganisatie=$bronOrganisatie")
        assertEquals(HttpStatusCode.OK, resp.status)
        val body = Json.parseToJsonElement(resp.bodyAsText()).jsonObject
        assertEquals(12, body["count"]?.jsonPrimitive?.content?.toInt())
        val next = body["next"]?.jsonPrimitive?.content
        assertNotNull(next)
        assertContains(next, "page=2")
        assertContains(next, "pageSize=5")
        assertContains(next, "bronorganisatie=$bronOrganisatie")
    }

    @Test
    fun `test GET list with experimental object filters`() = testApplication {
        application { setup() }

        // Create an EIO
        val resEio = client.post("$API_BASE/$RESOURCE_SEGMENT") {
            contentType(ContentType.Application.Json)
            setBody(Json.encodeToString(generateTestDocument(identificatie = "EIO-OIO-1")))
        }
        val eio = Json.decodeFromString<EnkelvoudigInformatieObjectResponse>(resEio.bodyAsText())

        // Create another EIO (not linked)
        client.post("$API_BASE/$RESOURCE_SEGMENT") {
            contentType(ContentType.Application.Json)
            setBody(Json.encodeToString(generateTestDocument(identificatie = "EIO-OIO-2")))
        }

        // Link first EIO to an object
        val objectUrl = "https://example.com/zaken/api/v1/zaken/12345"
        val oioReq = CreateOIORequest(
            informatieobject = eio.url!!,
            subjectObject = objectUrl,
            subjectType = SubjectType("zaak"),
        )
        client.post("$API_BASE/${ResourceSegments.OBJECT_INFORMATIE_OBJECTEN}") {
            contentType(ContentType.Application.Json)
            setBody(Json.encodeToString(oioReq))
        }

        // Filter EIOs by object
        val filterResp =
            client.get(
                "$API_BASE/$RESOURCE_SEGMENT?objectinformatieobjecten__object=${objectUrl.encodeURLParameter()}&objectinformatieobjecten__objectType=zaak",
            )
        assertEquals(HttpStatusCode.OK, filterResp.status)
        val body = Json.parseToJsonElement(filterResp.bodyAsText()).jsonObject
        val results = body["results"]?.jsonArray ?: error("results missing")

        assertEquals(1, results.size)
        assertEquals(eio.id, results[0].jsonObject["id"]?.jsonPrimitive?.content)
    }

    @Test
    fun `test POST zoek with experimental object filters`() = testApplication {
        application { setup() }

        // Create an EIO
        val resEio = client.post("$API_BASE/$RESOURCE_SEGMENT") {
            contentType(ContentType.Application.Json)
            setBody(Json.encodeToString(generateTestDocument(identificatie = "EIO-ZOEK-OIO-1")))
        }
        val eio = Json.decodeFromString<EnkelvoudigInformatieObjectResponse>(resEio.bodyAsText())

        // Link EIO to an object
        val objectUrl = "https://example.com/zaken/api/v1/zaken/67890"
        val oioReq = CreateOIORequest(
            informatieobject = eio.url!!,
            subjectObject = objectUrl,
            subjectType = SubjectType("zaak"),
        )
        client.post("$API_BASE/${ResourceSegments.OBJECT_INFORMATIE_OBJECTEN}") {
            contentType(ContentType.Application.Json)
            setBody(Json.encodeToString(oioReq))
        }

        // Search EIOs by UUID and filter by object (via query params)
        val zoekReq = EIOZoekRequest(uuidIn = listOf(eio.id))
        val filterResp =
            client.post(
                "$API_BASE/$RESOURCE_SEGMENT/_zoek?objectinformatieobjecten__object=${objectUrl.encodeURLParameter()}",
            ) {
                contentType(ContentType.Application.Json)
                setBody(Json.encodeToString(zoekReq))
            }

        assertEquals(HttpStatusCode.OK, filterResp.status)
        val body = Json.parseToJsonElement(filterResp.bodyAsText()).jsonObject
        val results = body["results"]?.jsonArray ?: error("results missing")

        assertEquals(1, results.size)
        assertEquals(eio.id, results[0].jsonObject["id"]?.jsonPrimitive?.content)
    }

    // ── download ──────────────────────────────────────────────────────────────

    @Test
    fun `GET download returns 200 with content headers for an EIO that has content`() = testApplication {
        application { setup() }

        val created = client.post("$API_BASE/$RESOURCE_SEGMENT") {
            contentType(ContentType.Application.Json)
            setBody(Json.encodeToString(generateTestDocument(withContent = true)))
        }
        assertEquals(HttpStatusCode.Created, created.status)
        val id = Json.decodeFromString<EnkelvoudigInformatieObjectResponse>(created.bodyAsText()).id

        val response = client.get("$API_BASE/$RESOURCE_SEGMENT/$id/download")

        assertEquals(HttpStatusCode.OK, response.status)
        assertContains(response.headers.names(), HttpHeaders.ContentDisposition)
        assertContains(response.headers.names(), HttpHeaders.ContentType)
    }
}
