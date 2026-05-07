// SPDX-License-Identifier: EUPL-1.2
// Copyright (C) 2026 Gemeente Utrecht
package com.baseflow.api.settings.routes

import com.baseflow.api.apiJsonConfig
import com.baseflow.api.models.settings.CreateOidcProviderSettingsRequest
import com.baseflow.api.models.settings.OidcProviderSettingsResponse
import com.baseflow.api.models.settings.UpdateOidcProviderSettingsRequest
import com.baseflow.config.SecretCrypto
import com.baseflow.entities.settings.OidcProviderSettingEntity
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class OidcProviderSettingsRoutesTest : SettingsTestBase("oidc_provider_settings") {

    private val json = apiJsonConfig()

    private fun insertProvider(
        name: String,
        issuer: String = "https://issuer.example.com",
        clientId: String = "client-id",
        clientSecret: String? = null,
    ): UUID = transaction {
        OidcProviderSettingEntity.new {
            this.name = name
            this.issuer = issuer
            this.clientId = clientId
            this.clientSecretEncrypted = clientSecret?.let { SecretCrypto.encrypt(it) }
            this.updatedAt = Clock.System.now().toLocalDateTime(TimeZone.UTC)
        }.id.value
    }

    // -----------------------------------------------------------------------
    // GET /settings/oidc-providers
    // -----------------------------------------------------------------------

    @Test
    fun `GET returns empty array when no providers exist`() = testApplication {
        application { setup() }

        val response = client.get("/settings/oidc-providers")

        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(json.decodeFromString<List<OidcProviderSettingsResponse>>(response.bodyAsText()).isEmpty())
    }

    @Test
    fun `GET returns all providers`() = testApplication {
        application { setup() }
        insertProvider("provider-a")
        insertProvider("provider-b")

        val response = client.get("/settings/oidc-providers")

        assertEquals(HttpStatusCode.OK, response.status)
        val body = json.decodeFromString<List<OidcProviderSettingsResponse>>(response.bodyAsText())
        assertEquals(2, body.size)
        assertTrue(body.map { it.name }.containsAll(listOf("provider-a", "provider-b")))
    }

    @Test
    fun `GET hasSecret is false when no secret stored`() = testApplication {
        application { setup() }
        insertProvider("provider")

        val response = client.get("/settings/oidc-providers")

        assertEquals(HttpStatusCode.OK, response.status)
        assertFalse(json.decodeFromString<List<OidcProviderSettingsResponse>>(response.bodyAsText()).first().hasSecret)
    }

    @Test
    fun `GET hasSecret is true when secret is stored`() = testApplication {
        application { setup() }
        insertProvider("provider", clientSecret = "my-secret")

        val response = client.get("/settings/oidc-providers")

        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(json.decodeFromString<List<OidcProviderSettingsResponse>>(response.bodyAsText()).first().hasSecret)
    }

    // -----------------------------------------------------------------------
    // POST /settings/oidc-providers
    // -----------------------------------------------------------------------

    @Test
    fun `POST creates provider and returns 201`() = testApplication {
        application { setup() }

        val response = client.post("/settings/oidc-providers") {
            contentType(ContentType.Application.Json)
            setBody(Json.encodeToString(CreateOidcProviderSettingsRequest.serializer(),
                CreateOidcProviderSettingsRequest(name = "new-provider", issuer = "https://issuer.example.com", clientId = "my-client")))
        }

        assertEquals(HttpStatusCode.Created, response.status)
        val body = json.decodeFromString<OidcProviderSettingsResponse>(response.bodyAsText())
        assertNotNull(body.id)
        assertEquals("new-provider", body.name)
        assertEquals("https://issuer.example.com", body.issuer)
    }

    @Test
    fun `POST returns 400 when name is blank`() = testApplication {
        application { setup() }

        val response = client.post("/settings/oidc-providers") {
            contentType(ContentType.Application.Json)
            setBody(Json.encodeToString(CreateOidcProviderSettingsRequest.serializer(),
                CreateOidcProviderSettingsRequest(name = "", issuer = "https://issuer.example.com", clientId = "my-client")))
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `POST returns 400 when issuer is blank`() = testApplication {
        application { setup() }

        val response = client.post("/settings/oidc-providers") {
            contentType(ContentType.Application.Json)
            setBody(Json.encodeToString(CreateOidcProviderSettingsRequest.serializer(),
                CreateOidcProviderSettingsRequest(name = "provider", issuer = "", clientId = "my-client")))
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `POST returns 400 when clientId is blank`() = testApplication {
        application { setup() }

        val response = client.post("/settings/oidc-providers") {
            contentType(ContentType.Application.Json)
            setBody(Json.encodeToString(CreateOidcProviderSettingsRequest.serializer(),
                CreateOidcProviderSettingsRequest(name = "provider", issuer = "https://issuer.example.com", clientId = "")))
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `POST returns 400 for malformed JSON`() = testApplication {
        application { setup() }

        val response = client.post("/settings/oidc-providers") {
            contentType(ContentType.Application.Json)
            setBody("not-json")
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `POST returns 409 when name already exists`() = testApplication {
        application { setup() }
        insertProvider("duplicate-provider")

        val response = client.post("/settings/oidc-providers") {
            contentType(ContentType.Application.Json)
            setBody(Json.encodeToString(CreateOidcProviderSettingsRequest.serializer(),
                CreateOidcProviderSettingsRequest(name = "duplicate-provider", issuer = "https://issuer.example.com", clientId = "my-client")))
        }

        assertEquals(HttpStatusCode.Conflict, response.status)
    }

    // -----------------------------------------------------------------------
    // PUT /settings/oidc-providers/{id}
    // -----------------------------------------------------------------------

    @Test
    fun `PUT updates provider and returns 200`() = testApplication {
        application { setup() }
        val id = insertProvider("original-provider")

        val response = client.put("/settings/oidc-providers/$id") {
            contentType(ContentType.Application.Json)
            setBody(Json.encodeToString(UpdateOidcProviderSettingsRequest.serializer(),
                UpdateOidcProviderSettingsRequest(name = "updated-provider", issuer = "https://new-issuer.example.com", clientId = "new-client")))
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val body = json.decodeFromString<OidcProviderSettingsResponse>(response.bodyAsText())
        assertEquals("updated-provider", body.name)
        assertEquals("https://new-issuer.example.com", body.issuer)
    }

    @Test
    fun `PUT returns 400 for invalid UUID`() = testApplication {
        application { setup() }

        val response = client.put("/settings/oidc-providers/not-a-uuid") {
            contentType(ContentType.Application.Json)
            setBody(Json.encodeToString(UpdateOidcProviderSettingsRequest.serializer(),
                UpdateOidcProviderSettingsRequest(name = "name", issuer = "https://issuer.example.com", clientId = "client")))
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `PUT returns 400 when name is blank`() = testApplication {
        application { setup() }
        val id = insertProvider("provider")

        val response = client.put("/settings/oidc-providers/$id") {
            contentType(ContentType.Application.Json)
            setBody(Json.encodeToString(UpdateOidcProviderSettingsRequest.serializer(),
                UpdateOidcProviderSettingsRequest(name = "", issuer = "https://issuer.example.com", clientId = "client")))
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `PUT returns 400 when issuer is blank`() = testApplication {
        application { setup() }
        val id = insertProvider("provider")

        val response = client.put("/settings/oidc-providers/$id") {
            contentType(ContentType.Application.Json)
            setBody(Json.encodeToString(UpdateOidcProviderSettingsRequest.serializer(),
                UpdateOidcProviderSettingsRequest(name = "provider", issuer = "", clientId = "client")))
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `PUT returns 400 when clientId is blank`() = testApplication {
        application { setup() }
        val id = insertProvider("provider")

        val response = client.put("/settings/oidc-providers/$id") {
            contentType(ContentType.Application.Json)
            setBody(Json.encodeToString(UpdateOidcProviderSettingsRequest.serializer(),
                UpdateOidcProviderSettingsRequest(name = "provider", issuer = "https://issuer.example.com", clientId = "")))
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `PUT returns 404 when provider does not exist`() = testApplication {
        application { setup() }

        val response = client.put("/settings/oidc-providers/${UUID.randomUUID()}") {
            contentType(ContentType.Application.Json)
            setBody(Json.encodeToString(UpdateOidcProviderSettingsRequest.serializer(),
                UpdateOidcProviderSettingsRequest(name = "name", issuer = "https://issuer.example.com", clientId = "client")))
        }

        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    @Test
    fun `PUT returns 409 when renaming to an existing name`() = testApplication {
        application { setup() }
        insertProvider("provider-a")
        val idB = insertProvider("provider-b")

        val response = client.put("/settings/oidc-providers/$idB") {
            contentType(ContentType.Application.Json)
            setBody(Json.encodeToString(UpdateOidcProviderSettingsRequest.serializer(),
                UpdateOidcProviderSettingsRequest(name = "provider-a", issuer = "https://issuer.example.com", clientId = "client")))
        }

        assertEquals(HttpStatusCode.Conflict, response.status)
    }

    @Test
    fun `PUT does not overwrite secret when clientSecret is omitted`() = testApplication {
        application { setup() }
        val id = insertProvider("provider", clientSecret = "original-secret")

        val response = client.put("/settings/oidc-providers/$id") {
            contentType(ContentType.Application.Json)
            setBody(Json.encodeToString(UpdateOidcProviderSettingsRequest.serializer(),
                UpdateOidcProviderSettingsRequest(name = "provider", issuer = "https://issuer.example.com", clientId = "client", clientSecret = null)))
        }
        assertEquals(HttpStatusCode.OK, response.status)

        transaction {
            val entity = OidcProviderSettingEntity.findById(id)!!
            assertNotNull(entity.clientSecretEncrypted)
            assertEquals("original-secret", SecretCrypto.decrypt(entity.clientSecretEncrypted!!))
        }
    }

    // -----------------------------------------------------------------------
    // DELETE /settings/oidc-providers/{id}
    // -----------------------------------------------------------------------

    @Test
    fun `DELETE removes provider and returns 204`() = testApplication {
        application { setup() }
        val id = insertProvider("to-delete")

        val response = client.delete("/settings/oidc-providers/$id")

        assertEquals(HttpStatusCode.NoContent, response.status)
        transaction { assertEquals(null, OidcProviderSettingEntity.findById(id)) }
    }

    @Test
    fun `DELETE returns 400 for invalid UUID`() = testApplication {
        application { setup() }

        val response = client.delete("/settings/oidc-providers/not-a-uuid")

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `DELETE returns 404 when provider does not exist`() = testApplication {
        application { setup() }

        val response = client.delete("/settings/oidc-providers/${UUID.randomUUID()}")

        assertEquals(HttpStatusCode.NotFound, response.status)
    }
}
