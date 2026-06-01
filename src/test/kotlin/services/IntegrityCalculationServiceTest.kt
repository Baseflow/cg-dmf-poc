// SPDX-License-Identifier: EUPL-1.2
// Copyright (C) 2026 Gemeente Utrecht
package com.baseflow.services

import com.baseflow.testutils.TestDataFactory
import java.io.OutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class IntegrityCalculationServiceTest {

    // ── Shared fixtures ───────────────────────────────────────────────────────

    private val sampleBytes = "The quick brown fox jumps over the lazy dog".toByteArray()
    private val emptyBytes = ByteArray(0)
    private val binaryBytes = ByteArray(256) { it.toByte() }

    // ── calculateIntegrity (ByteArray) ────────────────────────────────────────

    @Test
    fun `calculateIntegrity SHA_256 - returns empty result for blank algorithm`() {
        val result = IntegrityCalculationService.calculateIntegrity(sampleBytes, null)
        assertEquals("", result.hash)
        assertEquals("", result.algorithm)
    }

    @Test
    fun `calculateIntegrity SHA_256 - returns empty result for empty algorithm string`() {
        val result = IntegrityCalculationService.calculateIntegrity(sampleBytes, "")
        assertEquals("", result.hash)
        assertEquals("", result.algorithm)
    }

    @Test
    fun `calculateIntegrity SHA_256 - produces known hash for sample input`() {
        val result = IntegrityCalculationService.calculateIntegrity(sampleBytes, "SHA_256")
        // Well-known SHA-256 of "The quick brown fox jumps over the lazy dog"
        assertEquals(
            "d7a8fbb307d7809469ca9abcb0082e4f8d5651e46d3cdb762d02d0bf37c9e592",
            result.hash,
        )
        assertEquals("SHA_256", result.algorithm)
    }

    @Test
    fun `calculateIntegrity SHA_256 - empty bytes produce known hash`() {
        val result = IntegrityCalculationService.calculateIntegrity(emptyBytes, "SHA_256")
        // SHA-256 of empty input
        assertEquals(
            "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
            result.hash,
        )
    }

    @Test
    fun `calculateIntegrity MD5 - produces known hash`() {
        val result = IntegrityCalculationService.calculateIntegrity(sampleBytes, "MD5")
        assertEquals("9e107d9d372bb6826bd81d3542a419d6", result.hash)
        assertEquals("MD5", result.algorithm)
    }

    @Test
    fun `calculateIntegrity SHA_1 - produces known hash`() {
        val result = IntegrityCalculationService.calculateIntegrity(sampleBytes, "SHA_1")
        assertEquals("2fd4e1c67a2d28fced849ee1bb76e7391b93eb12", result.hash)
        assertEquals("SHA_1", result.algorithm)
    }

    @Test
    fun `calculateIntegrity CRC_32 - produces known checksum`() {
        val result = IntegrityCalculationService.calculateIntegrity(sampleBytes, "CRC_32")
        // CRC-32 of "The quick brown fox jumps over the lazy dog" = 0x414FA339
        assertEquals("414fa339", result.hash)
        assertEquals("CRC_32", result.algorithm)
    }

    // ── withIntegrity — hash equality with calculateIntegrity ─────────────────

    @Test
    fun `withIntegrity SHA_256 - hash matches calculateIntegrity for sample bytes`() {
        val expected = IntegrityCalculationService.calculateIntegrity(sampleBytes, "SHA_256")

        val (_, actual) = IntegrityCalculationService.withIntegrity(
            sampleBytes.inputStream(),
            "SHA_256",
        ) { stream -> stream.copyTo(OutputStream.nullOutputStream()) }

        assertEquals(expected.hash, actual.hash)
        assertEquals(expected.algorithm, actual.algorithm)
    }

    @Test
    fun `withIntegrity SHA_256 - hash matches calculateIntegrity for empty bytes`() {
        val expected = IntegrityCalculationService.calculateIntegrity(emptyBytes, "SHA_256")

        val (_, actual) = IntegrityCalculationService.withIntegrity(
            emptyBytes.inputStream(),
            "SHA_256",
        ) { stream -> stream.copyTo(OutputStream.nullOutputStream()) }

        assertEquals(expected.hash, actual.hash)
    }

    @Test
    fun `withIntegrity SHA_256 - hash matches calculateIntegrity for binary bytes`() {
        val expected = IntegrityCalculationService.calculateIntegrity(binaryBytes, "SHA_256")

        val (_, actual) = IntegrityCalculationService.withIntegrity(
            binaryBytes.inputStream(),
            "SHA_256",
        ) { stream -> stream.copyTo(OutputStream.nullOutputStream()) }

        assertEquals(expected.hash, actual.hash)
    }

    @Test
    fun `withIntegrity MD5 - hash matches calculateIntegrity`() {
        val expected = IntegrityCalculationService.calculateIntegrity(sampleBytes, "MD5")

        val (_, actual) = IntegrityCalculationService.withIntegrity(
            sampleBytes.inputStream(),
            "MD5",
        ) { stream -> stream.copyTo(OutputStream.nullOutputStream()) }

        assertEquals(expected.hash, actual.hash)
        assertEquals(expected.algorithm, actual.algorithm)
    }

    @Test
    fun `withIntegrity SHA_1 - hash matches calculateIntegrity`() {
        val expected = IntegrityCalculationService.calculateIntegrity(sampleBytes, "SHA_1")

        val (_, actual) = IntegrityCalculationService.withIntegrity(
            sampleBytes.inputStream(),
            "SHA_1",
        ) { stream -> stream.copyTo(OutputStream.nullOutputStream()) }

        assertEquals(expected.hash, actual.hash)
    }

    @Test
    fun `withIntegrity CRC_32 - hash matches calculateIntegrity`() {
        val expected = IntegrityCalculationService.calculateIntegrity(sampleBytes, "CRC_32")

        val (_, actual) = IntegrityCalculationService.withIntegrity(
            sampleBytes.inputStream(),
            "CRC_32",
        ) { stream -> stream.copyTo(OutputStream.nullOutputStream()) }

        assertEquals(expected.hash, actual.hash)
    }

    @Test
    fun `withIntegrity - returns empty result for blank algorithm without reading stream`() {
        var streamRead = false
        val (_, result) = IntegrityCalculationService.withIntegrity(
            sampleBytes.inputStream(),
            null,
        ) { stream ->
            streamRead = true
            stream.copyTo(OutputStream.nullOutputStream())
        }

        assertEquals("", result.hash)
        assertEquals("", result.algorithm)
        // block is still invoked so the upload proceeds; only hash is empty
        assertTrue(streamRead)
    }

    @Test
    fun `withIntegrity - block return value is propagated`() {
        val (blockResult, _) = IntegrityCalculationService.withIntegrity(
            sampleBytes.inputStream(),
            "SHA_256",
        ) { stream ->
            stream.copyTo(OutputStream.nullOutputStream())
            42 // arbitrary sentinel
        }

        assertEquals(42, blockResult)
    }

    @Test
    fun `withIntegrity - different inputs produce different hashes`() {
        val input1 = "hello".toByteArray()
        val input2 = "world".toByteArray()

        val (_, result1) = IntegrityCalculationService.withIntegrity(input1.inputStream(), "SHA_256") {
            it.copyTo(OutputStream.nullOutputStream())
        }
        val (_, result2) = IntegrityCalculationService.withIntegrity(input2.inputStream(), "SHA_256") {
            it.copyTo(OutputStream.nullOutputStream())
        }

        assertTrue(result1.hash != result2.hash)
    }

    @Test
    fun `withIntegrity - compare pdf content`() {
        val input = TestDataFactory.PDF_CONTENT.toByteArray()

        val (_, streamResult) = IntegrityCalculationService.withIntegrity(input.inputStream(), "SHA_256") {
            it.copyTo(OutputStream.nullOutputStream())
        }
        val result = IntegrityCalculationService.calculateIntegrity(input, "SHA_256")

        assertEquals("c97b22d2edb8996c7f6ab22921e9fa90787235978b7641d4513449a1ca2e99be", streamResult.hash)
        assertTrue(streamResult.hash == result.hash)
    }
}
