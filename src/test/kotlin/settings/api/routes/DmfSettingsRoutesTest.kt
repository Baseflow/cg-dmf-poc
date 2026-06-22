// SPDX-License-Identifier: EUPL-1.2
// Copyright (C) 2026 Gemeente Utrecht
package com.baseflow.settings.api.routes

import com.baseflow.shared.api.models.settings.DmfSettingEntry
import com.baseflow.shared.api.models.settings.UpsertDmfSettingRequest
import com.baseflow.shared.config.BestandsDeelConfig
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DmfSettingsRoutesTest : SettingsTestBase("dmf_settings") {

    private val json = Json { ignoreUnknownKeys = true }

    // -----------------------------------------------------------------------
    // GET /settings/dmf-settings
    // -----------------------------------------------------------------------

    @Test
    fun `GET returns seeded entries with type`() = testApplication {
        application { setup() }

        val response = client.get("/settings/dmf-settings")

        assertEquals(HttpStatusCode.OK, response.status)
        val body = json.decodeFromString<List<DmfSettingEntry>>(response.bodyAsText())
        assertEquals(3, body.size)

        val triggerEntry = body.first { it.key == "trigger_size_bytes" }
        assertEquals("int", triggerEntry.type)
        assertEquals(BestandsDeelConfig.triggerSizeBytes.toString(), triggerEntry.value)

        val chunkEntry = body.first { it.key == "chunk_size_bytes" }
        assertEquals("int", chunkEntry.type)
        assertEquals(BestandsDeelConfig.chunkSizeBytes.toString(), chunkEntry.value)

        val validationEntry = body.first { it.key == "validation_enabled" }
        assertEquals("boolean", validationEntry.type)
        assertEquals("true", validationEntry.value)
    }

    @Test
    fun `GET returns entries sorted by key`() = testApplication {
        application { setup() }

        val body = json.decodeFromString<List<DmfSettingEntry>>(
            client.get("/settings/dmf-settings").bodyAsText(),
        )
        val keys = body.map { it.key }
        assertEquals(keys.sorted(), keys)
    }

    // -----------------------------------------------------------------------
    // PUT /settings/dmf-settings/{key}
    // -----------------------------------------------------------------------

    @Test
    fun `PUT existing int key updates value and returns entry with type`() = testApplication {
        application { setup() }

        val response = client.put("/settings/dmf-settings/trigger_size_bytes") {
            contentType(ContentType.Application.Json)
            setBody(Json.encodeToString(UpsertDmfSettingRequest.serializer(), UpsertDmfSettingRequest("999")))
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val entry = json.decodeFromString<DmfSettingEntry>(response.bodyAsText())
        assertEquals("trigger_size_bytes", entry.key)
        assertEquals("int", entry.type)
        assertEquals("999", entry.value)
    }

    @Test
    fun `PUT boolean key accepts true and false`() = testApplication {
        application { setup() }

        val falseResponse = client.put("/settings/dmf-settings/validation_enabled") {
            contentType(ContentType.Application.Json)
            setBody(Json.encodeToString(UpsertDmfSettingRequest.serializer(), UpsertDmfSettingRequest("false")))
        }
        assertEquals(HttpStatusCode.OK, falseResponse.status)
        assertEquals("false", json.decodeFromString<DmfSettingEntry>(falseResponse.bodyAsText()).value)

        val trueResponse = client.put("/settings/dmf-settings/validation_enabled") {
            contentType(ContentType.Application.Json)
            setBody(Json.encodeToString(UpsertDmfSettingRequest.serializer(), UpsertDmfSettingRequest("true")))
        }
        assertEquals(HttpStatusCode.OK, trueResponse.status)
        assertEquals("boolean", json.decodeFromString<DmfSettingEntry>(trueResponse.bodyAsText()).type)
    }

    @Test
    fun `PUT persists change verified by subsequent GET`() = testApplication {
        application { setup() }

        client.put("/settings/dmf-settings/validation_enabled") {
            contentType(ContentType.Application.Json)
            setBody(Json.encodeToString(UpsertDmfSettingRequest.serializer(), UpsertDmfSettingRequest("false")))
        }

        val list = json.decodeFromString<List<DmfSettingEntry>>(
            client.get("/settings/dmf-settings").bodyAsText(),
        )
        assertEquals("false", list.first { it.key == "validation_enabled" }.value)
    }

    @Test
    fun `PUT returns 400 for unknown key`() = testApplication {
        application { setup() }

        val response = client.put("/settings/dmf-settings/unknown_key") {
            contentType(ContentType.Application.Json)
            setBody(Json.encodeToString(UpsertDmfSettingRequest.serializer(), UpsertDmfSettingRequest("hello")))
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    // -----------------------------------------------------------------------
    // Type validation — int
    // -----------------------------------------------------------------------

    @Test
    fun `PUT trigger_size_bytes rejects zero`() = testApplication {
        application { setup() }

        val response = client.put("/settings/dmf-settings/trigger_size_bytes") {
            contentType(ContentType.Application.Json)
            setBody(Json.encodeToString(UpsertDmfSettingRequest.serializer(), UpsertDmfSettingRequest("0")))
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `PUT trigger_size_bytes rejects negative value`() = testApplication {
        application { setup() }

        val response = client.put("/settings/dmf-settings/trigger_size_bytes") {
            contentType(ContentType.Application.Json)
            setBody(Json.encodeToString(UpsertDmfSettingRequest.serializer(), UpsertDmfSettingRequest("-1")))
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `PUT chunk_size_bytes rejects zero`() = testApplication {
        application { setup() }

        val response = client.put("/settings/dmf-settings/chunk_size_bytes") {
            contentType(ContentType.Application.Json)
            setBody(Json.encodeToString(UpsertDmfSettingRequest.serializer(), UpsertDmfSettingRequest("0")))
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `PUT chunk_size_bytes rejects negative value`() = testApplication {
        application { setup() }

        val response = client.put("/settings/dmf-settings/chunk_size_bytes") {
            contentType(ContentType.Application.Json)
            setBody(Json.encodeToString(UpsertDmfSettingRequest.serializer(), UpsertDmfSettingRequest("-1")))
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `PUT returns 400 when int key receives non-numeric value`() = testApplication {
        application { setup() }

        val response = client.put("/settings/dmf-settings/trigger_size_bytes") {
            contentType(ContentType.Application.Json)
            setBody(Json.encodeToString(UpsertDmfSettingRequest.serializer(), UpsertDmfSettingRequest("unlimited")))
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `PUT returns 400 when int key receives a decimal value`() = testApplication {
        application { setup() }

        val response = client.put("/settings/dmf-settings/trigger_size_bytes") {
            contentType(ContentType.Application.Json)
            setBody(Json.encodeToString(UpsertDmfSettingRequest.serializer(), UpsertDmfSettingRequest("1.5")))
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `PUT returns 400 when int key receives empty string`() = testApplication {
        application { setup() }

        val response = client.put("/settings/dmf-settings/trigger_size_bytes") {
            contentType(ContentType.Application.Json)
            setBody(Json.encodeToString(UpsertDmfSettingRequest.serializer(), UpsertDmfSettingRequest("")))
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    // -----------------------------------------------------------------------
    // Type validation — boolean
    // -----------------------------------------------------------------------

    @Test
    fun `PUT returns 400 when boolean key receives invalid value`() = testApplication {
        application { setup() }

        val response = client.put("/settings/dmf-settings/validation_enabled") {
            contentType(ContentType.Application.Json)
            setBody(Json.encodeToString(UpsertDmfSettingRequest.serializer(), UpsertDmfSettingRequest("yes")))
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `PUT returns 400 when boolean key receives 1 or 0`() = testApplication {
        application { setup() }

        for (value in listOf("1", "0")) {
            val response = client.put("/settings/dmf-settings/validation_enabled") {
                contentType(ContentType.Application.Json)
                setBody(Json.encodeToString(UpsertDmfSettingRequest.serializer(), UpsertDmfSettingRequest(value)))
            }
            assertEquals(HttpStatusCode.BadRequest, response.status, "Expected 400 for value '$value'")
        }
    }

    @Test
    fun `PUT boolean validation is case-sensitive`() = testApplication {
        application { setup() }

        for (value in listOf("True", "False", "TRUE", "FALSE")) {
            val response = client.put("/settings/dmf-settings/validation_enabled") {
                contentType(ContentType.Application.Json)
                setBody(Json.encodeToString(UpsertDmfSettingRequest.serializer(), UpsertDmfSettingRequest(value)))
            }
            assertEquals(HttpStatusCode.BadRequest, response.status, "Expected 400 for value '$value'")
        }
    }

    @Test
    fun `PUT returns 400 for malformed JSON`() = testApplication {
        application { setup() }

        val response = client.put("/settings/dmf-settings/trigger_size_bytes") {
            contentType(ContentType.Application.Json)
            setBody("not-json")
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    // -----------------------------------------------------------------------
    // DELETE /settings/dmf-settings/{key}
    // -----------------------------------------------------------------------

    @Test
    fun `DELETE existing key returns 204 and removes entry`() = testApplication {
        application { setup() }

        val del = client.delete("/settings/dmf-settings/validation_enabled")
        assertEquals(HttpStatusCode.NoContent, del.status)

        val list = json.decodeFromString<List<DmfSettingEntry>>(
            client.get("/settings/dmf-settings").bodyAsText(),
        )
        assertTrue(list.none { it.key == "validation_enabled" })
    }

    @Test
    fun `DELETE returns 400 for unknown key`() = testApplication {
        application { setup() }

        val response = client.delete("/settings/dmf-settings/does_not_exist")
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `DELETE known key that no longer exists returns 404`() = testApplication {
        application { setup() }

        client.delete("/settings/dmf-settings/validation_enabled")
        val response = client.delete("/settings/dmf-settings/validation_enabled")
        assertEquals(HttpStatusCode.NotFound, response.status)
    }
}
