// SPDX-License-Identifier: EUPL-1.2
// Copyright (C) 2026 Gemeente Utrecht
package com.baseflow.api.documenten.routes

import com.baseflow.api.apiJsonConfig
import com.baseflow.api.infra.healthModule
import com.baseflow.services.DependencyStatus
import com.baseflow.services.HealthCheckService
import com.baseflow.services.StorageStatus
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.testing.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.koin.dsl.module
import org.koin.ktor.plugin.Koin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * Tests for the /health/validate endpoint.
 *
 * The [HealthCheckService] is overridden in Koin for each scenario so tests
 * never touch the real database or S3.
 */
class HealthRoutesTest {

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private fun okDependency() = DependencyStatus(status = "ok")
    private fun errorDependency(detail: String = "connection refused") = DependencyStatus(status = "error", detail = detail)

    private fun okStorage() = StorageStatus(
        status = "ok",
        read = okDependency(),
        write = okDependency(),
    )

    private fun storageWithFailedRead() = StorageStatus(
        status = "error",
        read = errorDependency("read timeout"),
        write = okDependency(),
    )

    private fun storageWithFailedWrite() = StorageStatus(
        status = "error",
        read = okDependency(),
        write = errorDependency("write denied"),
    )

    /** Builds a test application with a stubbed [HealthCheckService]. */
    private fun ApplicationTestBuilder.setupWithHealthService(service: HealthCheckService) {
        application {
            install(Koin) {
                modules(
                    module {
                        single<HealthCheckService> { service }
                    },
                )
            }
            install(ContentNegotiation) {
                json(apiJsonConfig())
            }
            healthModule()
        }
    }

    // -------------------------------------------------------------------------
    // Test cases
    // -------------------------------------------------------------------------

    @Test
    fun `validate returns 200 and status ok when both database and storage are healthy`() = testApplication {
        val stubService = object : HealthCheckService() {
            override fun checkDatabase() = okDependency()
            override fun checkStorage() = okStorage()
        }
        setupWithHealthService(stubService)

        val response = client.get("/health/validate")

        assertEquals(HttpStatusCode.OK, response.status)

        val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
        assertEquals("ok", body["status"]?.jsonPrimitive?.content)
    }

    @Test
    fun `validate returns 503 when database is unhealthy`() = testApplication {
        val stubService = object : HealthCheckService() {
            override fun checkDatabase() = errorDependency("db unreachable")
            override fun checkStorage() = okStorage()
        }
        setupWithHealthService(stubService)

        val response = client.get("/health/validate")

        assertEquals(HttpStatusCode.ServiceUnavailable, response.status)

        val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
        assertEquals("error", body["status"]?.jsonPrimitive?.content)
    }

    @Test
    fun `validate returns 503 when storage read is unhealthy`() = testApplication {
        val stubService = object : HealthCheckService() {
            override fun checkDatabase() = okDependency()
            override fun checkStorage() = storageWithFailedRead()
        }
        setupWithHealthService(stubService)

        val response = client.get("/health/validate")

        assertEquals(HttpStatusCode.ServiceUnavailable, response.status)

        val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
        assertEquals("error", body["status"]?.jsonPrimitive?.content)
    }

    @Test
    fun `validate returns 503 when storage write is unhealthy`() = testApplication {
        val stubService = object : HealthCheckService() {
            override fun checkDatabase() = okDependency()
            override fun checkStorage() = storageWithFailedWrite()
        }
        setupWithHealthService(stubService)

        val response = client.get("/health/validate")

        assertEquals(HttpStatusCode.ServiceUnavailable, response.status)

        val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
        assertEquals("error", body["status"]?.jsonPrimitive?.content)
    }

    @Test
    fun `validate response JSON includes separate storage read and write statuses when all ok`() = testApplication {
        val stubService = object : HealthCheckService() {
            override fun checkDatabase() = okDependency()
            override fun checkStorage() = okStorage()
        }
        setupWithHealthService(stubService)

        val response = client.get("/health/validate")

        assertEquals(HttpStatusCode.OK, response.status)

        val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
        val storage = body["storage"]?.jsonObject
        assertNotNull(storage, "Response must contain a 'storage' object")
        assertEquals("ok", storage["status"]?.jsonPrimitive?.content)

        val read = storage["read"]?.jsonObject
        assertNotNull(read, "storage must contain a 'read' object")
        assertEquals("ok", read["status"]?.jsonPrimitive?.content)

        val write = storage["write"]?.jsonObject
        assertNotNull(write, "storage must contain a 'write' object")
        assertEquals("ok", write["status"]?.jsonPrimitive?.content)
    }

    @Test
    fun `validate response JSON includes separate storage read and write statuses when write fails`() = testApplication {
        val stubService = object : HealthCheckService() {
            override fun checkDatabase() = okDependency()
            override fun checkStorage() = storageWithFailedWrite()
        }
        setupWithHealthService(stubService)

        val response = client.get("/health/validate")

        assertEquals(HttpStatusCode.ServiceUnavailable, response.status)

        val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
        val storage = body["storage"]?.jsonObject
        assertNotNull(storage)
        assertEquals("error", storage["status"]?.jsonPrimitive?.content)

        val read = storage["read"]?.jsonObject
        assertNotNull(read)
        assertEquals("ok", read["status"]?.jsonPrimitive?.content)

        val write = storage["write"]?.jsonObject
        assertNotNull(write)
        assertEquals("error", write["status"]?.jsonPrimitive?.content)
        assertEquals("write denied", write["detail"]?.jsonPrimitive?.content)
    }

    @Test
    fun `validate response JSON includes database status`() = testApplication {
        val stubService = object : HealthCheckService() {
            override fun checkDatabase() = okDependency()
            override fun checkStorage() = okStorage()
        }
        setupWithHealthService(stubService)

        val response = client.get("/health/validate")

        val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
        val database = body["database"]?.jsonObject
        assertNotNull(database, "Response must contain a 'database' object")
        assertEquals("ok", database["status"]?.jsonPrimitive?.content)
    }
}
