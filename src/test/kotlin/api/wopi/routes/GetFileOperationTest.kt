// SPDX-License-Identifier: EUPL-1.2
// Copyright (C) 2026 Gemeente Utrecht
package com.baseflow.api.wopi.routes

import com.baseflow.api.WOPI_API_BASE_PATH
import com.baseflow.api.apiJsonConfig
import com.baseflow.api.middleware.AuditContext
import com.baseflow.api.wopi.services.WopiDocumentService
import com.baseflow.api.wopi.services.WopiFileVersion
import com.baseflow.api.wopi.wopiApiModule
import com.baseflow.config.WopiConfig
import com.baseflow.config.authenticationModule
import com.baseflow.services.EnkelvoudigInformatieObjectService
import com.baseflow.services.WopiSlatService
import com.baseflow.testutils.decodeRfc5987
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentDisposition
import io.ktor.http.HttpHeaders
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.testing.testApplication
import io.mockk.every
import io.mockk.mockk
import io.ktor.http.ContentType
import org.junit.jupiter.api.Nested
import org.koin.dsl.module
import org.koin.ktor.ext.get
import org.koin.ktor.plugin.Koin
import org.koin.module.requestScope
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals

class GetFileOperationTest {
    private val mockWopiDocumentService = mockk<WopiDocumentService>(relaxed = true)

    private fun Application.setup() {
        install(Koin) {
            allowOverride(true)

            modules(
                module {
                    val mockWopiSlatService = mockk<WopiSlatService>(relaxed = true).also {
                        every { it.validate(dummyAccessToken) } returns dummyFileId
                    }

                    single<WopiConfig> { WopiConfig(true, "wopi automated tests") }
                    requestScope {
                        scoped<AuditContext> { AuditContext() }
                        scoped<EnkelvoudigInformatieObjectService> { mockk<EnkelvoudigInformatieObjectService>() }
                        scoped<WopiDocumentService> { mockWopiDocumentService }
                        scoped<WopiSlatService> { mockWopiSlatService }
                    }
                },
            )
        }

        install(ContentNegotiation) {
            json(apiJsonConfig())
        }

        val wopiConfig = get<WopiConfig>()
        authenticationModule()
        wopiApiModule(wopiConfig)
    }

