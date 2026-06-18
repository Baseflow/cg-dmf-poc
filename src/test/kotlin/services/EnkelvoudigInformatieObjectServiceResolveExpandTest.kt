// SPDX-License-Identifier: EUPL-1.2
// Copyright (C) 2026 Gemeente Utrecht
package com.baseflow.shared.services

import com.baseflow.shared.api.middleware.AuditContext
import com.baseflow.shared.config.ApplicationConfig
import com.baseflow.shared.entities.settings.ApiConnectionSettingEntity
import com.baseflow.shared.entities.settings.ApiConnectionType
import com.baseflow.shared.services.models.QueryEnkelvoudigeInformatieObjectenFilter
import com.baseflow.shared.tooling.AllTables
import com.baseflow.testutils.TestDataFactory
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteReadChannel
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.util.UUID
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock

/**
 * Tests for resolveExpand behaviour, exercised via the public getById / getAll surface.
 *
 * A real CatalogusService is wired with a MockEngine so that no actual HTTP call is made,
 * but the full expand code-path runs inside the service.
 */
class EnkelvoudigInformatieObjectServiceResolveExpandTest {

    private val iotypeUrl = TestDataFactory.VALID_INFORMATIEOBJECTTYPE_URL

    private val iotypeJson = """
        {
            "url": "$iotypeUrl",
            "omschrijving": "Besluit",
            "vertrouwelijkheidaanduiding": "openbaar"
        }
    """.trimIndent()

    private lateinit var service: EnkelvoudigInformatieObjectService

    /** Builds the service with a CatalogusService backed by the given MockEngine. */
    private fun buildService(mockEngine: MockEngine): EnkelvoudigInformatieObjectService {
        val catalogusService = CatalogusService(HttpClient(mockEngine))
        val mockStorageService = mockk<StorageService>()
        every { mockStorageService.uploadFile(any(), any()) } answers { secondArg<ByteArray>().size.toLong() }
        val auditContext = AuditContext()
        return EnkelvoudigInformatieObjectService(
            storageService = mockStorageService,
            applicationConfig = ApplicationConfig,
            catalogusService = catalogusService,
            auditTrailService = AuditTrailService(auditContext),
            auditContext = auditContext,
            bestandsDeelService = BestandsDeelService(),
        )
    }

    @BeforeTest
    fun setup() {
        Database.connect(
            "jdbc:h2:mem:test_expand;DB_CLOSE_DELAY=-1;",
            driver = "org.h2.Driver",
            user = "root",
            password = "",
        )
        transaction {
            AllTables.createMissing()
            ApiConnectionSettingEntity.new {
                name = "openzaak-test"
                baseUrl = "https://openzaak.dev.baseflow.com"
                clientId = "test-client"
                clientSecret = "test-secret"
                apiType = ApiConnectionType.ZTC.value
                validationEnabled = false
                updatedAt = Clock.System.now()
            }
        }

        val mockEngine = MockEngine.Companion {
            respond(
                content = ByteReadChannel(iotypeJson),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        service = buildService(mockEngine)
    }

    @AfterTest
    fun teardown() {
        transaction { SchemaUtils.drop(*AllTables.tables.reversedArray()) }
    }

    @Test
    fun `getById with expand informatieobjecttype populates _expand field`() = runBlocking {
        val created = service.create(TestDataFactory.generateTestDocument())
        val id = UUID.fromString(created.id)

        val result = service.getById(id, expand = listOf("informatieobjecttype"))

        assertNotNull(result)
        val expand = result.expand
        assertNotNull(expand, "_expand should be present")
        assertTrue("informatieobjecttype" in expand, "_expand should contain 'informatieobjecttype' key")
        val expandedType = expand["informatieobjecttype"].toString()
        assertTrue(
            expandedType.contains("Besluit"),
            "expanded value should contain the omschrijving from mock response",
        )
    }

    @Test
    fun `getById without expand leaves _expand null`() = runBlocking {
        val created = service.create(TestDataFactory.generateTestDocument())
        val id = UUID.fromString(created.id)

        val result = service.getById(id, expand = emptyList())

        assertNotNull(result)
        assertNull(result.expand, "_expand should be null when no expand fields are requested")
    }

    @Test
    fun `getById with unknown expand field leaves _expand null`() = runBlocking {
        val created = service.create(TestDataFactory.generateTestDocument())
        val id = UUID.fromString(created.id)

        val result = service.getById(id, expand = listOf("nonexistent"))

        assertNotNull(result)
        assertNull(result.expand, "_expand should be null when expand field is not recognised")
    }

    @Test
    fun `getById gracefully swallows expand fetch failure and returns response without _expand`() = runBlocking {
        val failingEngine = MockEngine.Companion { throw Exception("Connection refused") }
        val failingService = buildService(failingEngine)

        val created = service.create(TestDataFactory.generateTestDocument())
        val id = UUID.fromString(created.id)

        // getById on the failing service: the DB record was created via the good service above
        val result = failingService.getById(id, expand = listOf("informatieobjecttype"))

        assertNotNull(result, "response should still be returned even when expand fails")
        assertNull(result.expand, "_expand should be null when fetch fails")
    }

    @Test
    fun `getAll with expand informatieobjecttype populates _expand on all results`() = runBlocking {
        service.create(TestDataFactory.generateTestDocument(bestandsnaam = "a.pdf"))
        service.create(TestDataFactory.generateTestDocument(bestandsnaam = "b.pdf"))

        val filter = QueryEnkelvoudigeInformatieObjectenFilter(expand = listOf("informatieobjecttype"))
        val (results, _) = service.getAll(filter)

        assertEquals(2, results.size)
        results.forEach { result ->
            val expand = result.expand
            assertNotNull(expand, "_expand should be present for all results")
            assertTrue("informatieobjecttype" in expand)
        }
    }

    @Test
    fun `getAll without expand leaves _expand null on all results`() = runBlocking {
        service.create(TestDataFactory.generateTestDocument())

        val (results, _) = service.getAll(QueryEnkelvoudigeInformatieObjectenFilter())

        results.forEach { result ->
            assertNull(result.expand, "_expand should be null when no expand is requested")
        }
    }
}
