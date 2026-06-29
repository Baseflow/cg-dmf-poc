// SPDX-License-Identifier: EUPL-1.2
// Copyright (C) 2026 Gemeente Utrecht
package wopi.api.routes

import com.baseflow.shared.api.WOPI_API_BASE_PATH
import com.baseflow.shared.api.apiJsonConfig
import com.baseflow.shared.api.middleware.AuditContext
import com.baseflow.shared.api.middleware.configureStatusPages
import com.baseflow.shared.api.models.EnkelvoudigInformatieObjectResponse
import com.baseflow.shared.config.WopiConfig
import com.baseflow.shared.config.authenticationModule
import com.baseflow.shared.services.EnkelvoudigInformatieObjectService
import com.baseflow.shared.services.WopiSlatService
import com.baseflow.wopi.api.models.WopiLockPayload
import com.baseflow.wopi.api.models.WopiLockResult
import com.baseflow.wopi.api.wopiApiModule
import com.baseflow.wopi.services.WopiDocumentService
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.statement.HttpResponse
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.testing.testApplication
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.datetime.LocalDate
import org.koin.dsl.module
import org.koin.ktor.ext.get
import org.koin.ktor.plugin.Koin
import org.koin.module.requestScope
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals

class LockOperationTest {

    private val mockEnkelvoudigInformatieObjectService = mockk<EnkelvoudigInformatieObjectService>()
    private val mockWopiDocumentService = mockk<WopiDocumentService>()

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
                        scoped<EnkelvoudigInformatieObjectService> { mockEnkelvoudigInformatieObjectService }
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
        configureStatusPages()
        authenticationModule()
        wopiApiModule(wopiConfig)
    }

    @Test
    fun `the LOCK operation should return 200 when the file is successfully locked`() = testApplication {
        application {
            setup()
        }

        mockEnkelvoudigInformatieObjectService.also {
            coEvery { it.getById(dummyFileId, expand = emptyList()) } returns dummyEnkelvoudigInformatieObject
        }

        coEvery { mockWopiDocumentService.wopiLock(dummyFileId, "lock-value") } returns WopiLockResult.Success

        val response: HttpResponse =
            client.post("$WOPI_API_BASE_PATH/files/$dummyFileId?access_token=$dummyAccessToken") {
                header("X-WOPI-Override", "LOCK")
                header("X-WOPI-Lock", "lock-value")
            }
        assertEquals(200, response.status.value)
    }

    @Test
    fun `the LOCK operation should return 400 Bad Request when X-WOPI-Lock was not provided or was empty`() = testApplication {
        application {
            setup()
        }

        mockEnkelvoudigInformatieObjectService.also {
            coEvery { it.getById(dummyFileId, expand = emptyList()) } returns dummyEnkelvoudigInformatieObject
        }

        coEvery { mockWopiDocumentService.wopiLock(dummyFileId, "lock-value") } returns WopiLockResult.Success

        val response: HttpResponse =
            client.post("$WOPI_API_BASE_PATH/files/$dummyFileId?access_token=$dummyAccessToken") {
                header("X-WOPI-Override", "LOCK")
            }
        assertEquals(400, response.status.value)
    }

    @Test
    fun `the LOCK operation should return 401 Unauthorized when the access token is invalid`() = testApplication {
        application {
            setup()
        }

        val response: HttpResponse =
            client.post("$WOPI_API_BASE_PATH/files/$dummyFileId?access_token=invalid_token") {
                header("X-WOPI-Override", "LOCK")
                header("X-WOPI-Lock", "lock-value")
            }
        assertEquals(401, response.status.value)
    }

    @Test
    fun `the LOCK operation should return 404 Not Found when the resource is not found`() = testApplication {
        application {
            setup()
        }

        coEvery { mockWopiDocumentService.wopiLock(dummyFileId, "lock-value") } returns null

        val response: HttpResponse =
            client.post("$WOPI_API_BASE_PATH/files/$dummyFileId?access_token=$dummyAccessToken") {
                header("X-WOPI-Override", "LOCK")
                header("X-WOPI-Lock", "lock-value")
            }
        assertEquals(404, response.status.value)
    }

    @Test
    fun `the LOCK operation should return 409 Conflict when there is a lock mismatch`() = testApplication {
        application {
            setup()
        }

        coEvery {
            mockWopiDocumentService.wopiLock(dummyFileId, "lock-value")
        } returns WopiLockResult.LockMismatch(WopiLockPayload(lock = "existing-lock"))

        val response: HttpResponse =
            client.post("$WOPI_API_BASE_PATH/files/$dummyFileId?access_token=$dummyAccessToken") {
                header("X-WOPI-Override", "LOCK")
                header("X-WOPI-Lock", "lock-value")
            }
        assertEquals(409, response.status.value)
        assertEquals("existing-lock", response.headers["X-WOPI-Lock"])
    }

    companion object {
        private val dummyFileId = UUID.fromString("12345678-1234-1234-1234-123456789012")

        private val dummyAccessToken = "test_token"

        private val dummyEnkelvoudigInformatieObject = EnkelvoudigInformatieObjectResponse(
            id = dummyFileId.toString(),
            url = null,
            identificatie = null,
            bronorganisatie = "Test organisation",
            creatiedatum = LocalDate(1978, 1, 1),
            titel = "Automated test document",
            versie = 1,
            vertrouwelijkheidaanduiding = null,
            auteur = "Automated test author",
            status = null,
            formaat = null,
            taal = "en",
            bestandsnaam = "automated_test_document.tst",
            inhoud = null,
            bestandsomvang = 42,
            link = null,
            beschrijving = null,
            beginRegistratie = "2026-05-26T11:38:05Z",
            indicatieGebruiksrecht = null,
            verschijningsvorm = null,
            ondertekening = null,
            integriteit = null,
            informatieobjecttype = "https://test.example.com/type/1",
            trefwoorden = emptyList(),
            inhoudIsVervallen = false,
            bestandsdelen = emptyList(),
            lock = "",
            locked = false,
            expand = null,
        )
    }
}
