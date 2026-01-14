// SPDX-License-Identifier: EUPL-1.2
// Copyright (C) 2026 Gemeente Utrecht
package com.baseflow.api.routes

import com.baseflow.api.DOCUMENTEN_API_VERSION
import com.baseflow.api.DOCUMENTEN_API_BASE_PATH
import com.baseflow.api.documentenApiModule
import com.baseflow.api.models.CreateOIORequest
import com.baseflow.api.models.ObjectInformatieObjectResponse
import com.baseflow.api.models.ProblemDetailsResponse
import com.baseflow.api.models.SubjectTypeEnum
import io.ktor.http.*
import io.ktor.server.testing.*
import io.ktor.server.application.*
import io.ktor.client.request.*
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import com.baseflow.services.EnkelvoudigInformatieObjectService
import com.baseflow.services.OpenZaakService
import com.baseflow.services.StorageService
import com.baseflow.config.ApplicationConfig
import com.baseflow.config.OpenZaakConfig
import com.baseflow.testutils.TestDataFactory
import com.baseflow.tooling.AllTables
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.Test
import kotlin.test.BeforeTest

class ObjectInformatieObjectenRoutesTest {
    private lateinit var dbName: String

    @BeforeTest
    fun setup() {
        dbName = "oio_routes_${UUID.randomUUID()}"
        Database.connect(
            "jdbc:h2:mem:$dbName;DB_CLOSE_DELAY=-1;",
            driver = "org.h2.Driver",
            user = "root",
            password = ""
        )
        transaction {
            AllTables.createMissing()
        }
    }

    companion object {
        private const val API_BASE = DOCUMENTEN_API_BASE_PATH
        private const val RESOURCE_SEGMENT = "objectinformatieobjecten"
    }

    private fun Application.testModule() {
        // Reuse the connection from setup()
        // This avoids conflicts between the testcases and
        // allows the service and the routes to share the same database connection
        Database.connect(
            "jdbc:h2:mem:$dbName;DB_CLOSE_DELAY=-1;",
            driver = "org.h2.Driver",
            user = "root",
            password = ""
        )

        val openZaakConfig = OpenZaakConfig(validationEnabled = false)
        documentenApiModule(useAuthentication = false, openZaakConfig = openZaakConfig)
    }

    // Helper to create an EIO record using the service
    private fun createTestEIO(): String = runBlocking {
        val openZaakConfig = OpenZaakConfig(validationEnabled = false)
        val service = EnkelvoudigInformatieObjectService(StorageService(), ApplicationConfig, OpenZaakService(openZaakConfig))
        val request = TestDataFactory.generateTestDocument(taal = "nld")
        return@runBlocking service.create(request).id
    }

    @Test
    fun `test list empty objectinformatieobjecten returns empty array`() = testApplication {
        application { testModule() }

        val response = client.get("$API_BASE/$RESOURCE_SEGMENT")

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(DOCUMENTEN_API_VERSION, response.headers["API-version"])

        val items = Json.decodeFromString<List<ObjectInformatieObjectResponse>>(response.bodyAsText())
        assertTrue(items.isEmpty())
    }

