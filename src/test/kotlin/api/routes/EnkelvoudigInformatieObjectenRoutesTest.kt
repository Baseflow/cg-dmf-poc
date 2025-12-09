// SPDX-License-Identifier: EUPL-1.2
// Copyright (C) 2025 Gemeente Utrecht
package com.baseflow.api.routes

import com.baseflow.EIORecords
import com.baseflow.EIOVersions
import com.baseflow.api.models.CreateEIORequest
import com.baseflow.api.models.EnkelvoudigInformatieObjectResponse
import com.baseflow.api.models.UnlockEIORequest
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
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.SchemaUtils

class EnkelvoudigInformatieObjectenRoutesTest {
    companion object {
        private const val API_BASE = "/documenten/api/v1"
    }

    @Serializable
    private data class LockPayload(val lock: String)

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

    private fun generateTestDocument(
        taal: String = "dut",
        bestandsnaam: String = "test.pdf",
        titel: String = "test",
        auteur: String = "auteur"
    ): CreateEIORequest {
        return CreateEIORequest(
            taal = taal,
            bestandsnaam = bestandsnaam,
            titel = titel,
            auteur = auteur
        )
    }

    @Test
    fun `test POST enkelvoudiginformatieobjecten with taal and bestandsnaam`() = testApplication {
        application { testModule() }

        val request = generateTestDocument();
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

        val request = generateTestDocument()
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

        val request = generateTestDocument()
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

    @Test
    fun `lock returns 200 with lock token, second lock returns 409`() = testApplication {
        application { testModule() }

        // Create an object first
        val createReq = generateTestDocument()
        val postResp = client.post("$API_BASE/enkelvoudiginformatieobjecten") {
            contentType(ContentType.Application.Json)
            setBody(Json.encodeToString(createReq))
        }
        assertEquals(HttpStatusCode.OK, postResp.status)
        val created = Json.decodeFromString<EnkelvoudigInformatieObjectResponse>(postResp.bodyAsText())

        // Lock it
        val lockResp = client.post("$API_BASE/enkelvoudiginformatieobjecten/${created.identificatie}/lock")
        assertEquals(HttpStatusCode.OK, lockResp.status)
        val lockContentType = lockResp.headers[HttpHeaders.ContentType]
        assertEquals(ContentType.Application.Json.withCharset(Charsets.UTF_8).toString(), lockContentType)
        val payload = Json.decodeFromString<LockPayload>(lockResp.bodyAsText())
        assert(payload.lock.isNotBlank())

        // Lock again -> Conflict
        val secondLock = client.post("$API_BASE/enkelvoudiginformatieobjecten/${created.identificatie}/lock")
        assertEquals(HttpStatusCode.Conflict, secondLock.status)
    }

    @Test
    fun `unlock with correct token returns 204 and subsequent unlock returns 409`() = testApplication {
        application { testModule() }

        // Create
        val createReq = generateTestDocument()
        val postResp = client.post("$API_BASE/enkelvoudiginformatieobjecten") {
            contentType(ContentType.Application.Json)
            setBody(Json.encodeToString(createReq))
        }
        val created = Json.decodeFromString<EnkelvoudigInformatieObjectResponse>(postResp.bodyAsText())

        // Lock to obtain token
        val lockResp = client.post("$API_BASE/enkelvoudiginformatieobjecten/${created.identificatie}/lock")
        val payload = Json.decodeFromString<LockPayload>(lockResp.bodyAsText())

        // Unlock with correct token
        val unlockReq = UnlockEIORequest(lock = payload.lock)
        val unlockResp = client.post("$API_BASE/enkelvoudiginformatieobjecten/${created.identificatie}/unlock") {
            contentType(ContentType.Application.Json)
            setBody(Json.encodeToString(unlockReq))
        }
        val lockContentType = lockResp.headers[HttpHeaders.ContentType]
        assertEquals(ContentType.Application.Json.withCharset(Charsets.UTF_8).toString(), lockContentType)
        assertEquals(HttpStatusCode.NoContent, unlockResp.status)

        // Unlock again -> NotLocked (409)
        val secondUnlock = client.post("$API_BASE/enkelvoudiginformatieobjecten/${created.identificatie}/unlock") {
            contentType(ContentType.Application.Json)
            setBody(Json.encodeToString(unlockReq))
        }
        assertEquals(HttpStatusCode.Conflict, secondUnlock.status)
    }

    @Test
    fun `unlock with invalid token returns 409 Conflict`() = testApplication {
        application { testModule() }

        // Create and lock
        val createReq = generateTestDocument()
        val postResp = client.post("$API_BASE/enkelvoudiginformatieobjecten") {
            contentType(ContentType.Application.Json)
            setBody(Json.encodeToString(createReq))
        }
        val created = Json.decodeFromString<EnkelvoudigInformatieObjectResponse>(postResp.bodyAsText())
        val lockResp = client.post("$API_BASE/enkelvoudiginformatieobjecten/${created.identificatie}/lock")
        val payload = Json.decodeFromString<LockPayload>(lockResp.bodyAsText())

        // Try unlock with wrong token
        val unlockReq = UnlockEIORequest(lock = payload.lock + "-wrong")
        val unlockResp = client.post("$API_BASE/enkelvoudiginformatieobjecten/${created.identificatie}/unlock") {
            contentType(ContentType.Application.Json)
            setBody(Json.encodeToString(unlockReq))
        }
        assertEquals(HttpStatusCode.Conflict, unlockResp.status)
    }

    @Test
    fun `lock and unlock with unknown UUID return 404`() = testApplication {
        application { testModule() }

        val unknownId = "00000000-0000-0000-0000-000000000001"

        val lockResp = client.post("$API_BASE/enkelvoudiginformatieobjecten/$unknownId/lock")
        assertEquals(HttpStatusCode.NotFound, lockResp.status)

        val unlockReq = UnlockEIORequest(lock = "any")
        val unlockResp = client.post("$API_BASE/enkelvoudiginformatieobjecten/$unknownId/unlock") {
            contentType(ContentType.Application.Json)
            setBody(Json.encodeToString(unlockReq))
        }
        assertEquals(HttpStatusCode.NotFound, unlockResp.status)
    }

    @Test
    fun `lock and unlock with invalid UUID return 400`() = testApplication {
        application { testModule() }

        val invalidId = "not-a-uuid"

        val lockResp = client.post("$API_BASE/enkelvoudiginformatieobjecten/$invalidId/lock")
        assertEquals(HttpStatusCode.BadRequest, lockResp.status)

        val unlockReq = UnlockEIORequest(lock = "any")
        val unlockResp = client.post("$API_BASE/enkelvoudiginformatieobjecten/$invalidId/unlock") {
            contentType(ContentType.Application.Json)
            setBody(Json.encodeToString(unlockReq))
        }
        assertEquals(HttpStatusCode.BadRequest, unlockResp.status)
    }
}
