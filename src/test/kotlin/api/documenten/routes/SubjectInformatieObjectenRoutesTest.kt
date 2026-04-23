// SPDX-License-Identifier: EUPL-1.2
// Copyright (C) 2026 Gemeente Utrecht
package com.baseflow.api.documenten.routes

import com.baseflow.api.DOCUMENTEN_API_BASE_PATH
import com.baseflow.api.DOCUMENTEN_API_VERSION
import com.baseflow.api.middleware.AuditContext
import com.baseflow.api.models.CreateOIORequest
import com.baseflow.api.models.ObjectInformatieObjectResponse
import com.baseflow.api.models.ResourceSegments
import com.baseflow.api.models.SubjectType
import com.baseflow.config.ApplicationConfig
import com.baseflow.config.OpenZaakConfig
import com.baseflow.services.AuditTrailService
import com.baseflow.services.BestandsDeelService
import com.baseflow.services.CatalogusService
import com.baseflow.services.EnkelvoudigInformatieObjectService
import com.baseflow.services.StorageService
import com.baseflow.testutils.TestDataFactory
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.*

class SubjectInformatieObjectenRoutesTest : TestBase("subject_oio_routes") {
    companion object {
        private const val API_BASE = DOCUMENTEN_API_BASE_PATH
        private val RESOURCE_SEGMENT = ResourceSegments.SUBJECT_INFORMATIE_OBJECTEN
    }

    private fun createTestEIO(): String = runBlocking {
        val openZaakConfig = OpenZaakConfig(validationEnabled = false)
        val auditContext = AuditContext()
        val service = EnkelvoudigInformatieObjectService(
            mockk<StorageService>(relaxed = true),
            ApplicationConfig,
            CatalogusService(openZaakConfig),
            AuditTrailService(auditContext),
            auditContext,
            BestandsDeelService(),
        )
        val request = TestDataFactory.generateTestDocument(taal = "nld")
        return@runBlocking service.create(request).id
    }

    @Test
    fun `test list empty subjectinformatieobjecten returns paginated empty results`() = testApplication {
        application { setup() }

        val response = client.get("$API_BASE/$RESOURCE_SEGMENT")

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(DOCUMENTEN_API_VERSION, response.headers["API-version"])

        val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
        assertEquals(0, body["count"]?.jsonPrimitive?.content?.toInt())
        val results = body["results"]?.jsonArray
        assertNotNull(results)
        assertTrue(results.isEmpty())
    }

    @Test
    fun `test create subjectinformatieobject returns 201 with location header`() = testApplication {
        application { setup() }
        val eioId = createTestEIO()

        val request = CreateOIORequest(
            informatieobject = "$API_BASE/${ResourceSegments.ENKELVOUDIG_INFORMATIE_OBJECTEN}/$eioId",
            subjectObject = "https://example.com/subjects/123",
            subjectType = SubjectType("zaak"),
        )

        val response = client.post("$API_BASE/$RESOURCE_SEGMENT") {
            contentType(ContentType.Application.Json)
            setBody(Json.encodeToString(CreateOIORequest.serializer(), request))
        }

        assertEquals(HttpStatusCode.Created, response.status)
        assertTrue(response.headers.contains(HttpHeaders.Location))

        val body = Json.decodeFromString<ObjectInformatieObjectResponse>(response.bodyAsText())
        assertNotNull(body.url)
        assertTrue(body.url.contains(RESOURCE_SEGMENT.value))
    }

    @Test
    fun `test subjectinformatieobjecten list paging`() = testApplication {
        application { setup() }
        val eioId = createTestEIO()

        // Create 12 relations
        for (i in 1..12) {
            val request = CreateOIORequest(
                informatieobject = "$API_BASE/${ResourceSegments.ENKELVOUDIG_INFORMATIE_OBJECTEN}/$eioId",
                subjectObject = "https://example.com/subjects/$i",
                subjectType = SubjectType("zaak"),
            )
            client.post("$API_BASE/$RESOURCE_SEGMENT") {
                contentType(ContentType.Application.Json)
                setBody(Json.encodeToString(CreateOIORequest.serializer(), request))
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

        // Page 3
        val resp3 = client.get("$API_BASE/$RESOURCE_SEGMENT?page=3&pageSize=5")
        assertEquals(HttpStatusCode.OK, resp3.status)
        val body3 = Json.parseToJsonElement(resp3.bodyAsText()).jsonObject
        val results3 = body3["results"]?.jsonArray ?: error("results missing")
        assertEquals(2, results3.size)
    }

    @Test
    fun `test get subjectinformatieobject by id`() = testApplication {
        application { setup() }
        val eioId = createTestEIO()
        val request = CreateOIORequest(
            informatieobject = "$API_BASE/${ResourceSegments.ENKELVOUDIG_INFORMATIE_OBJECTEN}/$eioId",
            subjectObject = "https://example.com/subjects/test",
            subjectType = SubjectType("zaak"),
        )

        val createResp = client.post("$API_BASE/$RESOURCE_SEGMENT") {
            contentType(ContentType.Application.Json)
            setBody(Json.encodeToString(CreateOIORequest.serializer(), request))
        }
        val created = Json.decodeFromString<ObjectInformatieObjectResponse>(createResp.bodyAsText())
        val id = created.id

        val getResp = client.get("$API_BASE/$RESOURCE_SEGMENT/$id")
        assertEquals(HttpStatusCode.OK, getResp.status)
        val fetched = Json.decodeFromString<ObjectInformatieObjectResponse>(getResp.bodyAsText())
        assertEquals(id, fetched.id)
    }

    @Test
    fun `test delete subjectinformatieobject`() = testApplication {
        application { setup() }
        val eioId = createTestEIO()
        val request = CreateOIORequest(
            informatieobject = "$API_BASE/${ResourceSegments.ENKELVOUDIG_INFORMATIE_OBJECTEN}/$eioId",
            subjectObject = "https://example.com/subjects/delete",
            subjectType = SubjectType("zaak"),
        )

        val createResp = client.post("$API_BASE/$RESOURCE_SEGMENT") {
            contentType(ContentType.Application.Json)
            setBody(Json.encodeToString(CreateOIORequest.serializer(), request))
        }
        val created = Json.decodeFromString<ObjectInformatieObjectResponse>(createResp.bodyAsText())
        val id = created.id

        val delResp = client.delete("$API_BASE/$RESOURCE_SEGMENT/$id")
        assertEquals(HttpStatusCode.NoContent, delResp.status)

        val getResp = client.get("$API_BASE/$RESOURCE_SEGMENT/$id")
        assertEquals(HttpStatusCode.NotFound, getResp.status)
    }
}