    @Test
    fun `test create objectinformatieobject returns 201 with location header`() = testApplication {
        application { testModule() }
        val eioId = createTestEIO()

        val request = CreateOIORequest(
            informatieobject = "$API_BASE/enkelvoudiginformatieobjecten/$eioId",
            subjectObject = "https://example.com/zaken/api/v1/zaken/87654321-4321-4321-4321-210987654321",
            subjectType = SubjectTypeEnum.ZAAK
        )

        val response = client.post("$API_BASE/$RESOURCE_SEGMENT") {
            contentType(ContentType.Application.Json)
            setBody(Json.encodeToString(CreateOIORequest.serializer(), request))
        }

        val responseBody = response.bodyAsText()

        assertEquals(HttpStatusCode.Created, response.status, "Expected 201 Created but got ${response.status}. Body: $responseBody")
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
        application { testModule() }
        val eioId = createTestEIO()

        val request = CreateOIORequest(
            informatieobject = "$API_BASE/enkelvoudiginformatieobjecten/$eioId",
            subjectObject = "https://example.com/zaken/api/v1/zaken/87654321-4321-4321-4321-210987654321",
            subjectType = SubjectTypeEnum.ZAAK
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
        application { testModule() }
        val eioId = createTestEIO()
        val createRequest = CreateOIORequest(
            informatieobject = "$API_BASE/enkelvoudiginformatieobjecten/$eioId",
            subjectObject = "https://example.com/zaken/api/v1/zaken/87654321-4321-4321-4321-210987654321",
            subjectType = SubjectTypeEnum.ZAAK
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
        assertEquals(SubjectTypeEnum.ZAAK, body.subjectType)
    }

    @Test
    fun `test get non-existent objectinformatieobject returns 404`() = testApplication {
        application { testModule() }

        val nonExistentId = UUID.randomUUID()
        val response = client.get("$API_BASE/$RESOURCE_SEGMENT/$nonExistentId")

        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    @Test
    fun `test head existing objectinformatieobject returns 200`() = testApplication {
        application { testModule() }
        val eioId = createTestEIO()
        val createRequest = CreateOIORequest(
            informatieobject = "$API_BASE/enkelvoudiginformatieobjecten/$eioId",
            subjectObject = "https://example.com/zaken/api/v1/zaken/87654321-4321-4321-4321-210987654321",
            subjectType = SubjectTypeEnum.ZAAK
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
        application { testModule() }

        val nonExistentId = UUID.randomUUID()
        val response = client.head("$API_BASE/$RESOURCE_SEGMENT/$nonExistentId")

        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    @Test
    fun `test delete objectinformatieobject returns 204`() = testApplication {
        application { testModule() }
        val eioId = createTestEIO()
        val createRequest = CreateOIORequest(
            informatieobject = "$API_BASE/enkelvoudiginformatieobjecten/$eioId",
            subjectObject = "https://example.com/zaken/api/v1/zaken/87654321-4321-4321-4321-210987654321",
            subjectType = SubjectTypeEnum.ZAAK
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
        application { testModule() }

        val nonExistentId = UUID.randomUUID()
        val response = client.delete("$API_BASE/$RESOURCE_SEGMENT/$nonExistentId")

        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    @Test
    fun `test filter by informatieobject returns matching relations`() = testApplication {
        application { testModule() }
        val sharedEioId = createTestEIO()
        val sharedEioUrl = "$API_BASE/enkelvoudiginformatieobjecten/$sharedEioId"

        // Create two OIOs with the same informatieobject
        client.post("$API_BASE/$RESOURCE_SEGMENT") {
            contentType(ContentType.Application.Json)
            setBody(Json.encodeToString(CreateOIORequest.serializer(), CreateOIORequest(
                informatieobject = sharedEioUrl,
                subjectObject = "https://example.com/zaken/api/v1/zaken/11111111-1111-1111-1111-111111111111",
                subjectType = SubjectTypeEnum.ZAAK
            )))
        }

        client.post("$API_BASE/$RESOURCE_SEGMENT") {
            contentType(ContentType.Application.Json)
            setBody(Json.encodeToString(CreateOIORequest.serializer(), CreateOIORequest(
                informatieobject = sharedEioUrl,
                subjectObject = "https://example.com/besluiten/api/v1/besluiten/22222222-2222-2222-2222-222222222222",
                subjectType = SubjectTypeEnum.BESLUIT
            )))
        }

        // Create one OIO with a different informatieobject
        val otherEioId = createTestEIO()
        client.post("$API_BASE/$RESOURCE_SEGMENT") {
            contentType(ContentType.Application.Json)
            setBody(Json.encodeToString(CreateOIORequest.serializer(), CreateOIORequest(
                informatieobject = "$API_BASE/enkelvoudiginformatieobjecten/$otherEioId",
                subjectObject = "https://example.com/zaken/api/v1/zaken/33333333-3333-3333-3333-333333333333",
                subjectType = SubjectTypeEnum.ZAAK
            )))
        }

        val response = client.get("$API_BASE/$RESOURCE_SEGMENT?informatieobject=${sharedEioUrl.encodeURLParameter()}")
        val responseBody = response.bodyAsText()

        assertEquals(HttpStatusCode.OK, response.status)

        val items = Json.decodeFromString<List<ObjectInformatieObjectResponse>>(responseBody)
        assertEquals(2, items.size, "Expected 2 items but got ${items.size}. Items: $items")
        assertTrue(items.all { it.informatieobject.contains(sharedEioId) }, "Not all items have matching informatieobject UUID. Expected UUID: $sharedEioId, Got: ${items.map { it.informatieobject }}")
    }

    @Test
    fun `test filter by object returns matching relations`() = testApplication {
        application { testModule() }

        val sharedObjectUrl = "https://example.com/zaken/api/v1/zaken/87654321-4321-4321-4321-210987654321"

        // Create two OIOs with the same subject object
        val eio1Id = createTestEIO()
        client.post("$API_BASE/$RESOURCE_SEGMENT") {
            contentType(ContentType.Application.Json)
            setBody(Json.encodeToString(CreateOIORequest.serializer(), CreateOIORequest(
                informatieobject = "$API_BASE/enkelvoudiginformatieobjecten/$eio1Id",
                subjectObject = sharedObjectUrl,
                subjectType = SubjectTypeEnum.ZAAK
            )))
        }

        val eio2Id = createTestEIO()
        client.post("$API_BASE/$RESOURCE_SEGMENT") {
            contentType(ContentType.Application.Json)
            setBody(Json.encodeToString(CreateOIORequest.serializer(), CreateOIORequest(
                informatieobject = "$API_BASE/enkelvoudiginformatieobjecten/$eio2Id",
                subjectObject = sharedObjectUrl,
                subjectType = SubjectTypeEnum.ZAAK
            )))
        }

        // Create one OIO with a different subject object
        val eio3Id = createTestEIO()
        client.post("$API_BASE/$RESOURCE_SEGMENT") {
            contentType(ContentType.Application.Json)
            setBody(Json.encodeToString(CreateOIORequest.serializer(), CreateOIORequest(
                informatieobject = "$API_BASE/enkelvoudiginformatieobjecten/$eio3Id",
                subjectObject = "https://example.com/zaken/api/v1/zaken/99999999-9999-9999-9999-999999999999",
                subjectType = SubjectTypeEnum.ZAAK
            )))
        }

        val response = client.get("$API_BASE/$RESOURCE_SEGMENT?object=$sharedObjectUrl")

        assertEquals(HttpStatusCode.OK, response.status)

        val items = Json.decodeFromString<List<ObjectInformatieObjectResponse>>(response.bodyAsText())
        assertEquals(2, items.size)
        assertTrue(items.all { it.subjectObject == sharedObjectUrl })
    }

    @Test
    fun `test create objectinformatieobject with different object types`() = testApplication {
        application { testModule() }

        // Create an actual EIO via API
        val eioId = createTestEIO()

        // Test ZAAK
        val zaakRequest = CreateOIORequest(
            informatieobject = "$API_BASE/enkelvoudiginformatieobjecten/$eioId",
            subjectObject = "https://example.com/zaken/api/v1/zaken/87654321-4321-4321-4321-210987654321",
            subjectType = SubjectTypeEnum.ZAAK
        )
        val zaakResponse = client.post("$API_BASE/$RESOURCE_SEGMENT") {
            contentType(ContentType.Application.Json)
            setBody(Json.encodeToString(CreateOIORequest.serializer(), zaakRequest))
        }
        assertEquals(HttpStatusCode.Created, zaakResponse.status)

        // Test BESLUIT
        val besluitRequest = CreateOIORequest(
            informatieobject = "$API_BASE/enkelvoudiginformatieobjecten/$eioId",
            subjectObject = "https://example.com/besluiten/api/v1/besluiten/11111111-1111-1111-1111-111111111111",
            subjectType = SubjectTypeEnum.BESLUIT
        )
        val besluitResponse = client.post("$API_BASE/$RESOURCE_SEGMENT") {
            contentType(ContentType.Application.Json)
            setBody(Json.encodeToString(CreateOIORequest.serializer(), besluitRequest))
        }
        assertEquals(HttpStatusCode.Created, besluitResponse.status)

        // Test VERZOEK
        val verzoekRequest = CreateOIORequest(
            informatieobject = "$API_BASE/enkelvoudiginformatieobjecten/$eioId",
            subjectObject = "https://example.com/verzoeken/api/v1/verzoeken/22222222-2222-2222-2222-222222222222",
            subjectType = SubjectTypeEnum.VERZOEK
        )
        val verzoekResponse = client.post("$API_BASE/$RESOURCE_SEGMENT") {
            contentType(ContentType.Application.Json)
            setBody(Json.encodeToString(CreateOIORequest.serializer(), verzoekRequest))
        }
        assertEquals(HttpStatusCode.Created, verzoekResponse.status)
    }
    @Test
    fun `test get objectinformatieobject with invalid uuid returns 400`() = testApplication {
        application { testModule() }

        val response = client.get("$API_BASE/$RESOURCE_SEGMENT/not-a-uuid")

        assertEquals(HttpStatusCode.BadRequest, response.status)
        val body = Json.decodeFromString<ProblemDetailsResponse>(response.bodyAsText())
        assertEquals("Invalid UUID format", body.detail)
    }

    @Test
    fun `test create objectinformatieobject with invalid json returns 400`() = testApplication {
        application { testModule() }

        val response = client.post("$API_BASE/$RESOURCE_SEGMENT") {
            contentType(ContentType.Application.Json)
            setBody("{ \"invalid\": \"json\" }")
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
        val body = Json.decodeFromString<ProblemDetailsResponse>(response.bodyAsText())
        assertEquals(HttpStatusCode.BadRequest.value, body.status)
        assertEquals("Bad Request", body.title)
        assertTrue(body.detail?.contains("Invalid request body") == true)
    }

    @Test
    fun `test head objectinformatieobject returns 200 for existing`() = testApplication {
        application { testModule() }

        val eioId = createTestEIO()
        val request = CreateOIORequest(
            informatieobject = "$API_BASE/enkelvoudiginformatieobjecten/$eioId",
            subjectObject = "https://example.com/zaken/api/v1/zaken/1",
            subjectType = SubjectTypeEnum.ZAAK
        )

        val createResponse = client.post("$API_BASE/$RESOURCE_SEGMENT") {
            contentType(ContentType.Application.Json)
            setBody(Json.encodeToString(CreateOIORequest.serializer(), request))
        }
        val createBody = Json.decodeFromString<ObjectInformatieObjectResponse>(createResponse.bodyAsText())
        val id = createBody.url?.substringAfterLast("/") ?: error("No ID")

        val response = client.head("$API_BASE/$RESOURCE_SEGMENT/$id")
        assertEquals(HttpStatusCode.OK, response.status)
    }

    @Test
    fun `test head objectinformatieobject returns 404 for non-existing`() = testApplication {
        application { testModule() }

        val response = client.head("$API_BASE/$RESOURCE_SEGMENT/${UUID.randomUUID()}")
        assertEquals(HttpStatusCode.NotFound, response.status)
    }
}
