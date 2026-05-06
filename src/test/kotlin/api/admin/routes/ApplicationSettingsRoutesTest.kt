// SPDX-License-Identifier: EUPL-1.2
// Copyright (C) 2026 Gemeente Utrecht
package com.baseflow.api.admin.routes

import com.baseflow.api.documenten.routes.TestBase
import com.baseflow.config.SecretCrypto
import com.baseflow.entities.settings.ApplicationSettingEntity
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
import kotlin.test.assertTrue

class ApplicationSettingsRoutesTest : TestBase("application_settings") {

    @Test
    fun `GET returns empty list when no applications configured`() = testApplication {
        application { setup() }

        val response = client.get("/admin/application-settings") {
            header(HttpHeaders.Authorization, "Bearer test")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val body = Json.parseToJsonElement(response.bodyAsText()).jsonArray
        assertEquals(0, body.size)
    }

    @Test
    fun `GET returns all configured applications`() = testApplication {
        application { setup() }

        val id = UUID.randomUUID()
        transaction {
            ApplicationSettingEntity.new(id) {
                name = "My App"
                clientId = "my-client"
                clientSecretEncrypted = null
                updatedAt = Clock.System.now().toLocalDateTime(TimeZone.UTC)
            }
        }

        val response = client.get("/admin/application-settings") {
            header(HttpHeaders.Authorization, "Bearer test")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val body = Json.parseToJsonElement(response.bodyAsText()).jsonArray
        assertEquals(1, body.size)
        assertEquals("My App", body[0].jsonObject["name"]?.jsonPrimitive?.content)
        assertEquals("my-client", body[0].jsonObject["clientId"]?.jsonPrimitive?.content)
        assertEquals(id.toString(), body[0].jsonObject["id"]?.jsonPrimitive?.content)
    }

    @Test
    fun `POST creates an application without secret`() = testApplication {
        application { setup() }

        val response = client.post("/admin/application-settings") {
            header(HttpHeaders.Authorization, "Bearer test")
            contentType(ContentType.Application.Json)
            setBody("""{"name":"My App","clientId":"my-client"}""")
        }

        assertEquals(HttpStatusCode.Created, response.status)
        val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
        assertEquals("My App", body["name"]?.jsonPrimitive?.content)
        assertEquals("my-client", body["clientId"]?.jsonPrimitive?.content)
        assertEquals(false, body["hasSecret"]?.jsonPrimitive?.boolean)
        assertNotNull(body["id"]?.jsonPrimitive?.content)
    }

    @Test
    fun `POST creates an application with secret`() = testApplication {
        application { setup() }

        val response = client.post("/admin/application-settings") {
            header(HttpHeaders.Authorization, "Bearer test")
            contentType(ContentType.Application.Json)
            setBody("""{"name":"My App","clientId":"my-client","clientSecret":"supersecret"}""")
        }

        assertEquals(HttpStatusCode.Created, response.status)
        val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
        assertEquals(true, body["hasSecret"]?.jsonPrimitive?.boolean)
    }

    @Test
    fun `POST returns 409 when name already exists`() = testApplication {
        application { setup() }

        client.post("/admin/application-settings") {
            header(HttpHeaders.Authorization, "Bearer test")
            contentType(ContentType.Application.Json)
            setBody("""{"name":"Duplicate","clientId":"client-1"}""")
        }

        val response = client.post("/admin/application-settings") {
            header(HttpHeaders.Authorization, "Bearer test")
            contentType(ContentType.Application.Json)
            setBody("""{"name":"Duplicate","clientId":"client-2"}""")
        }

        assertEquals(HttpStatusCode.Conflict, response.status)
    }

    @Test
    fun `POST returns 400 when name is blank`() = testApplication {
        application { setup() }

        val response = client.post("/admin/application-settings") {
            header(HttpHeaders.Authorization, "Bearer test")
            contentType(ContentType.Application.Json)
            setBody("""{"name":"","clientId":"my-client"}""")
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `POST returns 400 when clientId is blank`() = testApplication {
        application { setup() }

        val response = client.post("/admin/application-settings") {
            header(HttpHeaders.Authorization, "Bearer test")
            contentType(ContentType.Application.Json)
            setBody("""{"name":"My App","clientId":""}""")
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `POST returns 400 when body is missing required fields`() = testApplication {
        application { setup() }

        val response = client.post("/admin/application-settings") {
            header(HttpHeaders.Authorization, "Bearer test")
            contentType(ContentType.Application.Json)
            setBody("""{"clientId":"my-client"}""")
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `PUT updates an existing application`() = testApplication {
        application { setup() }

        val id = UUID.randomUUID()
        transaction {
            ApplicationSettingEntity.new(id) {
                name = "Old Name"
                clientId = "old-client"
                clientSecretEncrypted = null
                updatedAt = Clock.System.now().toLocalDateTime(TimeZone.UTC)
            }
        }

        val response = client.put("/admin/application-settings/$id") {
            header(HttpHeaders.Authorization, "Bearer test")
            contentType(ContentType.Application.Json)
            setBody("""{"name":"New Name","clientId":"new-client"}""")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
        assertEquals("New Name", body["name"]?.jsonPrimitive?.content)
        assertEquals("new-client", body["clientId"]?.jsonPrimitive?.content)
        assertEquals(false, body["hasSecret"]?.jsonPrimitive?.boolean)
    }

    @Test
    fun `PUT preserves existing secret when clientSecret is omitted`() = testApplication {
        application { setup() }

        val id = UUID.randomUUID()
        transaction {
            ApplicationSettingEntity.new(id) {
                name = "My App"
                clientId = "my-client"
                clientSecretEncrypted = SecretCrypto.encrypt("original-secret")
                updatedAt = Clock.System.now().toLocalDateTime(TimeZone.UTC)
            }
        }

        val response = client.put("/admin/application-settings/$id") {
            header(HttpHeaders.Authorization, "Bearer test")
            contentType(ContentType.Application.Json)
            setBody("""{"name":"My App","clientId":"my-client"}""")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
        assertEquals(true, body["hasSecret"]?.jsonPrimitive?.boolean)
    }

    @Test
    fun `PUT returns 404 for unknown id`() = testApplication {
        application { setup() }

        val response = client.put("/admin/application-settings/${UUID.randomUUID()}") {
            header(HttpHeaders.Authorization, "Bearer test")
            contentType(ContentType.Application.Json)
            setBody("""{"name":"New Name","clientId":"new-client"}""")
        }

        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    @Test
    fun `PUT returns 409 when renaming to a name that already exists`() = testApplication {
        application { setup() }

        val idA = UUID.randomUUID()
        val idB = UUID.randomUUID()
        transaction {
            ApplicationSettingEntity.new(idA) {
                name = "App A"
                clientId = "client-a"
                clientSecretEncrypted = null
                updatedAt = Clock.System.now().toLocalDateTime(TimeZone.UTC)
            }
            ApplicationSettingEntity.new(idB) {
                name = "App B"
                clientId = "client-b"
                clientSecretEncrypted = null
                updatedAt = Clock.System.now().toLocalDateTime(TimeZone.UTC)
            }
        }

        val response = client.put("/admin/application-settings/$idB") {
            header(HttpHeaders.Authorization, "Bearer test")
            contentType(ContentType.Application.Json)
            setBody("""{"name":"App A","clientId":"client-b"}""")
        }

        assertEquals(HttpStatusCode.Conflict, response.status)
    }

    @Test
    fun `DELETE removes an application and returns 204`() = testApplication {
        application { setup() }

        val id = UUID.randomUUID()
        transaction {
            ApplicationSettingEntity.new(id) {
                name = "To Delete"
                clientId = "my-client"
                clientSecretEncrypted = null
                updatedAt = Clock.System.now().toLocalDateTime(TimeZone.UTC)
            }
        }

        val deleteResponse = client.delete("/admin/application-settings/$id") {
            header(HttpHeaders.Authorization, "Bearer test")
        }
        assertEquals(HttpStatusCode.NoContent, deleteResponse.status)

        val listResponse = client.get("/admin/application-settings") {
            header(HttpHeaders.Authorization, "Bearer test")
        }
        val body = Json.parseToJsonElement(listResponse.bodyAsText()).jsonArray
        assertEquals(0, body.size)
    }

    @Test
    fun `DELETE returns 404 for unknown id`() = testApplication {
        application { setup() }

        val response = client.delete("/admin/application-settings/${UUID.randomUUID()}") {
            header(HttpHeaders.Authorization, "Bearer test")
        }

        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    @Test
    fun `POST rotate-secret with provided secret stores and returns it`() = testApplication {
        application { setup() }

        val id = UUID.randomUUID()
        transaction {
            ApplicationSettingEntity.new(id) {
                name = "My App"
                clientId = "my-client"
                clientSecretEncrypted = SecretCrypto.encrypt("old-secret")
                updatedAt = Clock.System.now().toLocalDateTime(TimeZone.UTC)
            }
        }

        val response = client.post("/admin/application-settings/$id/rotate-secret") {
            header(HttpHeaders.Authorization, "Bearer test")
            contentType(ContentType.Application.Json)
            setBody("""{"newSecret":"new-secret-value"}""")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
        assertEquals("new-secret-value", body["secret"]?.jsonPrimitive?.content)

        // Verify it was persisted encrypted
        val stored = transaction { ApplicationSettingEntity.findById(id)?.clientSecretEncrypted }
        assertNotNull(stored)
        assertEquals("new-secret-value", SecretCrypto.decrypt(stored))
    }

    @Test
    fun `POST rotate-secret with no body auto-generates a secret`() = testApplication {
        application { setup() }

        val id = UUID.randomUUID()
        transaction {
            ApplicationSettingEntity.new(id) {
                name = "My App"
                clientId = "my-client"
                clientSecretEncrypted = null
                updatedAt = Clock.System.now().toLocalDateTime(TimeZone.UTC)
            }
        }

        val response = client.post("/admin/application-settings/$id/rotate-secret") {
            header(HttpHeaders.Authorization, "Bearer test")
            contentType(ContentType.Application.Json)
            setBody("{}")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
        val secret = body["secret"]?.jsonPrimitive?.content
        assertNotNull(secret)
        assertTrue(secret.length == 64, "Auto-generated secret should be 64 hex chars (32 bytes)")
    }

    @Test
    fun `POST rotate-secret returns 404 for unknown id`() = testApplication {
        application { setup() }

        val response = client.post("/admin/application-settings/${UUID.randomUUID()}/rotate-secret") {
            header(HttpHeaders.Authorization, "Bearer test")
            contentType(ContentType.Application.Json)
            setBody("{}")
        }

        assertEquals(HttpStatusCode.NotFound, response.status)
    }
}
