// SPDX-License-Identifier: EUPL-1.2
// Copyright (C) 2026 Gemeente Utrecht
package com.baseflow.settings.api.routes

import com.baseflow.shared.api.apiJsonConfig
import com.baseflow.shared.api.models.settings.ApiConnectionSettingResponse
import com.baseflow.shared.api.models.settings.CreateApiConnectionSettingRequest
import com.baseflow.shared.api.models.settings.UpdateApiConnectionSettingRequest
import com.baseflow.shared.entities.settings.ApiAuthType
import com.baseflow.shared.entities.settings.ApiConnectionSettingEntity
import com.baseflow.shared.entities.settings.ApiConnectionType
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Clock

class ApiConnectionSettingsRoutesTest : SettingsTestBase("api_connection_settings") {

    private val json = apiJsonConfig()

    private fun insertProfile(
        name: String,
        baseUrl: String = "https://api.example.com",
        clientId: String = "client-id",
        clientSecret: String? = null,
        apiType: String = ApiConnectionType.ZTC.value,
        authType: String = ApiAuthType.ZGW_AUTH.value,
        validationEnabled: Boolean = true,
        enabled: Boolean = true,
        readonly: Boolean = false,
    ): UUID = transaction {
        ApiConnectionSettingEntity.new {
            this.name = name
            this.baseUrl = baseUrl
            this.clientId = clientId
            this.clientSecret = clientSecret
            this.apiType = apiType
            this.authType = authType
            this.validationEnabled = validationEnabled
            this.enabled = enabled
            this.readonly = readonly
            val now = Clock.System.now()
            this.createdAt = now
            this.updatedAt = now
        }.id.value
    }

    // -----------------------------------------------------------------------
    // GET /settings/api-connection-settings
    // -----------------------------------------------------------------------

    @Test
    fun `GET returns empty array when no profiles exist`() = testApplication {
        application { setup() }

        val response = client.get("/settings/api-connection-settings")

        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(json.decodeFromString<List<ApiConnectionSettingResponse>>(response.bodyAsText()).isEmpty())
    }

    @Test
    fun `GET returns all profiles`() = testApplication {
        application { setup() }
        insertProfile("profile-a")
        insertProfile("profile-b")

        val response = client.get("/settings/api-connection-settings")

        assertEquals(HttpStatusCode.OK, response.status)
        val body = json.decodeFromString<List<ApiConnectionSettingResponse>>(response.bodyAsText())
        assertEquals(2, body.size)
        assertTrue(body.map { it.name }.containsAll(listOf("profile-a", "profile-b")))
    }

    @Test
    fun `GET includes apiType and validationEnabled in response`() = testApplication {
        application { setup() }
        insertProfile("profile", apiType = "nrc", validationEnabled = false)

        val response = client.get("/settings/api-connection-settings")

        assertEquals(HttpStatusCode.OK, response.status)
        val body = json.decodeFromString<List<ApiConnectionSettingResponse>>(response.bodyAsText())
        assertEquals("nrc", body.first().apiType)
        assertFalse(body.first().validationEnabled)
    }

    @Test
    fun `GET hasSecret is false when no secret stored`() = testApplication {
        application { setup() }
        insertProfile("profile")

        val response = client.get("/settings/api-connection-settings")

        assertEquals(HttpStatusCode.OK, response.status)
        assertFalse(json.decodeFromString<List<ApiConnectionSettingResponse>>(response.bodyAsText()).first().hasSecret)
    }

    @Test
    fun `GET hasSecret is true when secret is stored`() = testApplication {
        application { setup() }
        insertProfile("profile", clientSecret = "my-secret")

        val response = client.get("/settings/api-connection-settings")

        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(json.decodeFromString<List<ApiConnectionSettingResponse>>(response.bodyAsText()).first().hasSecret)
    }

    // -----------------------------------------------------------------------
    // POST /settings/api-connection-settings
    // -----------------------------------------------------------------------

    @Test
    fun `POST creates profile and returns 201`() = testApplication {
        application { setup() }

        val response = client.post("/settings/api-connection-settings") {
            contentType(ContentType.Application.Json)
            setBody(
                Json.encodeToString(
                    CreateApiConnectionSettingRequest.serializer(),
                    CreateApiConnectionSettingRequest(
                        name = "new-profile",
                        baseUrl = "https://api.example.com",
                        clientId = "my-client",
                        apiType = "ztc",
                    ),
                ),
            )
        }

        assertEquals(HttpStatusCode.Created, response.status)
        val body = json.decodeFromString<ApiConnectionSettingResponse>(response.bodyAsText())
        assertNotNull(body.id)
        assertEquals("new-profile", body.name)
        assertEquals("https://api.example.com", body.baseUrl)
        assertEquals("ztc", body.apiType)
        assertTrue(body.validationEnabled)
    }

