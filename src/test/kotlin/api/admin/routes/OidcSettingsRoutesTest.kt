// SPDX-License-Identifier: EUPL-1.2
// Copyright (C) 2026 Gemeente Utrecht
package com.baseflow.api.admin.routes

import com.baseflow.api.routes.TestBase
import com.baseflow.config.SecretCrypto
import com.baseflow.entities.OidcSettingsEntity
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals

private val SETTINGS_ID = UUID.fromString("00000000-0000-0000-0000-000000000001")

class OidcSettingsRoutesTest : TestBase("oidc_settings") {

    @Test
    fun `GET returns 404 when no settings are configured`() = testApplication {
        application { setup() }

        val response = client.get("/admin/oidc-settings") {
            header(HttpHeaders.Authorization, "Bearer test")
        }

        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    @Test
    fun `GET returns settings with decrypted clientSecret when a secret is stored`() = testApplication {
        application { setup() }

        val plainSecret = "my-super-secret"
        transaction {
            OidcSettingsEntity.new(SETTINGS_ID) {
                issuer = "https://auth.example.com"
                clientId = "my-client"
                clientSecretEncrypted = SecretCrypto.encrypt(plainSecret)
                updatedAt = Clock.System.now()
                    .toLocalDateTime(TimeZone.UTC)
            }
        }

        val response = client.get("/admin/oidc-settings") {
            header(HttpHeaders.Authorization, "Bearer test")
        }

        assertEquals(HttpStatusCode.OK, response.status)

        val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
        assertEquals("https://auth.example.com", body["issuer"]?.jsonPrimitive?.content)
        assertEquals("my-client", body["clientId"]?.jsonPrimitive?.content)
        assertEquals(true, body["hasSecret"]?.jsonPrimitive?.boolean)
        assertEquals(plainSecret, body["clientSecret"]?.jsonPrimitive?.content)
    }

    @Test
    fun `GET returns null clientSecret when no secret is stored`() = testApplication {
        application { setup() }

        transaction {
            OidcSettingsEntity.new(SETTINGS_ID) {
                issuer = "https://auth.example.com"
                clientId = "my-client"
                clientSecretEncrypted = null
                updatedAt = Clock.System.now()
                    .toLocalDateTime(TimeZone.UTC)
            }
        }

        val response = client.get("/admin/oidc-settings") {
            header(HttpHeaders.Authorization, "Bearer test")
        }

        assertEquals(HttpStatusCode.OK, response.status)

        val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
        assertEquals(false, body["hasSecret"]?.jsonPrimitive?.boolean)
        // explicitNulls = false: null fields are omitted from the serialized JSON
        assertEquals(null, body["clientSecret"])
    }
}
