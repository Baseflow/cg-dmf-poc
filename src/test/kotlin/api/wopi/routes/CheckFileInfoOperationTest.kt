// SPDX-License-Identifier: EUPL-1.2
// Copyright (C) 2026 Gemeente Utrecht
package com.baseflow.api.wopi.routes

import com.baseflow.api.WOPI_API_BASE_PATH
import com.baseflow.api.apiJsonConfig
import com.baseflow.api.middleware.AuditContext
import com.baseflow.api.models.EnkelvoudigInformatieObjectResponse
import com.baseflow.api.wopi.models.CheckFileInfoResponse
import com.baseflow.api.wopi.wopi.WopiDocumentService
import com.baseflow.api.wopi.wopiApiModule
import com.baseflow.config.WopiConfig
import com.baseflow.config.authenticationModule
import com.baseflow.services.EnkelvoudigInformatieObjectService
import com.baseflow.services.WopiSlatService
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.testing.testApplication
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.Json
import org.koin.dsl.module
import org.koin.ktor.ext.get
import org.koin.ktor.plugin.Koin
import org.koin.module.requestScope
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals

class CheckFileInfoOperationTest {

    private val mockEnkelvoudigInformatieObjectService = mockk<EnkelvoudigInformatieObjectService>()

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
                        scoped<WopiDocumentService> { mockk<WopiDocumentService>() }
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

    @Test
    fun `the CheckFileInfo operation should return required file information`() = testApplication {
        application {
            setup()
        }

        mockEnkelvoudigInformatieObjectService.also {
            coEvery { it.getById(dummyFileId, emptyList()) } returns dummyEnkelvoudigInformatieObject
        }

        val response: HttpResponse = client.get("${WOPI_API_BASE_PATH}/files/$dummyFileId?access_token=$dummyAccessToken")

        assertEquals(200, response.status.value)
        val fileInfo = Json.decodeFromString<CheckFileInfoResponse>(response.bodyAsText())
        assertEquals("automated_test_document.tst", fileInfo.baseFileName)
        assertEquals("2026-05-26T11:38:05Z", fileInfo.lastModifiedTime)
        assertEquals(42, fileInfo.size)
        assertEquals("1", fileInfo.version)
    }

    @Test
    fun `the CheckFileInfo operation should return supported WOPI host capabilities`() = testApplication {
        application {
            setup()
        }

        mockEnkelvoudigInformatieObjectService.also {
            coEvery { it.getById(dummyFileId, emptyList()) } returns dummyEnkelvoudigInformatieObject
        }

        val response: HttpResponse = client.get("${WOPI_API_BASE_PATH}/files/$dummyFileId?access_token=$dummyAccessToken")

        assertEquals(200, response.status.value)
        val capabilities = Json.decodeFromString<CheckFileInfoResponse>(response.bodyAsText())
        assertEquals(capabilities.supportedShareUrlTypes, null)
        assertEquals(capabilities.supportsAutosave, false)
        assertEquals(capabilities.supportsCobalt, null)
        assertEquals(capabilities.supportsContainers, false)
        assertEquals(capabilities.supportsDeleteFile, false)
        assertEquals(capabilities.supportsEcosystem, null)
        assertEquals(capabilities.supportsExtendedLockLength, null)
        assertEquals(capabilities.supportsFolders, null)
        assertEquals(capabilities.supportsGetFileWopiSrc, null)
        assertEquals(capabilities.supportsGetLock, true)
        assertEquals(capabilities.supportsLocks, true)
        assertEquals(capabilities.supportsPutRelativeFile, true)
        assertEquals(capabilities.supportsRename, true)
        assertEquals(capabilities.supportsUpdate, true)
        assertEquals(capabilities.supportsUserInfo, null)
    }

    @Test
    fun `the CheckFileInfo operation should return supported user metadata properties`() = testApplication {
        application {
            setup()
        }

        mockEnkelvoudigInformatieObjectService.also {
            coEvery { it.getById(dummyFileId, emptyList()) } returns dummyEnkelvoudigInformatieObject
        }

        val response: HttpResponse = client.get("${WOPI_API_BASE_PATH}/files/$dummyFileId?access_token=$dummyAccessToken")

        assertEquals(200, response.status.value)
        val userMetadata = Json.decodeFromString<CheckFileInfoResponse>(response.bodyAsText())
        assertEquals(userMetadata.isAnonymousUser, null)
        assertEquals(userMetadata.isEduUser, null)
        assertEquals(userMetadata.licenseCheckForEditIsEnabled, null)
        assertEquals(userMetadata.userFriendlyName, "Unknown user")
        assertEquals(userMetadata.userInfo, null)
    }

    @Test
    fun `the CheckFileInfo operation should return supported user permissions`() = testApplication {
        application {
            setup()
        }

        mockEnkelvoudigInformatieObjectService.also {
            coEvery { it.getById(dummyFileId, emptyList()) } returns dummyEnkelvoudigInformatieObject
        }

        val response: HttpResponse = client.get("${WOPI_API_BASE_PATH}/files/$dummyFileId?access_token=$dummyAccessToken")

        assertEquals(200, response.status.value)
        val userPermissions = Json.decodeFromString<CheckFileInfoResponse>(response.bodyAsText())
        assertEquals(userPermissions.readOnly, null)
        assertEquals(userPermissions.restrictedWebViewOnly, null)
        assertEquals(userPermissions.userCanAttend, null)
        assertEquals(userPermissions.userCanNotWriteRelative, null)
        assertEquals(userPermissions.userCanPresent, null)
        assertEquals(userPermissions.userCanRename, true)
        assertEquals(userPermissions.userCanWrite, true)
    }

    @Test
    fun `the CheckFileInfo operation should return notFound when the document does not exists`() = testApplication {
        application {
            setup()
        }

        mockEnkelvoudigInformatieObjectService.also {
            coEvery { it.getById(dummyFileId, emptyList()) } returns null
        }

        val response: HttpResponse = client.get("${WOPI_API_BASE_PATH}/files/$dummyFileId?access_token=$dummyAccessToken")
        assertEquals(404, response.status.value)
    }

    @Test
    fun `the CheckFileInfo operation should return notFound when the document size is null`() = testApplication {
        application {
            setup()
        }

        mockEnkelvoudigInformatieObjectService.also {
            coEvery { it.getById(dummyFileId, emptyList()) } returns EnkelvoudigInformatieObjectResponse(
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
                bestandsomvang = null,
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

        val response: HttpResponse = client.get("${WOPI_API_BASE_PATH}/files/$dummyFileId?access_token=$dummyAccessToken")
        assertEquals(404, response.status.value)
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