    @Nested
    inner class MaxExpectedSizeHeaderTests {
        @Test
        fun `should return a bad request error when X-WOPI-MaxExpectedSize contains a negative value`() = testApplication {
            application {
                setup()
            }

            val response: HttpResponse =
                client.get("${WOPI_API_BASE_PATH}/files/$dummyFileId/contents?access_token=$dummyAccessToken") {
                    header("X-WOPI-MaxExpectedSize", "-1")
                }

            assertEquals(400, response.status.value)
        }

        @Test
        fun `should return a bad request error when X-WOPI-MaxExpectedSize contains a non-numeric value`() = testApplication {
            application {
                setup()
            }

            val response: HttpResponse =
                client.get("${WOPI_API_BASE_PATH}/files/$dummyFileId/contents?access_token=$dummyAccessToken") {
                    header("X-WOPI-MaxExpectedSize", "test123")
                }

            assertEquals(400, response.status.value)
        }

        @Test
        fun `should return a bad request error when X-WOPI-MaxExpectedSize contains an out-of-range value`() = testApplication {
            application {
                setup()
            }

            val response: HttpResponse =
                client.get("${WOPI_API_BASE_PATH}/files/$dummyFileId/contents?access_token=$dummyAccessToken") {
                    header("X-WOPI-MaxExpectedSize", "4294967296")
                }

            assertEquals(400, response.status.value)
        }

        @Test
        fun `should return a precondition failed error when X-WOPI-MaxExpectedSize is smaller than file size`() = testApplication {
            application {
                setup()
            }

            mockWopiDocumentService.also {
                every { it.wopiGetFileVersion(dummyFileId) } returns createDummyWopiFileVersion(100)
            }

            val response: HttpResponse =
                client.get("${WOPI_API_BASE_PATH}/files/$dummyFileId/contents?access_token=$dummyAccessToken") {
                    header("X-WOPI-MaxExpectedSize", "99")
                }

            assertEquals(412, response.status.value)
        }

        @Test
        fun `should return a success code when file size is not larger than X-WOPI-MaxExpectedSize`() = testApplication {
            application {
                setup()
            }

            mockWopiDocumentService.also {
                every { it.wopiGetFileVersion(dummyFileId) } returns createDummyWopiFileVersion(100)
            }

            val response: HttpResponse =
                client.get("${WOPI_API_BASE_PATH}/files/$dummyFileId/contents?access_token=$dummyAccessToken") {
                    header("X-WOPI-MaxExpectedSize", "100")
                }

            assertEquals(200, response.status.value)
        }

        @Test
        fun `should return a precondition failed when X-WOPI-MaxExpectedSize is not specified and file is larger than max uint`() =
            testApplication {
                application {
                    setup()
                }

                mockWopiDocumentService.also {
                    every { it.wopiGetFileVersion(dummyFileId) } returns createDummyWopiFileVersion(UInt.MAX_VALUE.toLong() + 1)
                }

                val response: HttpResponse =
                    client.get("${WOPI_API_BASE_PATH}/files/$dummyFileId/contents?access_token=$dummyAccessToken")

                assertEquals(412, response.status.value)
            }

        @Test
        fun `should return a success code when X-WOPI-MaxExpectedSize is not specified and file size not larger than max uint`() =
            testApplication {
                application {
                    setup()
                }

                mockWopiDocumentService.also {
                    every { it.wopiGetFileVersion(dummyFileId) } returns createDummyWopiFileVersion(UInt.MAX_VALUE.toLong())
                }

                val response: HttpResponse =
                    client.get("${WOPI_API_BASE_PATH}/files/$dummyFileId/contents?access_token=$dummyAccessToken")

                assertEquals(200, response.status.value)
            }
    }

    @Nested
    inner class FileNotFoundTests {
        @Test
        fun `should return a not found error when file identifier doesn't exist`() = testApplication {
            application {
                setup()
            }

            mockWopiDocumentService.also {
                every { it.wopiGetFileVersion(dummyFileId) } returns null
            }

            val response: HttpResponse =
                client.get("${WOPI_API_BASE_PATH}/files/$dummyFileId/contents?access_token=$dummyAccessToken")

            assertEquals(404, response.status.value)
        }

        @Test
        fun `should return a not found error when file location is blank`() = testApplication {
            application {
                setup()
            }

            mockWopiDocumentService.also {
                every { it.wopiGetFileVersion(dummyFileId) } returns createDummyWopiFileVersion(100, fileLocation = "")
            }

            val response: HttpResponse =
                client.get("${WOPI_API_BASE_PATH}/files/$dummyFileId/contents?access_token=$dummyAccessToken")

            assertEquals(404, response.status.value)
        }
    }

    @Nested
    inner class FileFormatTests {
        @Test
        fun `should fallback to OctetStream mimetype if format is not specified`() = testApplication {
            application {
                setup()
            }

            mockWopiDocumentService.also {
                every { it.wopiGetFileVersion(dummyFileId) } returns createDummyWopiFileVersion(100, format = null)
            }

            val response: HttpResponse =
                client.get("${WOPI_API_BASE_PATH}/files/$dummyFileId/contents?access_token=$dummyAccessToken")

            assertEquals(200, response.status.value)
            assertEquals("application/octet-stream", response.headers["Content-Type"])
        }

        @Test
        fun `should fallback to OctetStream mimetype if format cannot be parsed`() = testApplication {
            application {
                setup()
            }

            mockWopiDocumentService.also {
                every { it.wopiGetFileVersion(dummyFileId) } returns createDummyWopiFileVersion(
                    100,
                    format = "invalid format",
                )
            }

            val response: HttpResponse =
                client.get("${WOPI_API_BASE_PATH}/files/$dummyFileId/contents?access_token=$dummyAccessToken")

            assertEquals(200, response.status.value)
            assertEquals("application/octet-stream", response.headers["Content-Type"])
        }

        @Test
        fun `should return the correct mimetype matching the file format`() = testApplication {
            application {
                setup()
            }

            mockWopiDocumentService.also {
                every { it.wopiGetFileVersion(dummyFileId) } returns createDummyWopiFileVersion(
                    100,
                    format = ContentType.Application.Pdf.toString(),
                )
            }

            val response: HttpResponse =
                client.get("${WOPI_API_BASE_PATH}/files/$dummyFileId/contents?access_token=$dummyAccessToken")

            assertEquals(200, response.status.value)
            assertEquals(ContentType.Application.Pdf.toString(), response.headers["Content-Type"])
        }
    }

