// SPDX-License-Identifier: EUPL-1.2
// Copyright (C) 2026 Gemeente Utrecht
package com.baseflow.api.settings.routes

import com.baseflow.api.models.settings.DmfSettingsResponse
import com.baseflow.api.models.settings.UpdateDmfSettingsRequest
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DmfSettingsRoutesTest : SettingsTestBase("dmf_settings") {

    // -----------------------------------------------------------------------
    // GET /settings/dmf-settings
    // -----------------------------------------------------------------------

    @Test
    fun `GET returns seeded DMF settings`() = testApplication {
        application { setup() }

        val response = client.get("/settings/dmf-settings")

        assertEquals(HttpStatusCode.OK, response.status)
        // DmfSettingsResponse has no nullable fields, so default Json is sufficient here
        val body = Json.decodeFromString<DmfSettingsResponse>(response.bodyAsText())
        assertEquals(4_294_967_296L, body.triggerSize)
        assertEquals(3_221_225_472L, body.chunkSize)
        assertTrue(body.validationEnabled)
    }

    // -----------------------------------------------------------------------
    // PUT /settings/dmf-settings
    // -----------------------------------------------------------------------

    @Test
    fun `PUT updates all fields and returns 200`() = testApplication {
        application { setup() }

        val response = client.put("/settings/dmf-settings") {
            contentType(ContentType.Application.Json)
            setBody(Json.encodeToString(UpdateDmfSettingsRequest.serializer(),
                UpdateDmfSettingsRequest(triggerSize = 1_000_000L, chunkSize = 500_000L, validationEnabled = false)))
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val body = Json.decodeFromString<DmfSettingsResponse>(response.bodyAsText())
        assertEquals(1_000_000L, body.triggerSize)
        assertEquals(500_000L, body.chunkSize)
        assertFalse(body.validationEnabled)
    }

    @Test
    fun `PUT persists changes verified by subsequent GET`() = testApplication {
        application { setup() }

        client.put("/settings/dmf-settings") {
            contentType(ContentType.Application.Json)
            setBody(Json.encodeToString(UpdateDmfSettingsRequest.serializer(),
                UpdateDmfSettingsRequest(triggerSize = 2_000_000L, chunkSize = 1_000_000L, validationEnabled = false)))
        }

        val getResponse = client.get("/settings/dmf-settings")
        assertEquals(HttpStatusCode.OK, getResponse.status)
        val body = Json.decodeFromString<DmfSettingsResponse>(getResponse.bodyAsText())
        assertEquals(2_000_000L, body.triggerSize)
        assertEquals(1_000_000L, body.chunkSize)
        assertFalse(body.validationEnabled)
    }

    @Test
    fun `PUT returns 400 when triggerSize is less than 1`() = testApplication {
        application { setup() }

        val response = client.put("/settings/dmf-settings") {
            contentType(ContentType.Application.Json)
            setBody(Json.encodeToString(UpdateDmfSettingsRequest.serializer(),
                UpdateDmfSettingsRequest(triggerSize = 0L, chunkSize = 500_000L, validationEnabled = true)))
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `PUT returns 400 when chunkSize is less than 1`() = testApplication {
        application { setup() }

        val response = client.put("/settings/dmf-settings") {
            contentType(ContentType.Application.Json)
            setBody(Json.encodeToString(UpdateDmfSettingsRequest.serializer(),
                UpdateDmfSettingsRequest(triggerSize = 1_000_000L, chunkSize = 0L, validationEnabled = true)))
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `PUT returns 400 for malformed JSON`() = testApplication {
        application { setup() }

        val response = client.put("/settings/dmf-settings") {
            contentType(ContentType.Application.Json)
            setBody("not-json")
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }
}
