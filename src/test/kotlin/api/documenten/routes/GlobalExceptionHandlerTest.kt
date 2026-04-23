// SPDX-License-Identifier: EUPL-1.2
// Copyright (C) 2026 Gemeente Utrecht
package com.baseflow.api.documenten.routes

import com.baseflow.api.DOCUMENTEN_API_BASE_PATH
import com.baseflow.api.models.ResourceSegments
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GlobalExceptionHandlerTest : TestBase("global_exception_test") {
    private val API_BASE = DOCUMENTEN_API_BASE_PATH
    private val RESOURCE_SEGMENT = ResourceSegments.ENKELVOUDIG_INFORMATIE_OBJECTEN.value

    @Test
    fun `test malformed JSON returns ProblemDetailsResponse`() = testApplication {
        application { setup() }

        val malformedJson = "{ \"identificatie\": \"test\", \"bronorganisatie\": \"012345678\", " // Missing closing brace and other fields

        val response = client.post("$API_BASE/$RESOURCE_SEGMENT") {
            contentType(ContentType.Application.Json)
            setBody(malformedJson)
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
        val body = response.bodyAsText()
        val problem = Json.parseToJsonElement(body).jsonObject
        assertEquals("Bad Request", problem["title"]?.jsonPrimitive?.content)
        assertEquals(400, problem["status"]?.jsonPrimitive?.content?.toInt())
    }

    @Test
    fun `test invalid objectType value in JSON returns specific error message`() = testApplication {
        application { setup() }

        // OIO request with an objectType containing spaces, which is invalid per SubjectType validation
        val invalidJson = """
            {
                "informatieobject": "https://example.com/documenten/api/v1/enkelvoudiginformatieobjecten/12345678-1234-1234-1234-123456789012",
                "object": "https://example.com/zaken/api/v1/zaken/87654321-4321-4321-4321-210987654321",
                "objectType": "INVALID TYPE WITH SPACES"
            }
        """

        val response = client.post("$API_BASE/objectinformatieobjecten") {
            contentType(ContentType.Application.Json)
            setBody(invalidJson)
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
        val body = response.bodyAsText()
        println("[DEBUG_LOG] Response Body for invalid objectType: $body")
        val problem = Json.parseToJsonElement(body).jsonObject

        val detail = problem["detail"]?.jsonPrimitive?.content ?: ""
        // We expect something like "Invalid request body: ..."
        assertTrue(detail.contains("Invalid request body"), "Detail should mention invalid request body. Got: $detail")
    }

    @Test
    fun `test response serialization error returns 500`() = testApplication {
        application {
            setup()
            routing {
                get("/test-serialization-error") {
                    // Trigger a serialization error manually to simulate a failure during response serialization
                    throw SerializationException("Mock response serialization error")
                }
            }
        }

        val response = client.get("/test-serialization-error")

        // Serialization errors during response should technically be 500s because it's a server failure to format data
        assertEquals(HttpStatusCode.InternalServerError, response.status)
        val body = response.bodyAsText()
        val problem = Json.parseToJsonElement(body).jsonObject
        assertEquals("Internal Server Error", problem["title"]?.jsonPrimitive?.content)
        assertTrue(problem["detail"]?.jsonPrimitive?.content?.contains("Mock response serialization error") == true)
    }
}
