// SPDX-License-Identifier: EUPL-1.2
// Copyright (C) 2026 Gemeente Utrecht
package com.baseflow.api.settings.routes

import com.baseflow.api.apiJsonConfig
import com.baseflow.api.models.settings.CreateZgwApiSettingsRequest
import com.baseflow.api.models.settings.UpdateZgwApiSettingsRequest
import com.baseflow.api.models.settings.ZgwApiSettingsResponse
import com.baseflow.config.SecretCrypto
import com.baseflow.entities.settings.ZgwApiSettingEntity
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

class ZgwApiSettingsRoutesTest : SettingsTestBase("zgw_api_settings") {

    private val json = apiJsonConfig()

    private fun insertProfile(
        name: String,
        baseUrl: String = "https://api.example.com",
        clientId: String = "client-id",
        clientSecret: String? = null,
    ): UUID = transaction {
        ZgwApiSettingEntity.new {
            this.name = name
            this.baseUrl = baseUrl
            this.clientId = clientId
            this.clientSecretEncrypted = clientSecret?.let { SecretCrypto.encrypt(it) }
            this.updatedAt = Clock.System.now().toLocalDateTime(TimeZone.UTC)
        }.id.value
    }

    // -----------------------------------------------------------------------
    // GET /settings/zgw-api-settings
    // -----------------------------------------------------------------------

    @Test
    fun `GET returns empty array when no profiles exist`() = testApplication {
        application { setup() }

        val response = client.get("/settings/zgw-api-settings")

        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(json.decodeFromString<List<ZgwApiSettingsResponse>>(response.bodyAsText()).isEmpty())
    }

    @Test
    fun `GET returns all profiles`() = testApplication {
        application { setup() }
        insertProfile("profile-a")
        insertProfile("profile-b")

        val response = client.get("/settings/zgw-api-settings")

        assertEquals(HttpStatusCode.OK, response.status)
        val body = json.decodeFromString<List<ZgwApiSettingsResponse>>(response.bodyAsText())
        assertEquals(2, body.size)
        assertTrue(body.map { it.name }.containsAll(listOf("profile-a", "profile-b")))
    }

    @Test
    fun `GET hasSecret is false when no secret stored`() = testApplication {
        application { setup() }
        insertProfile("profile")

        val response = client.get("/settings/zgw-api-settings")

        assertEquals(HttpStatusCode.OK, response.status)
        assertFalse(json.decodeFromString<List<ZgwApiSettingsResponse>>(response.bodyAsText()).first().hasSecret)
    }

    @Test
    fun `GET hasSecret is true when secret is stored`() = testApplication {
        application { setup() }
        insertProfile("profile", clientSecret = "my-secret")

        val response = client.get("/settings/zgw-api-settings")

        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(json.decodeFromString<List<ZgwApiSettingsResponse>>(response.bodyAsText()).first().hasSecret)
    }

    // -----------------------------------------------------------------------
    // POST /settings/zgw-api-settings
    // -----------------------------------------------------------------------

    @Test
    fun `POST creates profile and returns 201`() = testApplication {
        application { setup() }

        val response = client.post("/settings/zgw-api-settings") {
            contentType(ContentType.Application.Json)
            setBody(
                Json.encodeToString(
                    CreateZgwApiSettingsRequest.serializer(),
                    CreateZgwApiSettingsRequest(name = "new-profile", baseUrl = "https://api.example.com", clientId = "my-client"),
                ),
            )
        }

        assertEquals(HttpStatusCode.Created, response.status)
        val body = json.decodeFromString<ZgwApiSettingsResponse>(response.bodyAsText())
        assertNotNull(body.id)
        assertEquals("new-profile", body.name)
        assertEquals("https://api.example.com", body.baseUrl)
    }

