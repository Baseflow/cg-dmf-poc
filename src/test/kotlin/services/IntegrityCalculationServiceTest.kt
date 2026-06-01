// SPDX-License-Identifier: EUPL-1.2
// Copyright (C) 2026 Gemeente Utrecht
package com.baseflow.services

import com.baseflow.testutils.TestDataFactory
import java.io.OutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
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

    // ── withIntegrity — all remaining algorithms ───────────────────────────────

    @Test
    fun `withIntegrity CRC_16 - hash matches calculateIntegrity for sample bytes`() {
        val expected = IntegrityCalculationService.calculateIntegrity(sampleBytes, "CRC_16")
        val (_, actual) = IntegrityCalculationService.withIntegrity(sampleBytes.inputStream(), "CRC_16") {
            it.copyTo(OutputStream.nullOutputStream())
        }
        assertEquals(expected.hash, actual.hash)
        assertEquals("CRC_16", actual.algorithm)
    }

    @Test
    fun `withIntegrity CRC_16 - hash matches calculateIntegrity for binary bytes`() {
        val expected = IntegrityCalculationService.calculateIntegrity(binaryBytes, "CRC_16")
        val (_, actual) = IntegrityCalculationService.withIntegrity(binaryBytes.inputStream(), "CRC_16") {
            it.copyTo(OutputStream.nullOutputStream())
        }
        assertEquals(expected.hash, actual.hash)
    }

    @Test
    fun `withIntegrity CRC_16 - empty input produces stable zero-like result`() {
        val expected = IntegrityCalculationService.calculateIntegrity(emptyBytes, "CRC_16")
        val (_, actual) = IntegrityCalculationService.withIntegrity(emptyBytes.inputStream(), "CRC_16") {
            it.copyTo(OutputStream.nullOutputStream())
        }
        assertEquals(expected.hash, actual.hash)
    }

    @Test
    fun `calculateIntegrity CRC_16 - produces known checksum for single byte 0x01`() {
        // CRC-16/IBM of [0x01]: poly 0xA001, init 0
        // feed(0): crc = (0 ushr 8) xor TABLE[(0 xor 1) and 0xFF] = TABLE[1] = 0xC0C1
        val result = IntegrityCalculationService.calculateIntegrity(byteArrayOf(0x01), "CRC_16")
        assertEquals("c0c1", result.hash)
    }

    @Test
    fun `withIntegrity CRC_64 - hash matches calculateIntegrity for sample bytes`() {
        val expected = IntegrityCalculationService.calculateIntegrity(sampleBytes, "CRC_64")
        val (_, actual) = IntegrityCalculationService.withIntegrity(sampleBytes.inputStream(), "CRC_64") {
            it.copyTo(OutputStream.nullOutputStream())
        }
        assertEquals(expected.hash, actual.hash)
        assertEquals("CRC_64", actual.algorithm)
    }

    @Test
    fun `withIntegrity CRC_64 - hash matches calculateIntegrity for binary bytes`() {
        val expected = IntegrityCalculationService.calculateIntegrity(binaryBytes, "CRC_64")
        val (_, actual) = IntegrityCalculationService.withIntegrity(binaryBytes.inputStream(), "CRC_64") {
            it.copyTo(OutputStream.nullOutputStream())
        }
        assertEquals(expected.hash, actual.hash)
    }

    @Test
    fun `withIntegrity CRC_64 - empty input is stable`() {
        val expected = IntegrityCalculationService.calculateIntegrity(emptyBytes, "CRC_64")
        val (_, actual) = IntegrityCalculationService.withIntegrity(emptyBytes.inputStream(), "CRC_64") {
            it.copyTo(OutputStream.nullOutputStream())
        }
        assertEquals(expected.hash, actual.hash)
    }

    @Test
    fun `withIntegrity FLETCHER_4 - hash matches calculateIntegrity for sample bytes`() {
        val expected = IntegrityCalculationService.calculateIntegrity(sampleBytes, "FLETCHER_4")
        val (_, actual) = IntegrityCalculationService.withIntegrity(sampleBytes.inputStream(), "FLETCHER_4") {
            it.copyTo(OutputStream.nullOutputStream())
        }
        assertEquals(expected.hash, actual.hash)
        assertEquals("FLETCHER_4", actual.algorithm)
    }

    @Test
    fun `withIntegrity FLETCHER_4 - hash matches calculateIntegrity for binary bytes`() {
        val expected = IntegrityCalculationService.calculateIntegrity(binaryBytes, "FLETCHER_4")
        val (_, actual) = IntegrityCalculationService.withIntegrity(binaryBytes.inputStream(), "FLETCHER_4") {
            it.copyTo(OutputStream.nullOutputStream())
        }
        assertEquals(expected.hash, actual.hash)
    }

    @Test
    fun `withIntegrity FLETCHER_8 - hash matches calculateIntegrity for sample bytes`() {
        val expected = IntegrityCalculationService.calculateIntegrity(sampleBytes, "FLETCHER_8")
        val (_, actual) = IntegrityCalculationService.withIntegrity(sampleBytes.inputStream(), "FLETCHER_8") {
            it.copyTo(OutputStream.nullOutputStream())
        }
        assertEquals(expected.hash, actual.hash)
        assertEquals("FLETCHER_8", actual.algorithm)
    }

    @Test
    fun `withIntegrity FLETCHER_8 - hash matches calculateIntegrity for binary bytes`() {
        val expected = IntegrityCalculationService.calculateIntegrity(binaryBytes, "FLETCHER_8")
        val (_, actual) = IntegrityCalculationService.withIntegrity(binaryBytes.inputStream(), "FLETCHER_8") {
            it.copyTo(OutputStream.nullOutputStream())
        }
        assertEquals(expected.hash, actual.hash)
    }

    @Test
    fun `withIntegrity FLETCHER_16 - hash matches calculateIntegrity for sample bytes`() {
        val expected = IntegrityCalculationService.calculateIntegrity(sampleBytes, "FLETCHER_16")
        val (_, actual) = IntegrityCalculationService.withIntegrity(sampleBytes.inputStream(), "FLETCHER_16") {
            it.copyTo(OutputStream.nullOutputStream())
        }
        assertEquals(expected.hash, actual.hash)
        assertEquals("FLETCHER_16", actual.algorithm)
    }

    @Test
    fun `withIntegrity FLETCHER_16 - hash matches calculateIntegrity for binary bytes`() {
        val expected = IntegrityCalculationService.calculateIntegrity(binaryBytes, "FLETCHER_16")
        val (_, actual) = IntegrityCalculationService.withIntegrity(binaryBytes.inputStream(), "FLETCHER_16") {
            it.copyTo(OutputStream.nullOutputStream())
        }
        assertEquals(expected.hash, actual.hash)
    }

    @Test
    fun `withIntegrity FLETCHER_32 - hash matches calculateIntegrity for sample bytes`() {
        val expected = IntegrityCalculationService.calculateIntegrity(sampleBytes, "FLETCHER_32")
        val (_, actual) = IntegrityCalculationService.withIntegrity(sampleBytes.inputStream(), "FLETCHER_32") {
            it.copyTo(OutputStream.nullOutputStream())
        }
        assertEquals(expected.hash, actual.hash)
        assertEquals("FLETCHER_32", actual.algorithm)
    }

    @Test
    fun `withIntegrity FLETCHER_32 - hash matches calculateIntegrity for binary bytes`() {
        val expected = IntegrityCalculationService.calculateIntegrity(binaryBytes, "FLETCHER_32")
        val (_, actual) = IntegrityCalculationService.withIntegrity(binaryBytes.inputStream(), "FLETCHER_32") {
            it.copyTo(OutputStream.nullOutputStream())
        }
        assertEquals(expected.hash, actual.hash)
    }

    @Test
    fun `calculateIntegrity HMAC - throws UnsupportedOperationException`() {
        assertFailsWith<UnsupportedOperationException> {
            IntegrityCalculationService.calculateIntegrity(sampleBytes, "HMAC")
        }
    }

    @Test
    fun `withIntegrity HMAC - throws UnsupportedOperationException`() {
        assertFailsWith<UnsupportedOperationException> {
            IntegrityCalculationService.withIntegrity(sampleBytes.inputStream(), "HMAC") {
                it.copyTo(OutputStream.nullOutputStream())
            }
        }
    }

    @Test
    fun `withIntegrity - different inputs produce different hashes for every algorithm`() {
        val input1 = "hello".toByteArray()
        val input2 = "world".toByteArray()
        val algorithms = listOf(
            "CRC_16",
            "CRC_32",
            "CRC_64",
            "FLETCHER_4",
            "FLETCHER_8",
            "FLETCHER_16",
            "FLETCHER_32",
            "MD5",
            "SHA_1",
            "SHA_256",
        )

        for (algo in algorithms) {
            val (_, r1) = IntegrityCalculationService.withIntegrity(
                input1.inputStream(),
                algo,
            ) { it.copyTo(OutputStream.nullOutputStream()) }
            val (_, r2) = IntegrityCalculationService.withIntegrity(
                input2.inputStream(),
                algo,
            ) { it.copyTo(OutputStream.nullOutputStream()) }
            assertTrue(
                r1.hash != r2.hash,
                "Expected different hashes for algo $algo but got '${r1.hash}' for both inputs",
            )
        }
    }

    // ── Reference-vector tests (values from scripts/generate_test_vectors.py) ──
    // Input: "The quick brown fox jumps over the lazy dog"

    @Test
    fun `calculateIntegrity CRC_16 - produces known checksum for quick fox`() {
        assertEquals("fcdf", IntegrityCalculationService.calculateIntegrity(sampleBytes, "CRC_16").hash)
    }

    @Test
    fun `calculateIntegrity CRC_32 - produces known checksum for quick fox`() {
        assertEquals("414fa339", IntegrityCalculationService.calculateIntegrity(sampleBytes, "CRC_32").hash)
    }

    @Test
    fun `calculateIntegrity CRC_64 - produces known checksum for quick fox`() {
        assertEquals("efc9898bcbddbd7b", IntegrityCalculationService.calculateIntegrity(sampleBytes, "CRC_64").hash)
    }

    @Test
    fun `calculateIntegrity FLETCHER_4 - produces known checksum for quick fox`() {
        // libscrc.fletcher8(b"The quick brown fox jumps over the lazy dog")
        assertEquals("29", IntegrityCalculationService.calculateIntegrity(sampleBytes, "FLETCHER_4").hash)
    }

    @Test
    fun `calculateIntegrity FLETCHER_8 - produces known checksum for quick fox`() {
        // libscrc.fletcher16 = Wikipedia Fletcher-16: libscrc.fletcher16(b"The quick brown fox...")
        assertEquals("fee8", IntegrityCalculationService.calculateIntegrity(sampleBytes, "FLETCHER_8").hash)
    }

    @Test
    fun `calculateIntegrity FLETCHER_8 - produces Wikipedia reference value for abcde`() {
        // Wikipedia explicit example: Fletcher-16("abcde") = 0xC8F0
        assertEquals("c8f0", IntegrityCalculationService.calculateIntegrity("abcde".toByteArray(), "FLETCHER_8").hash)
    }

    @Test
    fun `calculateIntegrity FLETCHER_16 - produces known checksum for quick fox`() {
        // libscrc.fletcher32 = Wikipedia Fletcher-32: libscrc.fletcher32(b"The quick brown fox...")
        assertEquals("53cd5b8d", IntegrityCalculationService.calculateIntegrity(sampleBytes, "FLETCHER_16").hash)
    }

    @Test
    fun `calculateIntegrity FLETCHER_16 - produces Wikipedia reference value for abcde`() {
        // Canonical reference: Fletcher-32("abcde") = 0xF04FC729
        assertEquals("f04fc729", IntegrityCalculationService.calculateIntegrity("abcde".toByteArray(), "FLETCHER_16").hash)
    }

    @Test
    fun `calculateIntegrity FLETCHER_32 - produces known checksum for quick fox`() {
        // 32-bit LE word extension of libscrc pattern (no independent library)
        assertEquals("7ba5bdcb1f163c77", IntegrityCalculationService.calculateIntegrity(sampleBytes, "FLETCHER_32").hash)
    }

    @Test
    fun `calculateIntegrity MD5 - produces known hash for quick fox`() {
        assertEquals("9e107d9d372bb6826bd81d3542a419d6", IntegrityCalculationService.calculateIntegrity(sampleBytes, "MD5").hash)
    }

    @Test
    fun `calculateIntegrity SHA_1 - produces known hash for quick fox`() {
        assertEquals("2fd4e1c67a2d28fced849ee1bb76e7391b93eb12", IntegrityCalculationService.calculateIntegrity(sampleBytes, "SHA_1").hash)
    }

    @Test
    fun `calculateIntegrity SHA_256 - produces known hash for quick fox`() {
        assertEquals(
            "d7a8fbb307d7809469ca9abcb0082e4f8d5651e46d3cdb762d02d0bf37c9e592",
            IntegrityCalculationService.calculateIntegrity(sampleBytes, "SHA_256").hash,
        )
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
