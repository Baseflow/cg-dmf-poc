// SPDX-License-Identifier: EUPL-1.2
// Copyright (C) 2026 Gemeente Utrecht
package com.baseflow.documenten.api.routes

import com.baseflow.shared.api.DOCUMENTEN_API_BASE_PATH
import com.baseflow.shared.api.DOCUMENTEN_API_VERSION
import com.baseflow.shared.api.middleware.AuditContext
import com.baseflow.shared.api.models.CreateOIORequest
import com.baseflow.shared.api.models.ObjectInformatieObjectResponse
import com.baseflow.shared.api.models.ProblemDetailsResponse
import com.baseflow.shared.api.models.ResourceSegments
import com.baseflow.shared.api.models.SubjectType
import com.baseflow.shared.config.ApplicationConfig
import com.baseflow.shared.services.AuditTrailService
import com.baseflow.shared.services.BestandsDeelService
import com.baseflow.shared.services.CatalogusService
import com.baseflow.shared.services.EnkelvoudigInformatieObjectService
import com.baseflow.shared.services.StorageService
import com.baseflow.testutils.TestDataFactory
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import java.util.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ObjectInformatieObjectenRoutesTest : TestBase("oio_routes") {
    companion object {
        private const val API_BASE = DOCUMENTEN_API_BASE_PATH
        private val RESOURCE_SEGMENT = ResourceSegments.OBJECT_INFORMATIE_OBJECTEN
    }

    // Helper to create an EIO record using the service
    private fun createTestEIO(): String = runBlocking {
        val auditContext = AuditContext()
        val service = EnkelvoudigInformatieObjectService(
            mockk<StorageService>(relaxed = true),
            ApplicationConfig,
            CatalogusService(),
            AuditTrailService(auditContext),
            auditContext,
            BestandsDeelService(),
        )
        val request = TestDataFactory.generateTestDocument(taal = "nld")
        return@runBlocking service.create(request).id
    }

    @Test
    fun `test list empty objectinformatieobjecten returns empty array`() = testApplication {
        application { setup() }

        val response = client.get("$API_BASE/$RESOURCE_SEGMENT")

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(DOCUMENTEN_API_VERSION, response.headers["API-version"])

        val items = Json.decodeFromString<List<ObjectInformatieObjectResponse>>(response.bodyAsText())
        assertTrue(items.isEmpty())
    }

    @Test
    fun `test create objectinformatieobject returns 201 with location header`() = testApplication {
        application { setup() }
        val eioId = createTestEIO()

        val request = CreateOIORequest(
            informatieobject = "$API_BASE/${ResourceSegments.ENKELVOUDIG_INFORMATIE_OBJECTEN}/$eioId",
            subjectObject = "https://example.com/zaken/api/v1/zaken/87654321-4321-4321-4321-210987654321",
            subjectType = SubjectType("zaak"),
        )

        val response = client.post("$API_BASE/$RESOURCE_SEGMENT") {
            contentType(ContentType.Application.Json)
            setBody(Json.encodeToString(CreateOIORequest.serializer(), request))
        }

        val responseBody = response.bodyAsText()

        assertEquals(
            HttpStatusCode.Created,
            response.status,
            "Expected 201 Created but got ${response.status}. Body: $responseBody",
        )
        assertEquals(DOCUMENTEN_API_VERSION, response.headers["API-version"])
        assertTrue(response.headers.contains(HttpHeaders.Location))

        val body = Json.decodeFromString<ObjectInformatieObjectResponse>(responseBody)
        assertNotNull(body.url)
        assertTrue(body.informatieobject.contains(eioId))
        assertEquals(request.subjectObject, body.subjectObject)
        assertEquals(request.subjectType, body.subjectType)
    }

    @Test
    fun `test create duplicate objectinformatieobject returns 400`() = testApplication {
        application { setup() }
        val eioId = createTestEIO()

        val request = CreateOIORequest(
            informatieobject = "$API_BASE/${ResourceSegments.ENKELVOUDIG_INFORMATIE_OBJECTEN}/$eioId",
            subjectObject = "https://example.com/zaken/api/v1/zaken/87654321-4321-4321-4321-210987654321",
            subjectType = SubjectType("zaak"),
        )

        // Create first relation
        client.post("$API_BASE/$RESOURCE_SEGMENT") {
            contentType(ContentType.Application.Json)
            setBody(Json.encodeToString(CreateOIORequest.serializer(), request))
        }

        // Try to create duplicate
        val response = client.post("$API_BASE/$RESOURCE_SEGMENT") {
            contentType(ContentType.Application.Json)
            setBody(Json.encodeToString(CreateOIORequest.serializer(), request))
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `test get objectinformatieobject by id returns 200`() = testApplication {
        application { setup() }
        val eioId = createTestEIO()
        val createRequest = CreateOIORequest(
            informatieobject = "$API_BASE/${ResourceSegments.ENKELVOUDIG_INFORMATIE_OBJECTEN}/$eioId",
            subjectObject = "https://example.com/zaken/api/v1/zaken/87654321-4321-4321-4321-210987654321",
            subjectType = SubjectType("zaak"),
        )

        val createResponse = client.post("$API_BASE/$RESOURCE_SEGMENT") {
            contentType(ContentType.Application.Json)
            setBody(Json.encodeToString(CreateOIORequest.serializer(), createRequest))
        }
        val createBody = Json.decodeFromString<ObjectInformatieObjectResponse>(createResponse.bodyAsText())
        val id = createBody.url?.substringAfterLast("/") ?: error("No ID in create response")

        // Now get it
        val response = client.get("$API_BASE/$RESOURCE_SEGMENT/$id")

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(DOCUMENTEN_API_VERSION, response.headers["API-version"])

        val body = Json.decodeFromString<ObjectInformatieObjectResponse>(response.bodyAsText())
        assertNotNull(body.url)
        assertTrue(body.informatieobject.contains(eioId))
        assertEquals(SubjectType("zaak"), body.subjectType)
    }

    @Test
    fun `test get non-existent objectinformatieobject returns 404`() = testApplication {
        application { setup() }

        val nonExistentId = UUID.randomUUID()
        val response = client.get("$API_BASE/$RESOURCE_SEGMENT/$nonExistentId")

        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    @Test
    fun `test head existing objectinformatieobject returns 200`() = testApplication {
        application { setup() }
        val eioId = createTestEIO()
        val createRequest = CreateOIORequest(
            informatieobject = "$API_BASE/${ResourceSegments.ENKELVOUDIG_INFORMATIE_OBJECTEN}/$eioId",
            subjectObject = "https://example.com/zaken/api/v1/zaken/87654321-4321-4321-4321-210987654321",
            subjectType = SubjectType("zaak"),
        )

        val createResponse = client.post("$API_BASE/$RESOURCE_SEGMENT") {
            contentType(ContentType.Application.Json)
            setBody(Json.encodeToString(CreateOIORequest.serializer(), createRequest))
        }
        val createBody = Json.decodeFromString<ObjectInformatieObjectResponse>(createResponse.bodyAsText())
        val id = createBody.url?.substringAfterLast("/") ?: error("No ID in create response")

        val response = client.head("$API_BASE/$RESOURCE_SEGMENT/$id")

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(DOCUMENTEN_API_VERSION, response.headers["API-version"])
    }

    @Test
    fun `test head non-existent objectinformatieobject returns 404`() = testApplication {
        application { setup() }

        val nonExistentId = UUID.randomUUID()
        val response = client.head("$API_BASE/$RESOURCE_SEGMENT/$nonExistentId")

        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    @Test
    fun `test delete objectinformatieobject returns 204`() = testApplication {
        application { setup() }
        val eioId = createTestEIO()
        val createRequest = CreateOIORequest(
            informatieobject = "$API_BASE/${ResourceSegments.ENKELVOUDIG_INFORMATIE_OBJECTEN}/$eioId",
            subjectObject = "https://example.com/zaken/api/v1/zaken/87654321-4321-4321-4321-210987654321",
            subjectType = SubjectType("zaak"),
        )

        val createResponse = client.post("$API_BASE/$RESOURCE_SEGMENT") {
            contentType(ContentType.Application.Json)
            setBody(Json.encodeToString(CreateOIORequest.serializer(), createRequest))
        }
        val createBody = Json.decodeFromString<ObjectInformatieObjectResponse>(createResponse.bodyAsText())
        val id = createBody.url?.substringAfterLast("/") ?: error("No ID in create response")

        val response = client.delete("$API_BASE/$RESOURCE_SEGMENT/$id")

        assertEquals(HttpStatusCode.NoContent, response.status)

        // Verify deletion
        val getResponse = client.get("$API_BASE/$RESOURCE_SEGMENT/$id")
        assertEquals(HttpStatusCode.NotFound, getResponse.status)
    }

    @Test
    fun `test delete non-existent objectinformatieobject returns 404`() = testApplication {
        application { setup() }

        val nonExistentId = UUID.randomUUID()
        val response = client.delete("$API_BASE/$RESOURCE_SEGMENT/$nonExistentId")

        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    @Test
    fun `test filter by informatieobject returns matching relations`() = testApplication {
        application { setup() }
        val sharedEioId = createTestEIO()
        val sharedEioUrl = "$API_BASE/${ResourceSegments.ENKELVOUDIG_INFORMATIE_OBJECTEN}/$sharedEioId"

        // Create two OIOs with the same informatieobject
        client.post("$API_BASE/$RESOURCE_SEGMENT") {
            contentType(ContentType.Application.Json)
            setBody(
                Json.encodeToString(
                    CreateOIORequest.serializer(),
                    CreateOIORequest(
                        informatieobject = sharedEioUrl,
                        subjectObject = "https://example.com/zaken/api/v1/zaken/11111111-1111-1111-1111-111111111111",
                        subjectType = SubjectType("zaak"),
                    ),
                ),
            )
        }

        client.post("$API_BASE/$RESOURCE_SEGMENT") {
            contentType(ContentType.Application.Json)
            setBody(
                Json.encodeToString(
                    CreateOIORequest.serializer(),
                    CreateOIORequest(
                        informatieobject = sharedEioUrl,
                        subjectObject = "https://example.com/besluiten/api/v1/besluiten/22222222-2222-2222-2222-222222222222",
                        subjectType = SubjectType("besluit"),
                    ),
                ),
            )
        }

        // Create one OIO with a different informatieobject
        val otherEioId = createTestEIO()
        client.post("$API_BASE/$RESOURCE_SEGMENT") {
            contentType(ContentType.Application.Json)
            setBody(
                Json.encodeToString(
                    CreateOIORequest.serializer(),
                    CreateOIORequest(
                        informatieobject = "$API_BASE/${ResourceSegments.ENKELVOUDIG_INFORMATIE_OBJECTEN}/$otherEioId",
                        subjectObject = "https://example.com/zaken/api/v1/zaken/33333333-3333-3333-3333-333333333333",
                        subjectType = SubjectType("zaak"),
                    ),
                ),
            )
        }

        val response = client.get("$API_BASE/$RESOURCE_SEGMENT?informatieobject=${sharedEioUrl.encodeURLParameter()}")
        val responseBody = response.bodyAsText()

        assertEquals(HttpStatusCode.OK, response.status)

        val items = Json.decodeFromString<List<ObjectInformatieObjectResponse>>(responseBody)
        assertEquals(2, items.size, "Expected 2 items but got ${items.size}. Items: $items")
        assertTrue(
            items.all {
                it.informatieobject.contains(sharedEioId)
            },
            "Not all items have matching informatieobject UUID. Expected UUID: $sharedEioId, Got: ${items.map { it.informatieobject }}",
        )
    }

    @Test
    fun `test filter by object returns matching relations`() = testApplication {
        application { setup() }

        val sharedObjectUrl = "https://example.com/zaken/api/v1/zaken/87654321-4321-4321-4321-210987654321"

        // Create two OIOs with the same subject object
        val eio1Id = createTestEIO()
        client.post("$API_BASE/$RESOURCE_SEGMENT") {
            contentType(ContentType.Application.Json)
            setBody(
                Json.encodeToString(
                    CreateOIORequest.serializer(),
                    CreateOIORequest(
                        informatieobject = "$API_BASE/${ResourceSegments.ENKELVOUDIG_INFORMATIE_OBJECTEN}/$eio1Id",
                        subjectObject = sharedObjectUrl,
                        subjectType = SubjectType("zaak"),
                    ),
                ),
            )
        }

        val eio2Id = createTestEIO()
        client.post("$API_BASE/$RESOURCE_SEGMENT") {
            contentType(ContentType.Application.Json)
            setBody(
                Json.encodeToString(
                    CreateOIORequest.serializer(),
                    CreateOIORequest(
                        informatieobject = "$API_BASE/${ResourceSegments.ENKELVOUDIG_INFORMATIE_OBJECTEN}/$eio2Id",
                        subjectObject = sharedObjectUrl,
                        subjectType = SubjectType("zaak"),
                    ),
                ),
            )
        }

        // Create one OIO with a different subject object
        val eio3Id = createTestEIO()
        client.post("$API_BASE/$RESOURCE_SEGMENT") {
            contentType(ContentType.Application.Json)
            setBody(
                Json.encodeToString(
                    CreateOIORequest.serializer(),
                    CreateOIORequest(
                        informatieobject = "$API_BASE/${ResourceSegments.ENKELVOUDIG_INFORMATIE_OBJECTEN}/$eio3Id",
                        subjectObject = "https://example.com/zaken/api/v1/zaken/99999999-9999-9999-9999-999999999999",
                        subjectType = SubjectType("zaak"),
                    ),
                ),
            )
        }

        val response = client.get("$API_BASE/$RESOURCE_SEGMENT?object=$sharedObjectUrl")

        assertEquals(HttpStatusCode.OK, response.status)

        val items = Json.decodeFromString<List<ObjectInformatieObjectResponse>>(response.bodyAsText())
        assertEquals(2, items.size)
        assertTrue(items.all { it.subjectObject == sharedObjectUrl })
    }

    @Test
    fun `test create objectinformatieobject with different object types`() = testApplication {
        application { setup() }

        // Create an actual EIO via API
        val eioId = createTestEIO()

        // Test ZAAK
        val zaakRequest = CreateOIORequest(
            informatieobject = "$API_BASE/${ResourceSegments.ENKELVOUDIG_INFORMATIE_OBJECTEN}/$eioId",
            subjectObject = "https://example.com/zaken/api/v1/zaken/87654321-4321-4321-4321-210987654321",
            subjectType = SubjectType("zaak"),
        )
        val zaakResponse = client.post("$API_BASE/$RESOURCE_SEGMENT") {
            contentType(ContentType.Application.Json)
            setBody(Json.encodeToString(CreateOIORequest.serializer(), zaakRequest))
        }
        assertEquals(HttpStatusCode.Created, zaakResponse.status)

        // Test BESLUIT
        val besluitRequest = CreateOIORequest(
            informatieobject = "$API_BASE/${ResourceSegments.ENKELVOUDIG_INFORMATIE_OBJECTEN}/$eioId",
            subjectObject = "https://example.com/besluiten/api/v1/besluiten/11111111-1111-1111-1111-111111111111",
            subjectType = SubjectType("besluit"),
        )
        val besluitResponse = client.post("$API_BASE/$RESOURCE_SEGMENT") {
            contentType(ContentType.Application.Json)
            setBody(Json.encodeToString(CreateOIORequest.serializer(), besluitRequest))
        }
        assertEquals(HttpStatusCode.Created, besluitResponse.status)

        // Test VERZOEK
        val verzoekRequest = CreateOIORequest(
            informatieobject = "$API_BASE/${ResourceSegments.ENKELVOUDIG_INFORMATIE_OBJECTEN}/$eioId",
            subjectObject = "https://example.com/verzoeken/api/v1/verzoeken/22222222-2222-2222-2222-222222222222",
            subjectType = SubjectType("verzoek"),
        )
        val verzoekResponse = client.post("$API_BASE/$RESOURCE_SEGMENT") {
            contentType(ContentType.Application.Json)
            setBody(Json.encodeToString(CreateOIORequest.serializer(), verzoekRequest))
        }
        assertEquals(HttpStatusCode.Created, verzoekResponse.status)
    }

    @Test
    fun `test get objectinformatieobject with invalid uuid returns 400`() = testApplication {
        application { setup() }

        val response = client.get("$API_BASE/$RESOURCE_SEGMENT/not-a-uuid")

        assertEquals(HttpStatusCode.BadRequest, response.status)
        val body = Json.decodeFromString<ProblemDetailsResponse>(response.bodyAsText())
        assertEquals("Invalid UUID format", body.detail)
    }

    @Test
    fun `test create objectinformatieobject with invalid json returns 400`() = testApplication {
        application { setup() }

        val response = client.post("$API_BASE/$RESOURCE_SEGMENT") {
            contentType(ContentType.Application.Json)
            setBody("{ \"invalid\": \"json\" }")
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
        val body = Json.decodeFromString<ProblemDetailsResponse>(response.bodyAsText())
        assertEquals(HttpStatusCode.BadRequest.value, body.status)
        assertEquals("Bad Request", body.title)
        assertEquals(body.detail?.contains("Invalid request body"), true)
    }

    @Test
    fun `test create objectinformatieobject with non-standard objectType returns 201`() = testApplication {
        application { setup() }
        val eioId = createTestEIO()

        // A type that is not zaak, verzoek or besluit but is a valid format should be accepted
        val request = CreateOIORequest(
            informatieobject = "$API_BASE/${ResourceSegments.ENKELVOUDIG_INFORMATIE_OBJECTEN}/$eioId",
            subjectObject = "https://example.com/overige/api/v1/objecten/11111111-1111-1111-1111-111111111111",
            subjectType = SubjectType("overig"),
        )
        val response = client.post("$API_BASE/$RESOURCE_SEGMENT") {
            contentType(ContentType.Application.Json)
            setBody(Json.encodeToString(CreateOIORequest.serializer(), request))
        }

        assertEquals(HttpStatusCode.Created, response.status)
        val body = Json.decodeFromString<ObjectInformatieObjectResponse>(response.bodyAsText())
        assertEquals(SubjectType("overig"), body.subjectType)
    }

    @Test
    fun `test create objectinformatieobject with invalid objectType format returns 400`() = testApplication {
        application { setup() }
        val eioId = createTestEIO()

        // Inject raw invalid JSON to bypass Kotlin-side SubjectType validation
        val rawJson = """
            {
                "informatieobject": "$API_BASE/${ResourceSegments.ENKELVOUDIG_INFORMATIE_OBJECTEN}/$eioId",
                "object": "https://example.com/zaken/api/v1/zaken/87654321-4321-4321-4321-210987654321",
                "objectType": "INVALID TYPE WITH SPACES"
            }
        """.trimIndent()

        val response = client.post("$API_BASE/$RESOURCE_SEGMENT") {
            contentType(ContentType.Application.Json)
            setBody(rawJson)
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    // ── DELETE / (deleteByEioUrl) ──────────────────────────────────────────────

    private suspend fun ApplicationTestBuilder.createOio(
        eioId: String,
        subjectObject: String = "https://example.com/zaken/api/v1/zaken/${UUID.randomUUID()}",
    ): ObjectInformatieObjectResponse {
        val request = CreateOIORequest(
            informatieobject = "$API_BASE/${ResourceSegments.ENKELVOUDIG_INFORMATIE_OBJECTEN}/$eioId",
            subjectObject = subjectObject,
            subjectType = SubjectType("zaak"),
        )
        val response = client.post("$API_BASE/$RESOURCE_SEGMENT") {
            contentType(ContentType.Application.Json)
            setBody(Json.encodeToString(CreateOIORequest.serializer(), request))
        }
        assertEquals(HttpStatusCode.Created, response.status)
        return Json.decodeFromString(response.bodyAsText())
    }

    @Test
    fun `deleteByFilter - delete on collection without filter returns 400`() = testApplication {
        application { setup() }

        val response = client.delete("$API_BASE/$RESOURCE_SEGMENT")

        assertEquals(HttpStatusCode.BadRequest, response.status)
        val body = Json.decodeFromString<ProblemDetailsResponse>(response.bodyAsText())
        assertEquals("Either 'informatieobject' or 'object' query parameter is required", body.detail)
    }

    @Test
    fun `deleteByEioUrl - invalid informatieobject URL values return 400`() = testApplication {
        application { setup() }

        val invalidValues = listOf(
            UUID.randomUUID().toString(),
            "https://example.com/documenten/api/v1/zaken/${UUID.randomUUID()}",
        )

        invalidValues.forEach { invalidValue ->
            val response = client.delete("$API_BASE/$RESOURCE_SEGMENT") {
                parameter("informatieobject", invalidValue)
            }

            assertEquals(HttpStatusCode.BadRequest, response.status)
            val body = Json.decodeFromString<ProblemDetailsResponse>(response.bodyAsText())
            assertEquals(
                "informatieobject must be a valid URL ending in .../enkelvoudiginformatieobjecten/{uuid}",
                body.detail,
            )
        }
    }

    @Test
    fun `deleteByEioUrl - valid URL with no OIO relations returns 404`() = testApplication {
        application { setup() }
        val eioId = createTestEIO()

        val response = client.delete("$API_BASE/$RESOURCE_SEGMENT") {
            parameter(
                "informatieobject",
                "https://example.com/$API_BASE/${ResourceSegments.ENKELVOUDIG_INFORMATIE_OBJECTEN}/$eioId",
            )
        }

        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    @Test
    fun `deleteByEioUrl - deletes all OIO relations for the given EIO`() = testApplication {
        application { setup() }
        val eioId = createTestEIO()
        val otherEioId = createTestEIO()

        // Create two OIO relations for the same EIO
        val zaakId1 = UUID.randomUUID()
        val zaakId2 = UUID.randomUUID()
        createOio(eioId, "https://example.com/zaken/api/v1/zaken/$zaakId1")
        createOio(eioId, "https://example.com/zaken/api/v1/zaken/$zaakId2")
        createOio(otherEioId)

        val deleteResponse = client.delete("$API_BASE/$RESOURCE_SEGMENT") {
            parameter(
                "informatieobject",
                "https://example.com/$API_BASE/${ResourceSegments.ENKELVOUDIG_INFORMATIE_OBJECTEN}/$eioId",
            )
        }

        assertEquals(HttpStatusCode.NoContent, deleteResponse.status)

        // Verify both relations have been deleted
        val listResponse = client.get("$API_BASE/$RESOURCE_SEGMENT") {
            parameter("informatieobject", "$API_BASE/${ResourceSegments.ENKELVOUDIG_INFORMATIE_OBJECTEN}/$eioId")
        }
        assertEquals(HttpStatusCode.OK, listResponse.status)
        val items = Json.decodeFromString<List<ObjectInformatieObjectResponse>>(listResponse.bodyAsText())
        assertTrue(items.isEmpty(), "Expected all OIO relations to be deleted, but found: $items")

        // Verify relations for another EIO remain untouched
        val controlResponse = client.get("$API_BASE/$RESOURCE_SEGMENT") {
            parameter("informatieobject", "$API_BASE/${ResourceSegments.ENKELVOUDIG_INFORMATIE_OBJECTEN}/$otherEioId")
        }
        assertEquals(HttpStatusCode.OK, controlResponse.status)
        val controlItems = Json.decodeFromString<List<ObjectInformatieObjectResponse>>(controlResponse.bodyAsText())
        assertEquals(1, controlItems.size)
        assertTrue(controlItems.all { it.informatieobject.contains(otherEioId) })
    }

    // ── DELETE / by object (subject_object) ───────────────────────────────────

    @Test
    fun `deleteBySubjectObject - no OIO relations for subject returns 404`() = testApplication {
        application { setup() }

        val response = client.delete("$API_BASE/$RESOURCE_SEGMENT") {
            parameter("object", "https://example.com/zaken/api/v1/zaken/${UUID.randomUUID()}")
        }

        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    @Test
    fun `deleteBySubjectObject - deletes all matching relations and keeps non-matching`() = testApplication {
        application { setup() }
        val eioId = createTestEIO()
        val otherEioId = createTestEIO()
        val subjectUrl = "https://example.com/zaken/api/v1/zaken/${UUID.randomUUID()}"
        val otherSubjectUrl = "https://example.com/zaken/api/v1/zaken/${UUID.randomUUID()}"

        createOio(eioId, subjectUrl)
        createOio(otherEioId, subjectUrl)
        createOio(eioId, otherSubjectUrl)

        val deleteResponse = client.delete("$API_BASE/$RESOURCE_SEGMENT") {
            parameter("object", subjectUrl)
        }
        assertEquals(HttpStatusCode.NoContent, deleteResponse.status)

        val listResponse = client.get("$API_BASE/$RESOURCE_SEGMENT") {
            parameter("object", subjectUrl)
        }
        assertEquals(HttpStatusCode.OK, listResponse.status)
        val items = Json.decodeFromString<List<ObjectInformatieObjectResponse>>(listResponse.bodyAsText())
        assertTrue(items.isEmpty())

        val controlResponse = client.get("$API_BASE/$RESOURCE_SEGMENT") {
            parameter("object", otherSubjectUrl)
        }
        assertEquals(HttpStatusCode.OK, controlResponse.status)
        val controlItems = Json.decodeFromString<List<ObjectInformatieObjectResponse>>(controlResponse.bodyAsText())
        assertEquals(1, controlItems.size)
        assertTrue(controlItems.all { it.subjectObject == otherSubjectUrl })
    }

    @Test
    fun `deleteByFilter - both params supplied returns 400`() = testApplication {
        application { setup() }
        val eioId = createTestEIO()
        val subjectUrl = "https://example.com/zaken/api/v1/zaken/${UUID.randomUUID()}"

        createOio(eioId, subjectUrl)

        val response = client.delete("$API_BASE/$RESOURCE_SEGMENT") {
            parameter(
                "informatieobject",
                "https://example.com/$API_BASE/${ResourceSegments.ENKELVOUDIG_INFORMATIE_OBJECTEN}/$eioId",
            )
            parameter("object", subjectUrl)
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
        val body = Json.decodeFromString<ProblemDetailsResponse>(response.bodyAsText())
        assertEquals("Provide either 'informatieobject' or 'object', not both.", body.detail)
    }
}
