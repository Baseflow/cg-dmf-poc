// SPDX-License-Identifier: EUPL-1.2
// Copyright (C) 2026 Gemeente Utrecht
package com.baseflow.api.middleware

import io.ktor.http.ContentType
import io.ktor.http.content.EntityTagVersion
import io.ktor.http.content.OutgoingContent
import io.ktor.http.content.TextContent
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.ApplicationRequest
import io.ktor.server.response.ApplicationResponse
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * Pure unit tests for ApiConditionalHeadersProvider/jsonEtagVersionFor.
 * No Ktor testApplication, routing, or DB — only provider behavior.
 */
class ApiConditionalHeadersProviderUnitTest {

    // mock for ApplicationCall
    private fun mockCall(): ApplicationCall {
        val call = mockk<ApplicationCall>(relaxed = true)
        every { call.request } returns mockk<ApplicationRequest>(relaxed = true)
        every { call.response } returns mockk<ApplicationResponse>(relaxed = true)
        return call
    }

    private fun jsonContent(text: String): TextContent = TextContent(text, ContentType.Application.Json)

    private fun plainTextContent(text: String): TextContent = TextContent(text, ContentType.Text.Plain)

    @Test
    fun returnsEntityTagForJsonTextContent() = runBlocking {
        val content: OutgoingContent = jsonContent("{ \"x\":1}")
        val versions = ApiConditionalHeadersProvider(mockCall(), content)
        assertTrue(versions.isNotEmpty(), "Expected a version for JSON content")
        // Same body should result in identical tag
        val etag = assertIs<EntityTagVersion>(versions.first())
        val again = ApiConditionalHeadersProvider(mockCall(), content)
        val etag2 = assertIs<EntityTagVersion>(again.first())
        assertEquals(etag, etag2)
    }

    @Test
    fun returnsEmptyForNonJson() = runBlocking {
        val content: OutgoingContent = plainTextContent("hello")
        val versions = ApiConditionalHeadersProvider(mockCall(), content)
        assertTrue(versions.isEmpty(), "Expected no version for non-JSON content")
    }

    @Test
    fun differentBodiesYieldDifferentTags() = runBlocking {
        val a: OutgoingContent = jsonContent("{ \"x\":1 }")
        val b: OutgoingContent = jsonContent("{ \"x\":2 }")

        val etagA = assertIs<EntityTagVersion>(ApiConditionalHeadersProvider(mockCall(), a).first())
        val etagB = assertIs<EntityTagVersion>(ApiConditionalHeadersProvider(mockCall(), b).first())
        assertNotEquals(etagA, etagB)
    }

    @Test
    fun helperNullForNonJson() {
        val nonJson = plainTextContent("hi")
        val v = jsonEtagVersionFor(nonJson)
        assertEquals(null, v)
    }
}