    @Test
    fun `POST returns 400 when name is blank`() = testApplication {
        application { setup() }

        val response = client.post("/settings/zgw-api-settings") {
            contentType(ContentType.Application.Json)
            setBody(
                Json.encodeToString(
                    CreateZgwApiSettingsRequest.serializer(),
                    CreateZgwApiSettingsRequest(name = "", baseUrl = "https://api.example.com", clientId = "my-client"),
                ),
            )
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `POST returns 400 when baseUrl is blank`() = testApplication {
        application { setup() }

        val response = client.post("/settings/zgw-api-settings") {
            contentType(ContentType.Application.Json)
            setBody(
                Json.encodeToString(
                    CreateZgwApiSettingsRequest.serializer(),
                    CreateZgwApiSettingsRequest(name = "profile", baseUrl = "", clientId = "my-client"),
                ),
            )
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `POST returns 400 when clientId is blank`() = testApplication {
        application { setup() }

        val response = client.post("/settings/zgw-api-settings") {
            contentType(ContentType.Application.Json)
            setBody(
                Json.encodeToString(
                    CreateZgwApiSettingsRequest.serializer(),
                    CreateZgwApiSettingsRequest(name = "profile", baseUrl = "https://api.example.com", clientId = ""),
                ),
            )
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `POST returns 400 for malformed JSON`() = testApplication {
        application { setup() }

        val response = client.post("/settings/zgw-api-settings") {
            contentType(ContentType.Application.Json)
            setBody("not-json")
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `POST returns 409 when name already exists`() = testApplication {
        application { setup() }
        insertProfile("duplicate-profile")

        val response = client.post("/settings/zgw-api-settings") {
            contentType(ContentType.Application.Json)
            setBody(
                Json.encodeToString(
                    CreateZgwApiSettingsRequest.serializer(),
                    CreateZgwApiSettingsRequest(name = "duplicate-profile", baseUrl = "https://api.example.com", clientId = "my-client"),
                ),
            )
        }

        assertEquals(HttpStatusCode.Conflict, response.status)
    }

    // -----------------------------------------------------------------------
    // PUT /settings/zgw-api-settings/{id}
    // -----------------------------------------------------------------------

    @Test
    fun `PUT updates profile and returns 200`() = testApplication {
        application { setup() }
        val id = insertProfile("original-profile")

        val response = client.put("/settings/zgw-api-settings/$id") {
            contentType(ContentType.Application.Json)
            setBody(
                Json.encodeToString(
                    UpdateZgwApiSettingsRequest.serializer(),
                    UpdateZgwApiSettingsRequest(name = "updated-profile", baseUrl = "https://new-api.example.com", clientId = "new-client"),
                ),
            )
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val body = json.decodeFromString<ZgwApiSettingsResponse>(response.bodyAsText())
        assertEquals("updated-profile", body.name)
        assertEquals("https://new-api.example.com", body.baseUrl)
    }

    @Test
    fun `PUT returns 400 for invalid UUID`() = testApplication {
        application { setup() }

        val response = client.put("/settings/zgw-api-settings/not-a-uuid") {
            contentType(ContentType.Application.Json)
            setBody(
                Json.encodeToString(
                    UpdateZgwApiSettingsRequest.serializer(),
                    UpdateZgwApiSettingsRequest(name = "name", baseUrl = "https://api.example.com", clientId = "client"),
                ),
            )
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `PUT returns 400 when name is blank`() = testApplication {
        application { setup() }
        val id = insertProfile("profile")

        val response = client.put("/settings/zgw-api-settings/$id") {
            contentType(ContentType.Application.Json)
            setBody(
                Json.encodeToString(
                    UpdateZgwApiSettingsRequest.serializer(),
                    UpdateZgwApiSettingsRequest(name = "", baseUrl = "https://api.example.com", clientId = "client"),
                ),
            )
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `PUT returns 400 when baseUrl is blank`() = testApplication {
        application { setup() }
        val id = insertProfile("profile")

        val response = client.put("/settings/zgw-api-settings/$id") {
            contentType(ContentType.Application.Json)
            setBody(
                Json.encodeToString(
                    UpdateZgwApiSettingsRequest.serializer(),
                    UpdateZgwApiSettingsRequest(name = "profile", baseUrl = "", clientId = "client"),
                ),
            )
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `PUT returns 400 when clientId is blank`() = testApplication {
        application { setup() }
        val id = insertProfile("profile")

        val response = client.put("/settings/zgw-api-settings/$id") {
            contentType(ContentType.Application.Json)
            setBody(
                Json.encodeToString(
                    UpdateZgwApiSettingsRequest.serializer(),
                    UpdateZgwApiSettingsRequest(name = "profile", baseUrl = "https://api.example.com", clientId = ""),
                ),
            )
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `PUT returns 404 when profile does not exist`() = testApplication {
        application { setup() }

        val response = client.put("/settings/zgw-api-settings/${UUID.randomUUID()}") {
            contentType(ContentType.Application.Json)
            setBody(
                Json.encodeToString(
                    UpdateZgwApiSettingsRequest.serializer(),
                    UpdateZgwApiSettingsRequest(name = "name", baseUrl = "https://api.example.com", clientId = "client"),
                ),
            )
        }

        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    @Test
    fun `PUT returns 409 when renaming to an existing name`() = testApplication {
        application { setup() }
        insertProfile("profile-a")
        val idB = insertProfile("profile-b")

        val response = client.put("/settings/zgw-api-settings/$idB") {
            contentType(ContentType.Application.Json)
            setBody(
                Json.encodeToString(
                    UpdateZgwApiSettingsRequest.serializer(),
                    UpdateZgwApiSettingsRequest(name = "profile-a", baseUrl = "https://api.example.com", clientId = "client"),
                ),
            )
        }

        assertEquals(HttpStatusCode.Conflict, response.status)
    }

    @Test
    fun `PUT does not overwrite secret when clientSecret is omitted`() = testApplication {
        application { setup() }
        val id = insertProfile("profile", clientSecret = "original-secret")

        val response = client.put("/settings/zgw-api-settings/$id") {
            contentType(ContentType.Application.Json)
            setBody(
                Json.encodeToString(
                    UpdateZgwApiSettingsRequest.serializer(),
                    UpdateZgwApiSettingsRequest(
                        name = "profile",
                        baseUrl = "https://api.example.com",
                        clientId = "client",
                        clientSecret = null,
                    ),
                ),
            )
        }
        assertEquals(HttpStatusCode.OK, response.status)

        transaction {
            val entity = ZgwApiSettingEntity.findById(id)!!
            assertNotNull(entity.clientSecretEncrypted)
            assertEquals("original-secret", SecretCrypto.decrypt(entity.clientSecretEncrypted!!))
        }
    }

    // -----------------------------------------------------------------------
    // DELETE /settings/zgw-api-settings/{id}
    // -----------------------------------------------------------------------

    @Test
    fun `DELETE removes profile and returns 204`() = testApplication {
        application { setup() }
        val id = insertProfile("to-delete")

        val response = client.delete("/settings/zgw-api-settings/$id")

        assertEquals(HttpStatusCode.NoContent, response.status)
        transaction { assertEquals(null, ZgwApiSettingEntity.findById(id)) }
    }

    @Test
    fun `DELETE returns 400 for invalid UUID`() = testApplication {
        application { setup() }

        val response = client.delete("/settings/zgw-api-settings/not-a-uuid")

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `DELETE returns 404 when profile does not exist`() = testApplication {
        application { setup() }

        val response = client.delete("/settings/zgw-api-settings/${UUID.randomUUID()}")

        assertEquals(HttpStatusCode.NotFound, response.status)
    }
}
