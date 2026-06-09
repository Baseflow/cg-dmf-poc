// SPDX-License-Identifier: EUPL-1.2
// Copyright (C) 2026 Gemeente Utrecht
package com.baseflow.shared.services

import com.auth0.jwt.JWT
import com.baseflow.shared.entities.settings.ApiConnectionSettingEntity
import com.baseflow.shared.entities.settings.ApiConnectionType
import com.baseflow.shared.tooling.AllTables
import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.utils.io.*
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.util.UUID
import kotlin.test.*
import kotlin.time.Clock

class CatalogusServiceTest {

    @BeforeTest
    fun setUp() {
        val dbName = "catalogus_service_${UUID.randomUUID()}"
        Database.connect(
            "jdbc:h2:mem:$dbName;DB_CLOSE_DELAY=-1;",
            driver = "org.h2.Driver",
            user = "root",
            password = "",
        )
        transaction { AllTables.createMissing() }
    }

    private fun insertConnection(
        name: String = "test-connection",
        baseUrl: String = "https://example.com",
        clientId: String = "test-client",
        clientSecret: String = "test-secret",
        apiType: ApiConnectionType = ApiConnectionType.ZTC,
        validationEnabled: Boolean = true,
    ): UUID = transaction {
        ApiConnectionSettingEntity.new {
            this.name = name
            this.baseUrl = baseUrl
            this.clientId = clientId
            this.clientSecret = clientSecret
            this.apiType = apiType.value
            this.validationEnabled = validationEnabled
            this.updatedAt = Clock.System.now().toLocalDateTime(TimeZone.UTC)
        }.id.value
    }

    private fun createMockService(handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData): CatalogusService {
        val mockEngine = MockEngine(handler)
        val httpClient = HttpClient(mockEngine)
        return CatalogusService(httpClient)
    }

    // -----------------------------------------------------------------------
    // generateJwtToken
    // -----------------------------------------------------------------------

    @Test
    fun `generateJwtToken contains expected claims`() {
        val service = CatalogusService()
        val jwtToken = service.generateJwtToken("test-client", "test-secret")

        assertNotNull(jwtToken)
        val decoded = JWT.decode(jwtToken)
        assertEquals("test-client", decoded.issuer)
        assertEquals("test-client", decoded.getClaim("client_id").asString())
        assertEquals("test-client", decoded.getClaim("user_id").asString())
        assertEquals("test-client", decoded.getClaim("user_representation").asString())
        assertNotNull(decoded.getClaim("iat").asLong())
    }

    // -----------------------------------------------------------------------
    // validateInformatieobjecttype
    // -----------------------------------------------------------------------

    @Test
    fun `validateInformatieobjecttype returns null when no ZTC connection exists`() = runBlocking {
        val service = createMockService { fail("Should not be called") }

        val result = service.validateInformatieobjecttype("https://example.com/api/v1/types/1")

        assertNull(result)
        service.close()
    }

    @Test
    fun `validateInformatieobjecttype skips when validationEnabled is false`() = runBlocking {
        insertConnection(baseUrl = "https://example.com", validationEnabled = false)
        val service = createMockService { fail("Should not be called when validation is disabled") }

        val result = service.validateInformatieobjecttype("https://example.com/api/v1/types/1")

        assertNull(result)
        service.close()
    }

    @Test
    fun `validateInformatieobjecttype skips when URL does not match any ZTC connection`() = runBlocking {
        insertConnection(baseUrl = "https://other.example.com")
        val service = createMockService { fail("Should not be called") }

        val result = service.validateInformatieobjecttype("https://example.com/api/v1/types/1")

        assertNull(result)
        service.close()
    }

