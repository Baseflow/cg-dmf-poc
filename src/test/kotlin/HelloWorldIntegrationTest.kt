// SPDX-License-Identifier: EUPL-1.2
// Copyright (C) 2025 Gemeente Utrecht
package com.baseflow

import io.ktor.server.testing.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import kotlin.test.Test
import kotlin.test.assertEquals

class HelloWorldIntegrationTest {
    @Test
    fun testRootEndpointReturnsHelloWorld() = testApplication {
        application {
            helloWorldModule()
        }
        val response = client.get("/")
        assertEquals(200, response.status.value)
        assertEquals("hello world", response.bodyAsText())
    }
}
