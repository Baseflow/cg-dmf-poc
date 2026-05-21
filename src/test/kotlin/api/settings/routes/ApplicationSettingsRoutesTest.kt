// SPDX-License-Identifier: EUPL-1.2
// Copyright (C) 2026 Gemeente Utrecht
package com.baseflow.api.settings.routes

import com.baseflow.api.apiJsonConfig
import com.baseflow.api.models.settings.ApplicationSettingsResponse
import com.baseflow.api.models.settings.CreateApplicationSettingsRequest
import com.baseflow.api.models.settings.RotateSecretRequest
import com.baseflow.api.models.settings.RotateSecretResponse
import com.baseflow.api.models.settings.UpdateApplicationSettingsRequest
import com.baseflow.entities.settings.ApplicationSettingEntity
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
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
import kotlin.time.Clock

class ApplicationSettingsRoutesTest : SettingsTestBase("application_settings") {

    private val json = apiJsonConfig()

    private fun insertApp(name: String, clientId: String = "client-id", clientSecret: String? = null): UUID = transaction {
        ApplicationSettingEntity.new {
            this.name = name
            this.clientId = clientId
            this.clientSecret = clientSecret
            this.updatedAt = Clock.System.now().toLocalDateTime(TimeZone.UTC)
        }.id.value
    }

    // -----------------------------------------------------------------------
    // GET /settings/application-settings
    // -----------------------------------------------------------------------

    @Test
    fun `GET returns empty array when no applications exist`() = testApplication {
        application { setup() }

        val response = client.get("/settings/application-settings")

        assertEquals(HttpStatusCode.OK, response.status)
        val body = json.decodeFromString<List<ApplicationSettingsResponse>>(response.bodyAsText())
        assertTrue(body.isEmpty())
    }

    @Test
    fun `GET returns all applications`() = testApplication {
        application { setup() }
        insertApp("app-a")
        insertApp("app-b")

        val response = client.get("/settings/application-settings")

        assertEquals(HttpStatusCode.OK, response.status)
        val body = json.decodeFromString<List<ApplicationSettingsResponse>>(response.bodyAsText())
        assertEquals(2, body.size)
        assertTrue(body.map { it.name }.containsAll(listOf("app-a", "app-b")))
    }

    @Test
    fun `GET hasSecret is false when no secret stored`() = testApplication {
        application { setup() }
        insertApp("no-secret-app")

        val response = client.get("/settings/application-settings")

        assertEquals(HttpStatusCode.OK, response.status)
        assertFalse(json.decodeFromString<List<ApplicationSettingsResponse>>(response.bodyAsText()).first().hasSecret)
    }

    @Test
    fun `GET hasSecret is true when secret is stored`() = testApplication {
        application { setup() }
        insertApp("secret-app", clientSecret = "my-secret")

        val response = client.get("/settings/application-settings")

        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(json.decodeFromString<List<ApplicationSettingsResponse>>(response.bodyAsText()).first().hasSecret)
    }

    // -----------------------------------------------------------------------
    // POST /settings/application-settings
    // -----------------------------------------------------------------------

    @Test
    fun `POST creates application and returns 201`() = testApplication {
        application { setup() }

        val response = client.post("/settings/application-settings") {
            contentType(ContentType.Application.Json)
            setBody(
                Json.encodeToString(
                    CreateApplicationSettingsRequest.serializer(),
                    CreateApplicationSettingsRequest(name = "new-app", clientId = "my-client"),
                ),
            )
        }

        assertEquals(HttpStatusCode.Created, response.status)
        val body = json.decodeFromString<ApplicationSettingsResponse>(response.bodyAsText())
        assertNotNull(body.id)
        assertEquals("new-app", body.name)
        assertEquals("my-client", body.clientId)
    }