    @Test
    fun `validateInformatieobjecttype success`() = runBlocking {
        insertConnection(baseUrl = "https://example.com", clientId = "test-client", clientSecret = "test-secret")
        val url = "https://example.com/api/v1/types/1"
        val service = createMockService { request ->
            assertEquals(url, request.url.toString())
            assertTrue(request.headers["Authorization"]!!.startsWith("Bearer "))
            respond(
                content = ByteReadChannel("""{"url": "$url", "omschrijving": "Test Type", "vertrouwelijkheidaanduiding": "openbaar"}"""),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }

        val result = service.validateInformatieobjecttype(url)

        assertNotNull(result)
        assertEquals(url, result.url)
        assertEquals("Test Type", result.omschrijving)
        service.close()
    }

    @Test
    fun `validateInformatieobjecttype handles 404 error`() = runBlocking {
        insertConnection(baseUrl = "https://example.com")
        val url = "https://example.com/api/v1/types/404"
        val service = createMockService {
            respond(
                content = ByteReadChannel("Not Found"),
                status = HttpStatusCode.NotFound,
                headers = headersOf(HttpHeaders.ContentType, "text/plain"),
            )
        }

        val exception = assertFailsWith<Exception> {
            service.validateInformatieobjecttype(url)
        }

        assertTrue(exception.message!!.contains("Status: 404"))
        assertTrue(exception.message!!.contains("Error fetching information object type"))
        service.close()
    }

    @Test
    fun `validateInformatieobjecttype handles connection exception`() = runBlocking {
        insertConnection(baseUrl = "https://example.com")
        val url = "https://example.com/api/v1/types/error"
        val mockEngine = MockEngine { throw Exception("Connection refused") }
        val service = CatalogusService(HttpClient(mockEngine))

        val exception = assertFailsWith<Exception> {
            service.validateInformatieobjecttype(url)
        }

        assertTrue(exception.message!!.contains("Failed to connect to Catalogus"))
        assertTrue(exception.message!!.contains("Connection refused"))
        service.close()
    }

    // -----------------------------------------------------------------------
    // fetchJsonFromUrl
    // -----------------------------------------------------------------------

    @Test
    fun `fetchJsonFromUrl throws when no connection matches URL`() = runBlocking {
        val service = createMockService { respondOk() }

        assertFailsWith<IllegalArgumentException> {
            service.fetchJsonFromUrl("https://other-host.example.com/api/v1/resource/1")
        }

        service.close()
    }

    @Test
    fun `fetchJsonFromUrl success returns parsed JsonObject`() = runBlocking {
        insertConnection(baseUrl = "https://openzaak.example.com", clientId = "test-client", clientSecret = "test-secret")
        val url = "https://openzaak.example.com/api/v1/resource/1"
        val service = createMockService { request ->
            assertEquals(url, request.url.toString())
            assertTrue(request.headers["Authorization"]!!.startsWith("Bearer "))
            respond(
                content = ByteReadChannel("""{"key": "value", "count": 42}"""),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }

        val result = service.fetchJsonFromUrl(url)

        assertNotNull(result)
        assertEquals("value", result["key"].toString().trim('"'))
        service.close()
    }

    @Test
    fun `fetchJsonFromUrl succeeds when baseUrl has no trailing slash`() = runBlocking {
        insertConnection(baseUrl = "https://openzaak.example.com")
        val url = "https://openzaak.example.com/api/v1/resource/1"
        val service = createMockService {
            respond(
                content = ByteReadChannel("""{"key": "value"}"""),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }

        val result = service.fetchJsonFromUrl(url)

        assertNotNull(result)
        assertEquals("value", result["key"].toString().trim('"'))
        service.close()
    }

    @Test
    fun `fetchJsonFromUrl succeeds when baseUrl has trailing slash`() = runBlocking {
        insertConnection(baseUrl = "https://openzaak.example.com/")
        val url = "https://openzaak.example.com/api/v1/resource/1"
        val service = createMockService {
            respond(
                content = ByteReadChannel("""{"key": "value"}"""),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }

        val result = service.fetchJsonFromUrl(url)

        assertNotNull(result)
        assertEquals("value", result["key"].toString().trim('"'))
        service.close()
    }

    @Test
    fun `fetchJsonFromUrl throws on non-200 status`() = runBlocking {
        insertConnection(baseUrl = "https://openzaak.example.com")
        val url = "https://openzaak.example.com/api/v1/resource/missing"
        val service = createMockService {
            respond(
                content = ByteReadChannel("Not Found"),
                status = HttpStatusCode.NotFound,
                headers = headersOf(HttpHeaders.ContentType, "text/plain"),
            )
        }

        val exception = assertFailsWith<Exception> {
            service.fetchJsonFromUrl(url)
        }

        assertTrue(exception.message!!.contains("Error fetching resource"))
        assertTrue(exception.message!!.contains("Status: 404"))
        service.close()
    }

    @Test
    fun `fetchJsonFromUrl wraps connection exception`() = runBlocking {
        insertConnection(baseUrl = "https://openzaak.example.com")
        val url = "https://openzaak.example.com/api/v1/resource/1"
        val mockEngine = MockEngine { throw Exception("Network unreachable") }
        val service = CatalogusService(HttpClient(mockEngine))

        val exception = assertFailsWith<Exception> {
            service.fetchJsonFromUrl(url)
        }

        assertTrue(exception.message!!.contains("Failed to fetch URL"))
        assertTrue(exception.message!!.contains("Network unreachable"))
        service.close()
    }

    @Test
    fun `fetchJsonFromUrl includes bearer token in request`() = runBlocking {
        insertConnection(baseUrl = "https://openzaak.example.com", clientId = "test-client", clientSecret = "test-secret")
        val url = "https://openzaak.example.com/api/v1/resource/1"
        var capturedAuthHeader: String? = null
        val service = createMockService { request ->
            capturedAuthHeader = request.headers["Authorization"]
            respond(
                content = ByteReadChannel("""{"result": "ok"}"""),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }

        service.fetchJsonFromUrl(url)

        assertNotNull(capturedAuthHeader)
        assertTrue(capturedAuthHeader!!.startsWith("Bearer "))
        val token = capturedAuthHeader!!.removePrefix("Bearer ")
        val decoded = JWT.decode(token)
        assertEquals("test-client", decoded.issuer)
        service.close()
    }

    @Test
    fun `close method closes client`() {
        val mockEngine = MockEngine { respondOk() }
        val httpClient = HttpClient(mockEngine)
        val service = CatalogusService(httpClient)

        service.close()

        runBlocking {
            assertFails {
                httpClient.get("https://any.com")
            }
        }
    }
}
