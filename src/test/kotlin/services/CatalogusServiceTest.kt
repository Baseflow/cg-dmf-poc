// SPDX-License-Identifier: EUPL-1.2
// Copyright (C) 2026 Gemeente Utrecht
package com.baseflow.services

import com.auth0.jwt.JWT
import com.baseflow.config.OpenZaakConfig
import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.utils.io.*
import kotlinx.coroutines.runBlocking
import kotlin.test.*

class CatalogusServiceTest {

    private val defaultConfig = OpenZaakConfig(
        clientId = "test-client",
        clientSecret = "test-secret",
        validationEnabled = true
    )

    private fun createMockService(config: OpenZaakConfig = defaultConfig, handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData): CatalogusService {
        val mockEngine = MockEngine(handler)
        val httpClient = HttpClient(mockEngine)
        return CatalogusService(config, httpClient)
    }

    @Test
    fun `test generateJwtToken contains expected claims`() {
        val service = CatalogusService(defaultConfig)
        val jwtToken = service.generateJwtToken()

        assertNotNull(jwtToken)
        val decoded = JWT.decode(jwtToken)
        assertEquals("test-client", decoded.issuer)
        assertEquals("test-client", decoded.getClaim("client_id").asString())
        assertEquals("test-client", decoded.getClaim("user_id").asString())
        assertEquals("test-client", decoded.getClaim("user_representation").asString())
        assertNotNull(decoded.getClaim("iat").asLong())
    }

    @Test
    fun `test validateInformatieobjecttype success`() = runBlocking {
        val url = "https://example.com/api/v1/types/1"
        val service = createMockService { request ->
            assertEquals(url, request.url.toString())
            assertTrue(request.headers["Authorization"]!!.startsWith("Bearer "))
            val jsonResponse = """
{
    "url": "$url",
    "omschrijving": "Test Type",
    "vertrouwelijkheidaanduiding": "openbaar"
}
            """
            respond(
                content = ByteReadChannel(jsonResponse),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }

        service.validateInformatieobjecttype(url)
        service.close()
    }

    @Test
    fun `test validateInformatieobjecttype handles 404 error`() = runBlocking {
        val url = "https://example.com/api/v1/types/404"
        val service = createMockService {
            respond(
                content = ByteReadChannel("Not Found"),
                status = HttpStatusCode.NotFound,
                headers = headersOf(HttpHeaders.ContentType, "text/plain")
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
    fun `test validateInformatieobjecttype handles connection exception`() = runBlocking {
        val url = "https://example.com/api/v1/types/error"
        val mockEngine = MockEngine {
            throw Exception("Connection refused")
        }
        val service = CatalogusService(defaultConfig, HttpClient(mockEngine))

        val exception = assertFailsWith<Exception> {
            service.validateInformatieobjecttype(url)
        }

        assertTrue(exception.message!!.contains("Failed to connect to Catalogus"))
        assertTrue(exception.message!!.contains("Connection refused"))
        service.close()
    }

    @Test
    fun `test validateInformatieobjecttype skips when disabled`() = runBlocking {
        val config = OpenZaakConfig(validationEnabled = false)
        val mockEngine = MockEngine {
            fail("Should not be called when validation is disabled")
        }
        val service = CatalogusService(config, HttpClient(mockEngine))

        service.validateInformatieobjecttype("https://any-url.com")
        service.close()
    }

    @Test
    fun `test close method closes client`() {
        val mockEngine = MockEngine { respondOk() }
        val httpClient = HttpClient(mockEngine)
        val service = CatalogusService(defaultConfig, httpClient)
        
        service.close()
        
        runBlocking {
            assertFails {
                httpClient.get("https://any.com")
            }
        }
    }
}