    @Test
    fun `POST stores validationEnabled false`() = testApplication {
        application { setup() }

        val response = client.post("/settings/api-connection-settings") {
            contentType(ContentType.Application.Json)
            setBody(
                Json.encodeToString(
                    CreateApiConnectionSettingRequest.serializer(),
                    CreateApiConnectionSettingRequest(
                        name = "no-validate",
                        baseUrl = "https://api.example.com",
                        clientId = "my-client",
                        apiType = "zrc",
                        validationEnabled = false,
                    ),
                ),
            )
        }

        assertEquals(HttpStatusCode.Created, response.status)
        assertFalse(json.decodeFromString<ApiConnectionSettingResponse>(response.bodyAsText()).validationEnabled)
    }

    @Test
    fun `POST returns 400 when name is blank`() = testApplication {
        application { setup() }

        val response = client.post("/settings/api-connection-settings") {
            contentType(ContentType.Application.Json)
            setBody(
                Json.encodeToString(
                    CreateApiConnectionSettingRequest.serializer(),
                    CreateApiConnectionSettingRequest(
                        name = "",
                        baseUrl = "https://api.example.com",
                        clientId = "my-client",
                        apiType = "ztc",
                    ),
                ),
            )
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `POST returns 400 when baseUrl is blank`() = testApplication {
        application { setup() }

        val response = client.post("/settings/api-connection-settings") {
            contentType(ContentType.Application.Json)
            setBody(
                Json.encodeToString(
                    CreateApiConnectionSettingRequest.serializer(),
                    CreateApiConnectionSettingRequest(name = "profile", baseUrl = "", clientId = "my-client", apiType = "ztc"),
                ),
            )
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `POST returns 400 when clientId is blank`() = testApplication {
        application { setup() }

        val response = client.post("/settings/api-connection-settings") {
            contentType(ContentType.Application.Json)
            setBody(
                Json.encodeToString(
                    CreateApiConnectionSettingRequest.serializer(),
                    CreateApiConnectionSettingRequest(
                        name = "profile",
                        baseUrl = "https://api.example.com",
                        clientId = "",
                        apiType = "ztc",
                    ),
                ),
            )
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `POST returns 400 when apiType is blank`() = testApplication {
        application { setup() }

        val response = client.post("/settings/api-connection-settings") {
            contentType(ContentType.Application.Json)
            setBody(
                Json.encodeToString(
                    CreateApiConnectionSettingRequest.serializer(),
                    CreateApiConnectionSettingRequest(
                        name = "profile",
                        baseUrl = "https://api.example.com",
                        clientId = "my-client",
                        apiType = "",
                    ),
                ),
            )
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `POST returns 400 for malformed JSON`() = testApplication {
        application { setup() }

        val response = client.post("/settings/api-connection-settings") {
            contentType(ContentType.Application.Json)
            setBody("not-json")
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `POST returns 409 when name already exists`() = testApplication {
        application { setup() }
        insertProfile("duplicate-profile")

        val response = client.post("/settings/api-connection-settings") {
            contentType(ContentType.Application.Json)
            setBody(
                Json.encodeToString(
                    CreateApiConnectionSettingRequest.serializer(),
                    CreateApiConnectionSettingRequest(
                        name = "duplicate-profile",
                        baseUrl = "https://api.example.com",
                        clientId = "my-client",
                        apiType = "ztc",
                    ),
                ),
            )
        }

        assertEquals(HttpStatusCode.Conflict, response.status)
    }

    // -----------------------------------------------------------------------
    // PUT /settings/api-connection-settings/{id}
    // -----------------------------------------------------------------------

    @Test
    fun `PUT updates profile and returns 200`() = testApplication {
        application { setup() }
        val id = insertProfile("original-profile")

        val response = client.put("/settings/api-connection-settings/$id") {
            contentType(ContentType.Application.Json)
            setBody(
                Json.encodeToString(
                    UpdateApiConnectionSettingRequest.serializer(),
                    UpdateApiConnectionSettingRequest(
                        name = "updated-profile",
                        baseUrl = "https://new-api.example.com",
                        clientId = "new-client",
                        apiType = "zrc",
                        validationEnabled = false,
                    ),
                ),
            )
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val body = json.decodeFromString<ApiConnectionSettingResponse>(response.bodyAsText())
        assertEquals("updated-profile", body.name)
        assertEquals("https://new-api.example.com", body.baseUrl)
        assertEquals("zrc", body.apiType)
        assertFalse(body.validationEnabled)
    }

    @Test
    fun `PUT returns 400 for invalid UUID`() = testApplication {
        application { setup() }

        val response = client.put("/settings/api-connection-settings/not-a-uuid") {
            contentType(ContentType.Application.Json)
            setBody(
                Json.encodeToString(
                    UpdateApiConnectionSettingRequest.serializer(),
                    UpdateApiConnectionSettingRequest(
                        name = "name",
                        baseUrl = "https://api.example.com",
                        clientId = "client",
                        apiType = "ztc",
                    ),
                ),
            )
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `PUT returns 400 when name is blank`() = testApplication {
        application { setup() }
        val id = insertProfile("profile")

        val response = client.put("/settings/api-connection-settings/$id") {
            contentType(ContentType.Application.Json)
            setBody(
                Json.encodeToString(
                    UpdateApiConnectionSettingRequest.serializer(),
                    UpdateApiConnectionSettingRequest(name = "", baseUrl = "https://api.example.com", clientId = "client", apiType = "ztc"),
                ),
            )
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `PUT returns 400 when baseUrl is blank`() = testApplication {
        application { setup() }
        val id = insertProfile("profile")

        val response = client.put("/settings/api-connection-settings/$id") {
            contentType(ContentType.Application.Json)
            setBody(
                Json.encodeToString(
                    UpdateApiConnectionSettingRequest.serializer(),
                    UpdateApiConnectionSettingRequest(name = "profile", baseUrl = "", clientId = "client", apiType = "ztc"),
                ),
            )
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `PUT returns 400 when clientId is blank`() = testApplication {
        application { setup() }
        val id = insertProfile("profile")

        val response = client.put("/settings/api-connection-settings/$id") {
            contentType(ContentType.Application.Json)
            setBody(
                Json.encodeToString(
                    UpdateApiConnectionSettingRequest.serializer(),
                    UpdateApiConnectionSettingRequest(
                        name = "profile",
                        baseUrl = "https://api.example.com",
                        clientId = "",
                        apiType = "ztc",
                    ),
                ),
            )
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `PUT returns 400 when apiType is blank`() = testApplication {
        application { setup() }
        val id = insertProfile("profile")

        val response = client.put("/settings/api-connection-settings/$id") {
            contentType(ContentType.Application.Json)
            setBody(
                Json.encodeToString(
                    UpdateApiConnectionSettingRequest.serializer(),
                    UpdateApiConnectionSettingRequest(
                        name = "profile",
                        baseUrl = "https://api.example.com",
                        clientId = "client",
                        apiType = "",
                    ),
                ),
            )
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `PUT returns 404 when profile does not exist`() = testApplication {
        application { setup() }

        val response = client.put("/settings/api-connection-settings/${UUID.randomUUID()}") {
            contentType(ContentType.Application.Json)
            setBody(
                Json.encodeToString(
                    UpdateApiConnectionSettingRequest.serializer(),
                    UpdateApiConnectionSettingRequest(
                        name = "name",
                        baseUrl = "https://api.example.com",
                        clientId = "client",
                        apiType = "ztc",
                    ),
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

        val response = client.put("/settings/api-connection-settings/$idB") {
            contentType(ContentType.Application.Json)
            setBody(
                Json.encodeToString(
                    UpdateApiConnectionSettingRequest.serializer(),
                    UpdateApiConnectionSettingRequest(
                        name = "profile-a",
                        baseUrl = "https://api.example.com",
                        clientId = "client",
                        apiType = "ztc",
                    ),
                ),
            )
        }

        assertEquals(HttpStatusCode.Conflict, response.status)
    }

    @Test
    fun `PUT does not overwrite secret when clientSecret is omitted`() = testApplication {
        application { setup() }
        val id = insertProfile("profile", clientSecret = "original-secret")

        val response = client.put("/settings/api-connection-settings/$id") {
            contentType(ContentType.Application.Json)
            setBody(
                Json.encodeToString(
                    UpdateApiConnectionSettingRequest.serializer(),
                    UpdateApiConnectionSettingRequest(
                        name = "profile",
                        baseUrl = "https://api.example.com",
                        clientId = "client",
                        clientSecret = null,
                        apiType = "ztc",
                    ),
                ),
            )
        }
        assertEquals(HttpStatusCode.OK, response.status)

        transaction {
            val entity = ApiConnectionSettingEntity.findById(id)!!
            assertNotNull(entity.clientSecret)
            assertEquals("original-secret", entity.clientSecret)
        }
    }

    // -----------------------------------------------------------------------
    // DELETE /settings/api-connection-settings/{id}
    // -----------------------------------------------------------------------

    @Test
    fun `DELETE removes profile and returns 204`() = testApplication {
        application { setup() }
        val id = insertProfile("to-delete")

        val response = client.delete("/settings/api-connection-settings/$id")

        assertEquals(HttpStatusCode.NoContent, response.status)
        transaction { assertEquals(null, ApiConnectionSettingEntity.findById(id)) }
    }

    @Test
    fun `DELETE returns 400 for invalid UUID`() = testApplication {
        application { setup() }

        val response = client.delete("/settings/api-connection-settings/not-a-uuid")

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `DELETE returns 404 when profile does not exist`() = testApplication {
        application { setup() }

        val response = client.delete("/settings/api-connection-settings/${UUID.randomUUID()}")

        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    @Test
    fun `PUT can recover a profile with a new secret`() = testApplication {
        application { setup() }
        val id = insertProfile("corrupted-profile", clientSecret = "some-secret")

        val response = client.put("/settings/api-connection-settings/$id") {
            contentType(ContentType.Application.Json)
            setBody(
                Json.encodeToString(
                    UpdateApiConnectionSettingRequest.serializer(),
                    UpdateApiConnectionSettingRequest(
                        name = "recovered-profile",
                        baseUrl = "https://new.example.com",
                        clientId = "client",
                        clientSecret = "new-working-secret",
                        apiType = "ztc",
                    ),
                ),
            )
        }
        assertEquals(HttpStatusCode.OK, response.status)

        transaction {
            val entity = ApiConnectionSettingEntity.findById(id)!!
            assertEquals("new-working-secret", entity.clientSecret)
            assertEquals("recovered-profile", entity.name)
        }
    }

    @Test
    fun `PUT returns 403 when profile is readonly`() = testApplication {
        application { setup() }
        val id = insertProfile("env-managed", readonly = true)

        val response = client.put("/settings/api-connection-settings/$id") {
            contentType(ContentType.Application.Json)
            setBody(
                Json.encodeToString(
                    UpdateApiConnectionSettingRequest.serializer(),
                    UpdateApiConnectionSettingRequest(
                        name = "env-managed",
                        baseUrl = "https://api.example.com",
                        clientId = "client",
                        apiType = "ztc",
                    ),
                ),
            )
        }

        assertEquals(HttpStatusCode.Forbidden, response.status)
    }

    @Test
    fun `GET readonly and enabled are included in response`() = testApplication {
        application { setup() }
        insertProfile("imported-profile", readonly = true, enabled = false)

        val response = client.get("/settings/api-connection-settings")

        assertEquals(HttpStatusCode.OK, response.status)
        val body = json.decodeFromString<List<ApiConnectionSettingResponse>>(response.bodyAsText())
        val profile = body.first()
        assertTrue(profile.readonly)
        assertFalse(profile.enabled)
    }

    @Test
    fun `GET includes createdAt in response`() = testApplication {
        application { setup() }
        insertProfile("profile")

        val response = client.get("/settings/api-connection-settings")

        assertEquals(HttpStatusCode.OK, response.status)
        val body = json.decodeFromString<List<ApiConnectionSettingResponse>>(response.bodyAsText())
        assertNotNull(body.first().createdAt)
    }

    // -----------------------------------------------------------------------
    // DELETE readonly
    // -----------------------------------------------------------------------
    @Test
    fun `DELETE returns 403 when profile is readonly`() = testApplication {
        application { setup() }
        val id = insertProfile("env-managed", readonly = true)

        val response = client.delete("/settings/api-connection-settings/$id")

        assertEquals(HttpStatusCode.Forbidden, response.status)
        transaction { assertNotNull(ApiConnectionSettingEntity.findById(id)) }
    }

    // -----------------------------------------------------------------------
    // Input validation
    // -----------------------------------------------------------------------

    @Test
    fun `POST succeeds when clientId is blank and authType is none`() = testApplication {
        application { setup() }

        val response = client.post("/settings/api-connection-settings") {
            contentType(ContentType.Application.Json)
            setBody(
                Json.encodeToString(
                    CreateApiConnectionSettingRequest.serializer(),
                    CreateApiConnectionSettingRequest(
                        name = "no-auth",
                        baseUrl = "https://api.example.com",
                        clientId = "",
                        apiType = "orc",
                        authType = "none",
                    ),
                ),
            )
        }

        assertEquals(HttpStatusCode.Created, response.status)
    }

    @Test
    fun `POST succeeds when clientId is blank and authType is bearer`() = testApplication {
        application { setup() }

        val response = client.post("/settings/api-connection-settings") {
            contentType(ContentType.Application.Json)
            setBody(
                Json.encodeToString(
                    CreateApiConnectionSettingRequest.serializer(),
                    CreateApiConnectionSettingRequest(
                        name = "bearer-no-id",
                        baseUrl = "https://api.example.com",
                        clientId = "",
                        apiType = "orc",
                        authType = "bearer",
                    ),
                ),
            )
        }

        assertEquals(HttpStatusCode.Created, response.status)
    }

    @Test
    fun `POST returns 400 when apiType is not a valid value`() = testApplication {
        application { setup() }

        val response = client.post("/settings/api-connection-settings") {
            contentType(ContentType.Application.Json)
            setBody(
                Json.encodeToString(
                    CreateApiConnectionSettingRequest.serializer(),
                    CreateApiConnectionSettingRequest(
                        name = "profile",
                        baseUrl = "https://api.example.com",
                        clientId = "client",
                        apiType = "invalid-type",
                    ),
                ),
            )
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `POST returns 400 when authType is not a valid value`() = testApplication {
        application { setup() }

        val response = client.post("/settings/api-connection-settings") {
            contentType(ContentType.Application.Json)
            setBody(
                Json.encodeToString(
                    CreateApiConnectionSettingRequest.serializer(),
                    CreateApiConnectionSettingRequest(
                        name = "profile",
                        baseUrl = "https://api.example.com",
                        clientId = "client",
                        apiType = "ztc",
                        authType = "magic-beans",
                    ),
                ),
            )
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `POST returns 400 when baseUrl is not a valid URL`() = testApplication {
        application { setup() }

        val response = client.post("/settings/api-connection-settings") {
            contentType(ContentType.Application.Json)
            setBody(
                Json.encodeToString(
                    CreateApiConnectionSettingRequest.serializer(),
                    CreateApiConnectionSettingRequest(
                        name = "profile",
                        baseUrl = "not-a-url",
                        clientId = "client",
                        apiType = "ztc",
                    ),
                ),
            )
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `POST returns 400 when name exceeds 100 characters`() = testApplication {
        application { setup() }

        val response = client.post("/settings/api-connection-settings") {
            contentType(ContentType.Application.Json)
            setBody(
                Json.encodeToString(
                    CreateApiConnectionSettingRequest.serializer(),
                    CreateApiConnectionSettingRequest(
                        name = "a".repeat(101),
                        baseUrl = "https://api.example.com",
                        clientId = "client",
                        apiType = "ztc",
                    ),
                ),
            )
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    // -----------------------------------------------------------------------
    // authType persistence
    // -----------------------------------------------------------------------
    @Test
    fun `POST stores authType correctly`() = testApplication {
        application { setup() }

        val response = client.post("/settings/api-connection-settings") {
            contentType(ContentType.Application.Json)
            setBody(
                Json.encodeToString(
                    CreateApiConnectionSettingRequest.serializer(),
                    CreateApiConnectionSettingRequest(
                        name = "bearer-profile",
                        baseUrl = "https://api.example.com",
                        clientId = "client",
                        apiType = "ztc",
                        authType = "bearer",
                    ),
                ),
            )
        }

        assertEquals(HttpStatusCode.Created, response.status)
        assertEquals("bearer", json.decodeFromString<ApiConnectionSettingResponse>(response.bodyAsText()).authType)
    }

    @Test
    fun `PUT updates authType`() = testApplication {
        application { setup() }
        val id = insertProfile("profile", authType = ApiAuthType.ZGW_AUTH.value)

        val response = client.put("/settings/api-connection-settings/$id") {
            contentType(ContentType.Application.Json)
            setBody(
                Json.encodeToString(
                    UpdateApiConnectionSettingRequest.serializer(),
                    UpdateApiConnectionSettingRequest(
                        name = "profile",
                        baseUrl = "https://api.example.com",
                        clientId = "client",
                        apiType = "ztc",
                        authType = "bearer",
                    ),
                ),
            )
        }

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("bearer", json.decodeFromString<ApiConnectionSettingResponse>(response.bodyAsText()).authType)
    }
}
