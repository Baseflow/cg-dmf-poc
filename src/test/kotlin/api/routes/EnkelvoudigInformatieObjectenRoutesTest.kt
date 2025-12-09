// SPDX-License-Identifier: EUPL-1.2
// Copyright (C) 2025 Gemeente Utrecht
package com.baseflow.api.routes

import com.baseflow.EIORecords
import com.baseflow.EIOVersions
import com.baseflow.api.models.CreateEIORequest
import com.baseflow.api.models.EnkelvoudigInformatieObjectResponse
import io.ktor.http.*
import io.ktor.server.testing.*
import io.ktor.server.routing.*
import io.ktor.server.application.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.SchemaUtils

class EnkelvoudigInformatieObjectenRoutesTest {
    companion object {
        private const val API_BASE = "/documenten/api/v1"
    }

    private fun Application.testModule() {
        Database.connect(
            "jdbc:h2:mem:test;DB_CLOSE_DELAY=-1;",
            driver = "org.h2.Driver",
            user = "root",
            password = ""
        )
        transaction {
            SchemaUtils.create(EIORecords, EIOVersions)
        }
        install(ContentNegotiation) {
            json()
        }
        routing {
            route(API_BASE) {
                route("/enkelvoudiginformatieobjecten") {
                    enkelvoudigInformatieObjectenRoutes()
                }
            }
        }
    }

    @Test
    fun `test POST enkelvoudiginformatieobjecten with taal and bestandsnaam`() = testApplication {
        application { testModule() }

        val request = CreateEIORequest(
            taal = "dut",
            bestandsnaam = "test.pdf"
        )
        val response = client.post("$API_BASE/enkelvoudiginformatieobjecten") {
            contentType(ContentType.Application.Json)
            setBody(Json.encodeToString(request))
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val responseBody = Json.decodeFromString<EnkelvoudigInformatieObjectResponse>(response.bodyAsText())
        assertEquals("dut", responseBody.taal)
        assertEquals("test.pdf", responseBody.bestandsnaam)
        assertEquals(1, responseBody.versie)
        assert(responseBody.identificatie.isNotEmpty()) // UUID should be generated
    }

    @Test
    fun `test GET enkelvoudiginformatieobjecten with invalid UUID`() = testApplication {
        application { testModule() }

        val response = client.get("$API_BASE/enkelvoudiginformatieobjecten/invalid-uuid")

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `test GET enkelvoudiginformatieobjecten with missing UUID parameter`() = testApplication {
        application { testModule() }

        val response = client.get("$API_BASE/enkelvoudiginformatieobjecten/")

        assertEquals(HttpStatusCode.NotFound, response.status) // Ktor returns 404 for missing path parameter
    }

    @Test
    fun `test GET enkelvoudiginformatieobjecten with valid UUID after creation`() = testApplication {
        application { testModule() }

        val request = CreateEIORequest(
            taal = "dut",
            bestandsnaam = "test.pdf"
        )
        val postResponse = client.post("$API_BASE/enkelvoudiginformatieobjecten") {
            contentType(ContentType.Application.Json)
            setBody(Json.encodeToString(request))
        }
        assertEquals(HttpStatusCode.OK, postResponse.status)
        val created = Json.decodeFromString<EnkelvoudigInformatieObjectResponse>(postResponse.bodyAsText())
        val uuid = created.identificatie

        val getResponse = client.get("$API_BASE/enkelvoudiginformatieobjecten/$uuid")
        assertEquals(HttpStatusCode.OK, getResponse.status)
        val fetched = Json.decodeFromString<EnkelvoudigInformatieObjectResponse>(getResponse.bodyAsText())
        assertEquals(uuid, fetched.identificatie)
        assertEquals("dut", fetched.taal)
        assertEquals("test.pdf", fetched.bestandsnaam)
        assertEquals(1, fetched.versie)
    }

    @Test
    fun `test GET enkelvoudiginformatieobjecten with valid UUID returns JSON UTF-8`() = testApplication {
        application { testModule() }

        val request = CreateEIORequest(
            taal = "dut",
            bestandsnaam = "test.pdf"
        )
        val postResponse = client.post("$API_BASE/enkelvoudiginformatieobjecten") {
            contentType(ContentType.Application.Json)
            setBody(Json.encodeToString(request))
        }
        assertEquals(HttpStatusCode.OK, postResponse.status)
        val created = Json.decodeFromString<EnkelvoudigInformatieObjectResponse>(postResponse.bodyAsText())
        val uuid = created.identificatie

        val getResponse = client.get("$API_BASE/enkelvoudiginformatieobjecten/$uuid")
        assertEquals(HttpStatusCode.OK, getResponse.status)
        val contentType = getResponse.headers[HttpHeaders.ContentType]
        assertEquals(ContentType.Application.Json.withCharset(Charsets.UTF_8).toString(), contentType)
        val fetched = Json.decodeFromString<EnkelvoudigInformatieObjectResponse>(getResponse.bodyAsText())
        assertEquals(uuid, fetched.identificatie)
        assertEquals("dut", fetched.taal)
        assertEquals("test.pdf", fetched.bestandsnaam)
        assertEquals(1, fetched.versie)
    }
}