    @Nested
    inner class FileNameTests {
        @Test
        fun `should use EIO bestandsnaam for file name if not blank`() = testApplication {
            application {
                setup()
            }

            mockWopiDocumentService.also {
                every { it.wopiGetFileVersion(dummyFileId) } returns createDummyWopiFileVersion(fileName = "test.pdf")
            }

            val response: HttpResponse =
                client.get("${WOPI_API_BASE_PATH}/files/$dummyFileId/contents?access_token=$dummyAccessToken")

            val contentDisposition = response.headers[HttpHeaders.ContentDisposition]
                ?.let { ContentDisposition.parse(it) }
            val fileName = contentDisposition?.parameter(ContentDisposition.Parameters.FileNameAsterisk)
                ?.let { decodeRfc5987(it) }

            assertEquals(200, response.status.value)
            assertEquals("test.pdf", fileName)
        }

        @Test
        fun `should use EIO title as file name if bestandsnaam is blank`() = testApplication {
            application {
                setup()
            }

            mockWopiDocumentService.also {
                every { it.wopiGetFileVersion(dummyFileId) } returns createDummyWopiFileVersion(fileName = "", title = "Test bestand")
            }

            val response: HttpResponse =
                client.get("${WOPI_API_BASE_PATH}/files/$dummyFileId/contents?access_token=$dummyAccessToken")

            val contentDisposition = response.headers[HttpHeaders.ContentDisposition]
                ?.let { ContentDisposition.parse(it) }
            val fileName = contentDisposition?.parameter(ContentDisposition.Parameters.FileNameAsterisk)?.let { decodeRfc5987(it) }

            assertEquals(200, response.status.value)
            assertEquals("Test bestand", fileName)
        }

        @Test
        fun `should use generated file name if bestandsnaam and titel of the EIO are blank`() = testApplication {
            application {
                setup()
            }

            mockWopiDocumentService.also {
                every { it.wopiGetFileVersion(dummyFileId) } returns
                    createDummyWopiFileVersion(fileName = "", title = "", recordId = dummyFileId)
            }

            val response: HttpResponse =
                client.get("${WOPI_API_BASE_PATH}/files/$dummyFileId/contents?access_token=$dummyAccessToken")

            val contentDisposition = response.headers[HttpHeaders.ContentDisposition]
                ?.let { ContentDisposition.parse(it) }
            val fileName = contentDisposition?.parameter(ContentDisposition.Parameters.FileNameAsterisk)?.let { decodeRfc5987(it) }

            assertEquals(200, response.status.value)
            assertEquals("document-$dummyFileId", fileName)
        }
    }

    private fun createDummyWopiFileVersion(
        fileSize: Long = 100,
        fileName: String = "test.pdf",
        fileLocation: String = "https://test.example.com/test.pdf",
        title: String = "Test bestand",
        format: String? = "application/pdf",
        recordId: UUID = UUID.randomUUID(),
    ) = WopiFileVersion(
        bestandsnaam = fileName,
        bestandsomvang = fileSize,
        bestandsLocatie = fileLocation,
        bestandsRepository = null,
        titel = title,
        formaat = format,
        versie = 1,
        recordId = recordId,
    )

    companion object {
        private val dummyFileId = UUID.fromString("12345678-1234-1234-1234-123456789012")

        private val dummyAccessToken = "test_token"
    }
}
