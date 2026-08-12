// SPDX-License-Identifier: EUPL-1.2
// Copyright (C) 2026 Gemeente Utrecht
package com.baseflow.wopi.web

import com.baseflow.shared.api.WOPI_API_BASE_PATH
import com.baseflow.shared.api.WOPI_WEB_BASE_PATH
import com.baseflow.shared.api.middleware.configureStatusPages
import com.baseflow.shared.config.WopiConfig
import com.baseflow.shared.services.WopiSlatService
import com.baseflow.shared.services.models.SlatPayload
import io.ktor.client.request.get
import io.ktor.client.request.url
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.testing.testApplication
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Nested
import org.koin.dsl.module
import org.koin.ktor.ext.get
import org.koin.ktor.plugin.Koin
import org.koin.module.requestScope
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Clock

class WopiWebRoutesTest {
    private val mockWopiSlatService = mockk<WopiSlatService>(relaxed = true)

    private fun Application.setup() {
        install(Koin) {
            allowOverride(true)

            modules(
                module {
                    single<WopiConfig> { WopiConfig(true, "wopi automated tests") }
                    requestScope {
                        scoped<WopiSlatService> { mockWopiSlatService }
                    }
                },
            )
        }

        configureStatusPages()

        val wopiConfig = get<WopiConfig>()
        wopiWebModule(wopiConfig)
    }

    @Nested
    inner class HostPageTests {
        @Test
        fun `should render the host page with the access token, ttl and WOPI URLs`() = testApplication {
            application { setup() }

            every { mockWopiSlatService.validate(dummyAccessToken) } returns dummySlatPayload

            val response: HttpResponse = client.get("$WOPI_WEB_BASE_PATH/files/$dummyFileId") {
                url {
                    parameters.append("access_token", dummyAccessToken)
                    parameters.append("wopiClient", dummyWopiClientUrl)
                }
            }

            assertEquals(200, response.status.value)
            assertTrue(response.headers[HttpHeaders.ContentType]?.contains("text/html") == true)

            val body: String = response.bodyAsText()
            assertTrue(body.contains("id=\"office_form\""))
            assertTrue(body.contains("name=\"access_token\""))
            assertTrue(body.contains("value=\"$dummyAccessToken\""))
            assertTrue(body.contains("name=\"access_token_ttl\""))
            assertTrue(body.contains("value=\"${dummySlatPayload.expiresAt * 1000}\""))
            assertTrue(body.contains("new URL(\"$dummyWopiClientUrl\")"))
            assertTrue(body.contains("$WOPI_API_BASE_PATH/files/$dummyFileId"))
        }
    }

    @Nested
    inner class WopiClientParamTests {
        @Test
        fun `should return a bad request error when the wopiClient query parameter is missing`() = testApplication {
            application { setup() }

            every { mockWopiSlatService.validate(dummyAccessToken) } returns dummySlatPayload

            val response: HttpResponse = client.get("$WOPI_WEB_BASE_PATH/files/$dummyFileId?access_token=$dummyAccessToken")

            assertEquals(400, response.status.value)
            val body: String = response.bodyAsText()
            assertTrue(body.contains("Missing the"))
            assertTrue(body.contains("wopiClient"))
            assertTrue(body.contains("query parameter"))
        }

        @Test
        fun `should return a bad request error when the wopiClient query parameter is not a valid URL`() = testApplication {
            application { setup() }

            every { mockWopiSlatService.validate(dummyAccessToken) } returns dummySlatPayload

            val response: HttpResponse = client.get("$WOPI_WEB_BASE_PATH/files/$dummyFileId") {
                url {
                    parameters.append("access_token", dummyAccessToken)
                    parameters.append("wopiClient", "https://example.com/has space")
                }
            }

            assertEquals(400, response.status.value)
            assertTrue(response.bodyAsText().contains("Invalid wopiClient URL"))
        }
    }

    @Nested
    inner class AccessTokenTests {
        @Test
        fun `should return an unauthorized error when the access_token query parameter is missing`() = testApplication {
            application { setup() }

            val response: HttpResponse = client.get("$WOPI_WEB_BASE_PATH/files/$dummyFileId")

            assertEquals(401, response.status.value)
            assertTrue(response.bodyAsText().contains("Missing access_token query parameter or Authorization header."))
        }

        @Test
        fun `should return an unauthorized error when the access_token is invalid or expired`() = testApplication {
            application { setup() }

            every { mockWopiSlatService.validate(dummyAccessToken) } returns null

            val response: HttpResponse = client.get("$WOPI_WEB_BASE_PATH/files/$dummyFileId?access_token=$dummyAccessToken")

            assertEquals(401, response.status.value)
            assertTrue(response.bodyAsText().contains("Invalid or expired access_token."))
        }

        @Test
        fun `should return an unauthorized error when the token was issued for a different file`() = testApplication {
            application { setup() }

            val slatPayloadForOtherFile = SlatPayload(
                fileId = UUID.randomUUID().toString(),
                expiresAt = Clock.System.now().epochSeconds + 3600,
                userId = "1",
            )
            every { mockWopiSlatService.validate(dummyAccessToken) } returns slatPayloadForOtherFile

            val response: HttpResponse = client.get("$WOPI_WEB_BASE_PATH/files/$dummyFileId?access_token=$dummyAccessToken")

            assertEquals(401, response.status.value)
            assertTrue(response.bodyAsText().contains("The token supplied was not issued for this resource."))
        }
    }

    @Nested
    inner class FileIdValidationTests {
        @Test
        fun `should return a bad request error when the file_id path parameter is not a valid UUID`() = testApplication {
            application { setup() }

            val response: HttpResponse = client.get("$WOPI_WEB_BASE_PATH/files/not-a-uuid")

            assertEquals(400, response.status.value)
            assertTrue(response.bodyAsText().contains("Invalid file_id path parameter. Expected a UUID."))
        }
    }

    companion object {
        private val dummyFileId = UUID.fromString("12345678-1234-1234-1234-123456789012")

        private val dummySlatPayload = SlatPayload(
            fileId = dummyFileId.toString(),
            expiresAt = Clock.System.now().epochSeconds + 3600,
            userId = "1",
        )

        private const val dummyAccessToken = "test_token"
        private const val dummyWopiClientUrl = "https://collabora.example.com/browser/1234/cool.html"
    }
}
