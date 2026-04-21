// SPDX-License-Identifier: EUPL-1.2
// Copyright (C) 2026 Gemeente Utrecht
package com.baseflow.api.admin.routes

import com.baseflow.api.routes.TestBase
import com.baseflow.config.SecretCrypto
import com.baseflow.entities.OidcProviderEntity
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class OidcProvidersRoutesTest : TestBase("oidc_providers") {

    @Test
    fun `GET returns empty list when no providers configured`() = testApplication {
        application { setup() }

        val response = client.get("/admin/oidc-providers") {
            header(HttpHeaders.Authorization, "Bearer test")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val body = Json.parseToJsonElement(response.bodyAsText()).jsonArray
        assertEquals(0, body.size)
    }

    @Test
    fun `GET returns all configured providers`() = testApplication {
        application { setup() }

        val id = UUID.randomUUID()
        transaction {
            OidcProviderEntity.new(id) {
                name = "Test Provider"
                issuer = "https://auth.example.com"
                clientId = "my-client"
                clientSecretEncrypted = null
                updatedAt = Clock.System.now().toLocalDateTime(TimeZone.UTC)
            }
        }

        val response = client.get("/admin/oidc-providers") {
            header(HttpHeaders.Authorization, "Bearer test")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val body = Json.parseToJsonElement(response.bodyAsText()).jsonArray
        assertEquals(1, body.size)
        assertEquals("Test Provider", body[0].jsonObject["name"]?.jsonPrimitive?.content)
        assertEquals("https://auth.example.com", body[0].jsonObject["issuer"]?.jsonPrimitive?.content)
        assertEquals(id.toString(), body[0].jsonObject["id"]?.jsonPrimitive?.content)
    }

    @Test
    fun `POST creates a provider without secret`() = testApplication {
        application { setup() }

        val response = client.post("/admin/oidc-providers") {
            header(HttpHeaders.Authorization, "Bearer test")
            contentType(ContentType.Application.Json)
            setBody("""{"name":"My Provider","issuer":"https://auth.example.com","clientId":"my-client"}""")
        }

        assertEquals(HttpStatusCode.Created, response.status)
        val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
        assertEquals("My Provider", body["name"]?.jsonPrimitive?.content)
        assertEquals("https://auth.example.com", body["issuer"]?.jsonPrimitive?.content)
        assertEquals("my-client", body["clientId"]?.jsonPrimitive?.content)
        assertEquals(false, body["hasSecret"]?.jsonPrimitive?.boolean)
        assertNotNull(body["id"]?.jsonPrimitive?.content)
    }

    @Test
    fun `POST returns 409 when name already exists`() = testApplication {
        application { setup() }

        client.post("/admin/oidc-providers") {
            header(HttpHeaders.Authorization, "Bearer test")
            contentType(ContentType.Application.Json)
            setBody("""{"name":"Duplicate","issuer":"https://auth.example.com","clientId":"client-1"}""")
        }

        val response = client.post("/admin/oidc-providers") {
            header(HttpHeaders.Authorization, "Bearer test")
            contentType(ContentType.Application.Json)
            setBody("""{"name":"Duplicate","issuer":"https://other.example.com","clientId":"client-2"}""")
        }

        assertEquals(HttpStatusCode.Conflict, response.status)
    }

    @Test
    fun `POST creates a provider with secret`() = testApplication {
        application { setup() }

        val response = client.post("/admin/oidc-providers") {
            header(HttpHeaders.Authorization, "Bearer test")
            contentType(ContentType.Application.Json)
            setBody("""{"name":"My Provider","issuer":"https://auth.example.com","clientId":"my-client","clientSecret":"supersecret"}""")
        }

        assertEquals(HttpStatusCode.Created, response.status)
        val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
        assertEquals(true, body["hasSecret"]?.jsonPrimitive?.boolean)
        assertEquals("supersecret", body["clientSecret"]?.jsonPrimitive?.content)
    }

    @Test
    fun `POST returns 400 when name is blank`() = testApplication {
        application { setup() }

        val response = client.post("/admin/oidc-providers") {
            header(HttpHeaders.Authorization, "Bearer test")
            contentType(ContentType.Application.Json)
            setBody("""{"name":"","issuer":"https://auth.example.com","clientId":"my-client"}""")
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `POST returns 400 when issuer is blank`() = testApplication {
        application { setup() }

        val response = client.post("/admin/oidc-providers") {
            header(HttpHeaders.Authorization, "Bearer test")
            contentType(ContentType.Application.Json)
            setBody("""{"name":"My Provider","issuer":"","clientId":"my-client"}""")
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `POST returns 400 when clientId is blank`() = testApplication {
        application { setup() }

        val response = client.post("/admin/oidc-providers") {
            header(HttpHeaders.Authorization, "Bearer test")
            contentType(ContentType.Application.Json)
            setBody("""{"name":"My Provider","issuer":"https://auth.example.com","clientId":""}""")
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `POST returns 400 when body is missing required fields`() = testApplication {
        application { setup() }

        val response = client.post("/admin/oidc-providers") {
            header(HttpHeaders.Authorization, "Bearer test")
            contentType(ContentType.Application.Json)
            setBody("""{"issuer":"https://auth.example.com"}""")
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `PUT updates an existing provider`() = testApplication {
        application { setup() }

        val id = UUID.randomUUID()
        transaction {
            OidcProviderEntity.new(id) {
                name = "Old Name"
                issuer = "https://old.example.com"
                clientId = "old-client"
                clientSecretEncrypted = null
                updatedAt = Clock.System.now().toLocalDateTime(TimeZone.UTC)
            }
        }

        val response = client.put("/admin/oidc-providers/$id") {
            header(HttpHeaders.Authorization, "Bearer test")
            contentType(ContentType.Application.Json)
            setBody("""{"name":"New Name","issuer":"https://new.example.com","clientId":"new-client"}""")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
        assertEquals("New Name", body["name"]?.jsonPrimitive?.content)
        assertEquals("https://new.example.com", body["issuer"]?.jsonPrimitive?.content)
        assertEquals("new-client", body["clientId"]?.jsonPrimitive?.content)
        assertEquals(false, body["hasSecret"]?.jsonPrimitive?.boolean)
    }

    @Test
    fun `PUT preserves existing secret when clientSecret is omitted`() = testApplication {
        application { setup() }

        val id = UUID.randomUUID()
        transaction {
            OidcProviderEntity.new(id) {
                name = "My Provider"
                issuer = "https://auth.example.com"
                clientId = "my-client"
                clientSecretEncrypted = SecretCrypto.encrypt("original-secret")
                updatedAt = Clock.System.now().toLocalDateTime(TimeZone.UTC)
            }
        }

        val response = client.put("/admin/oidc-providers/$id") {
            header(HttpHeaders.Authorization, "Bearer test")
            contentType(ContentType.Application.Json)
            setBody("""{"name":"My Provider","issuer":"https://auth.example.com","clientId":"my-client"}""")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
        assertEquals(true, body["hasSecret"]?.jsonPrimitive?.boolean)
        assertEquals("original-secret", body["clientSecret"]?.jsonPrimitive?.content)
    }

    @Test
    fun `PUT returns 404 for unknown id`() = testApplication {
        application { setup() }

        val response = client.put("/admin/oidc-providers/${UUID.randomUUID()}") {
            header(HttpHeaders.Authorization, "Bearer test")
            contentType(ContentType.Application.Json)
            setBody("""{"name":"New Name","issuer":"https://new.example.com","clientId":"new-client"}""")
        }

        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    @Test
    fun `PUT returns 409 when renaming to a name that already exists`() = testApplication {
        application { setup() }

        val idA = UUID.randomUUID()
        val idB = UUID.randomUUID()
        transaction {
            OidcProviderEntity.new(idA) {
                name = "Provider A"
                issuer = "https://auth.example.com"
                clientId = "client-a"
                clientSecretEncrypted = null
                updatedAt = Clock.System.now().toLocalDateTime(TimeZone.UTC)
            }
            OidcProviderEntity.new(idB) {
                name = "Provider B"
                issuer = "https://other.example.com"
                clientId = "client-b"
                clientSecretEncrypted = null
                updatedAt = Clock.System.now().toLocalDateTime(TimeZone.UTC)
            }
        }

        val response = client.put("/admin/oidc-providers/$idB") {
            header(HttpHeaders.Authorization, "Bearer test")
            contentType(ContentType.Application.Json)
            setBody("""{"name":"Provider A","issuer":"https://other.example.com","clientId":"client-b"}""")
        }

        assertEquals(HttpStatusCode.Conflict, response.status)
    }

    @Test
    fun `DELETE removes a provider and returns 204`() = testApplication {
        application { setup() }

        val id = UUID.randomUUID()
        transaction {
            OidcProviderEntity.new(id) {
                name = "To Delete"
                issuer = "https://auth.example.com"
                clientId = "my-client"
                clientSecretEncrypted = null
                updatedAt = Clock.System.now().toLocalDateTime(TimeZone.UTC)
            }
        }

        val deleteResponse = client.delete("/admin/oidc-providers/$id") {
            header(HttpHeaders.Authorization, "Bearer test")
        }
        assertEquals(HttpStatusCode.NoContent, deleteResponse.status)

        val listResponse = client.get("/admin/oidc-providers") {
            header(HttpHeaders.Authorization, "Bearer test")
        }
        val body = Json.parseToJsonElement(listResponse.bodyAsText()).jsonArray
        assertEquals(0, body.size)
    }

    @Test
    fun `DELETE returns 404 for unknown id`() = testApplication {
        application { setup() }

        val response = client.delete("/admin/oidc-providers/${UUID.randomUUID()}") {
            header(HttpHeaders.Authorization, "Bearer test")
        }

        assertEquals(HttpStatusCode.NotFound, response.status)
    }
}