    @Test
    fun `POST returns 400 when name is blank`() = testApplication {
        application { setup() }

        val response = client.post("/settings/application-settings") {
            contentType(ContentType.Application.Json)
            setBody(
                Json.encodeToString(
                    CreateApplicationSettingsRequest.serializer(),
                    CreateApplicationSettingsRequest(name = "", clientId = "my-client"),
                ),
            )
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `POST returns 400 when clientId is blank`() = testApplication {
        application { setup() }

        val response = client.post("/settings/application-settings") {
            contentType(ContentType.Application.Json)
            setBody(
                Json.encodeToString(
                    CreateApplicationSettingsRequest.serializer(),
                    CreateApplicationSettingsRequest(name = "my-app", clientId = ""),
                ),
            )
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `POST returns 400 for malformed JSON`() = testApplication {
        application { setup() }

        val response = client.post("/settings/application-settings") {
            contentType(ContentType.Application.Json)
            setBody("not-json")
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `POST returns 409 when name already exists`() = testApplication {
        application { setup() }
        insertApp("duplicate-app")

        val response = client.post("/settings/application-settings") {
            contentType(ContentType.Application.Json)
            setBody(
                Json.encodeToString(
                    CreateApplicationSettingsRequest.serializer(),
                    CreateApplicationSettingsRequest(name = "duplicate-app", clientId = "my-client"),
                ),
            )
        }

        assertEquals(HttpStatusCode.Conflict, response.status)
    }

    // -----------------------------------------------------------------------
    // PUT /settings/application-settings/{id}
    // -----------------------------------------------------------------------

    @Test
    fun `PUT updates application and returns 200`() = testApplication {
        application { setup() }
        val id = insertApp("original-name")

        val response = client.put("/settings/application-settings/$id") {
            contentType(ContentType.Application.Json)
            setBody(
                Json.encodeToString(
                    UpdateApplicationSettingsRequest.serializer(),
                    UpdateApplicationSettingsRequest(name = "updated-name", clientId = "updated-client"),
                ),
            )
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val body = json.decodeFromString<ApplicationSettingsResponse>(response.bodyAsText())
        assertEquals("updated-name", body.name)
        assertEquals("updated-client", body.clientId)
    }

    @Test
    fun `PUT returns 400 for invalid UUID`() = testApplication {
        application { setup() }

        val response = client.put("/settings/application-settings/not-a-uuid") {
            contentType(ContentType.Application.Json)
            setBody(
                Json.encodeToString(
                    UpdateApplicationSettingsRequest.serializer(),
                    UpdateApplicationSettingsRequest(name = "name", clientId = "client"),
                ),
            )
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `PUT returns 400 when name is blank`() = testApplication {
        application { setup() }
        val id = insertApp("app")

        val response = client.put("/settings/application-settings/$id") {
            contentType(ContentType.Application.Json)
            setBody(
                Json.encodeToString(
                    UpdateApplicationSettingsRequest.serializer(),
                    UpdateApplicationSettingsRequest(name = "", clientId = "client"),
                ),
            )
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `PUT returns 400 when clientId is blank`() = testApplication {
        application { setup() }
        val id = insertApp("app")

        val response = client.put("/settings/application-settings/$id") {
            contentType(ContentType.Application.Json)
            setBody(
                Json.encodeToString(
                    UpdateApplicationSettingsRequest.serializer(),
                    UpdateApplicationSettingsRequest(name = "app", clientId = ""),
                ),
            )
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `PUT returns 404 when application does not exist`() = testApplication {
        application { setup() }

        val response = client.put("/settings/application-settings/${UUID.randomUUID()}") {
            contentType(ContentType.Application.Json)
            setBody(
                Json.encodeToString(
                    UpdateApplicationSettingsRequest.serializer(),
                    UpdateApplicationSettingsRequest(name = "name", clientId = "client"),
                ),
            )
        }

        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    @Test
    fun `PUT returns 409 when renaming to an existing name`() = testApplication {
        application { setup() }
        insertApp("app-a")
        val idB = insertApp("app-b")

        val response = client.put("/settings/application-settings/$idB") {
            contentType(ContentType.Application.Json)
            setBody(
                Json.encodeToString(
                    UpdateApplicationSettingsRequest.serializer(),
                    UpdateApplicationSettingsRequest(name = "app-a", clientId = "client"),
                ),
            )
        }

        assertEquals(HttpStatusCode.Conflict, response.status)
    }

    @Test
    fun `PUT does not overwrite secret when clientSecret is omitted`() = testApplication {
        application { setup() }
        val id = insertApp("app", clientSecret = "original-secret")

        val response = client.put("/settings/application-settings/$id") {
            contentType(ContentType.Application.Json)
            setBody(
                Json.encodeToString(
                    UpdateApplicationSettingsRequest.serializer(),
                    UpdateApplicationSettingsRequest(name = "app", clientId = "client", clientSecret = null),
                ),
            )
        }
        assertEquals(HttpStatusCode.OK, response.status)

        transaction {
            val entity = ApplicationSettingEntity.findById(id)!!
            assertNotNull(entity.clientSecret)
            assertEquals("original-secret", entity.clientSecret)
        }
    }

    // -----------------------------------------------------------------------
    // DELETE /settings/application-settings/{id}
    // -----------------------------------------------------------------------

    @Test
    fun `DELETE removes application and returns 204`() = testApplication {
        application { setup() }
        val id = insertApp("to-delete")

        val response = client.delete("/settings/application-settings/$id")

        assertEquals(HttpStatusCode.NoContent, response.status)
        transaction { assertEquals(null, ApplicationSettingEntity.findById(id)) }
    }

    @Test
    fun `DELETE returns 400 for invalid UUID`() = testApplication {
        application { setup() }

        val response = client.delete("/settings/application-settings/not-a-uuid")

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `DELETE returns 404 when application does not exist`() = testApplication {
        application { setup() }

        val response = client.delete("/settings/application-settings/${UUID.randomUUID()}")

        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    // -----------------------------------------------------------------------
    // POST /settings/application-settings/{id}/rotate-secret
    // -----------------------------------------------------------------------

    @Test
    fun `rotate-secret returns 200 with 64-char hex secret when newSecret is omitted`() = testApplication {
        application { setup() }
        val id = insertApp("app")

        val response = client.post("/settings/application-settings/$id/rotate-secret") {
            contentType(ContentType.Application.Json)
            setBody("{}")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val body = json.decodeFromString<RotateSecretResponse>(response.bodyAsText())
        assertEquals(64, body.secret.length)
        assertTrue(Regex("^[0-9a-fA-F]{64}$").matches(body.secret))
    }

    @Test
    fun `rotate-secret returns 200 and returns the caller-supplied secret`() = testApplication {
        application { setup() }
        val id = insertApp("app")

        val response = client.post("/settings/application-settings/$id/rotate-secret") {
            contentType(ContentType.Application.Json)
            setBody(
                Json.encodeToString(
                    RotateSecretRequest.serializer(),
                    RotateSecretRequest(newSecret = "my-custom-secret"),
                ),
            )
        }

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("my-custom-secret", json.decodeFromString<RotateSecretResponse>(response.bodyAsText()).secret)
    }

    @Test
    fun `rotate-secret returns 400 for invalid UUID`() = testApplication {
        application { setup() }

        val response = client.post("/settings/application-settings/not-a-uuid/rotate-secret") {
            contentType(ContentType.Application.Json)
            setBody("{}")
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `rotate-secret returns 404 when application does not exist`() = testApplication {
        application { setup() }

        val response = client.post("/settings/application-settings/${UUID.randomUUID()}/rotate-secret") {
            contentType(ContentType.Application.Json)
            setBody("{}")
        }

        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    @Test
    fun `PUT can recover an application with a new secret`() = testApplication {
        application { setup() }
        val id = insertApp("corrupted-app", clientSecret = "some-secret")

        val response = client.put("/settings/application-settings/$id") {
            contentType(ContentType.Application.Json)
            setBody(
                Json.encodeToString(
                    UpdateApplicationSettingsRequest.serializer(),
                    UpdateApplicationSettingsRequest(
                        name = "recovered-app",
                        clientId = "client",
                        clientSecret = "new-working-secret",
                    ),
                ),
            )
        }
        assertEquals(HttpStatusCode.OK, response.status)

        transaction {
            val entity = ApplicationSettingEntity.findById(id)!!
            assertEquals("new-working-secret", entity.clientSecret)
            assertEquals("recovered-app", entity.name)
        }
    }
}
