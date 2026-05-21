// SPDX-License-Identifier: EUPL-1.2
// Copyright (C) 2026 Gemeente Utrecht
package com.baseflow.services

import com.baseflow.api.models.IntegriteitAlgoritme
import java.io.InputStream
import java.security.DigestInputStream
import java.security.MessageDigest
import java.util.zip.CRC32
import java.util.zip.CheckedInputStream
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

class IntegrityCalculationService {

    companion object {

        // ── CRC-16/IBM (poly 0xA001, reflected) ─────────────────────────────
        private val CRC16_TABLE: IntArray = IntArray(256) { i ->
            var crc = i
            repeat(8) { crc = if (crc and 1 != 0) (crc ushr 1) xor 0xA001 else crc ushr 1 }
            crc
        }

        private fun crc16(data: ByteArray): Int {
            var crc = 0
            for (b in data) crc = (crc ushr 8) xor CRC16_TABLE[(crc xor b.toInt()) and 0xFF]
            return crc
        }

        // ── CRC-64/ECMA-182 (poly 0xC96C5795D7870F42) ───────────────────────
        private val CRC64_TABLE: LongArray = LongArray(256) { i ->
            var crc = i.toLong()
            repeat(8) { crc = if (crc and 1L != 0L) (crc ushr 1) xor -0x3693a86a2777a96bL else crc ushr 1 }
            crc
        }

        private fun crc64(data: ByteArray): Long {
            var crc = -1L
            for (b in data) crc = (crc ushr 8) xor CRC64_TABLE[((crc xor b.toLong()) and 0xFF).toInt()]
            return crc.inv()
        }

        // ── Fletcher checksums ───────────────────────────────────────────────
        private fun fletcher4(data: ByteArray): Int {
            var s1 = 0
            var s2 = 0
            for (b in data) {
                s1 = (s1 + (b.toInt() and 0x03)) % 15
                s2 = (s2 + s1) % 15
            }
            return (s2 shl 4) or s1
        }

        private fun fletcher8(data: ByteArray): Int {
            var s1 = 0
            var s2 = 0
            for (b in data) {
                s1 = (s1 + (b.toInt() and 0xFF)) % 255
                s2 = (s2 + s1) % 255
            }
            return (s2 shl 8) or s1
        }

        private fun fletcher16(data: ByteArray): Int {
            var s1 = 0
            var s2 = 0
            for (b in data) {
                s1 = (s1 + (b.toInt() and 0xFF)) % 65535
                s2 = (s2 + s1) % 65535
            }
            return (s2 shl 16) or s1
        }

        private fun fletcher32(data: ByteArray): Long {
            var s1 = 0L
            var s2 = 0L
            for (b in data) {
                s1 = (s1 + (b.toLong() and 0xFF)) % 4294967295L
                s2 = (s2 + s1) % 4294967295L
            }
            return (s2 shl 32) or s1
        }

        // ── HMAC-SHA256 with empty key (integrity without shared secret) ─────
        private fun hmacSha256(data: ByteArray): String {
            val mac = Mac.getInstance("HmacSHA256")
            mac.init(SecretKeySpec(ByteArray(0), "HmacSHA256"))
            return mac.doFinal(data).joinToString("") { "%02x".format(it) }
        }

        // ── Shared helpers ───────────────────────────────────────────────────
        private fun hashBytesWithMessageDigest(data: ByteArray, algo: IntegriteitAlgoritme): String {
            val javaAlgo = algo.toJavaMessageDigestName()
            val hash = MessageDigest.getInstance(javaAlgo).digest(data)
            return hash.joinToString("") { "%02x".format(it) }
        }

        private fun IntegriteitAlgoritme.toJavaMessageDigestName(): String = when (this) {
            IntegriteitAlgoritme.MD5 -> "MD5"
            IntegriteitAlgoritme.SHA_1 -> "SHA-1"
            IntegriteitAlgoritme.SHA_256 -> "SHA-256"
            else -> throw IllegalArgumentException("Not a MessageDigest algorithm: $this")
        }

        private fun computeHash(data: ByteArray, algo: IntegriteitAlgoritme): String = when (algo) {
            IntegriteitAlgoritme.CRC_16 -> crc16(data).toString(16)
            IntegriteitAlgoritme.CRC_32 -> {
                val crc32 = CRC32()
                crc32.update(data)
                crc32.value.toString(16)
            }
            IntegriteitAlgoritme.CRC_64 -> crc64(data).toULong().toString(16)
            IntegriteitAlgoritme.FLETCHER_4 -> fletcher4(data).toString(16)
            IntegriteitAlgoritme.FLETCHER_8 -> fletcher8(data).toString(16)
            IntegriteitAlgoritme.FLETCHER_16 -> fletcher16(data).toString(16)
            IntegriteitAlgoritme.FLETCHER_32 -> fletcher32(data).toString(16)
            IntegriteitAlgoritme.HMAC -> hmacSha256(data)
            IntegriteitAlgoritme.MD5,
            IntegriteitAlgoritme.SHA_1,
            IntegriteitAlgoritme.SHA_256,
            -> hashBytesWithMessageDigest(data, algo)
        }

        fun calculateIntegrity(data: ByteArray, algorithm: String?): IntegrityCalculationResult {
            if (algorithm.isNullOrEmpty()) return IntegrityCalculationResult("", "")
            val algo = IntegriteitAlgoritme.valueOf(algorithm)
            return IntegrityCalculationResult(computeHash(data, algo), algorithm)
        }

        /**
         * Wraps [stream] in the appropriate digest/checksum filter for [algorithm], invokes
         * [block] with the wrapped stream (e.g. to upload it), then returns the computed hash.
         *
         * The bytes are never accumulated in memory — the hash accumulates as a side-effect of
         * whatever [block] does with the stream (a single pass).
         *
         * When [algorithm] is null or blank [block] is called with the original [stream] and
         * empty strings are returned, so the upload still proceeds normally.
         *
         * Note: CRC_16, CRC_64, Fletcher variants, and HMAC do not have standard Java streaming
         * filter support. For those algorithms the stream bytes are accumulated and hashed after
         * [block] completes. All MessageDigest-based algorithms (MD5, SHA_1, SHA_256) and CRC_32
         * use true streaming filters.
         */
        fun <T> withIntegrity(stream: InputStream, algorithm: String?, block: (InputStream) -> T): Pair<T, IntegrityCalculationResult> {
            if (algorithm.isNullOrEmpty()) {
                return block(stream) to IntegrityCalculationResult("", "")
            }
            val algo = IntegriteitAlgoritme.valueOf(algorithm)
            return when (algo) {
                IntegriteitAlgoritme.CRC_32 -> {
                    val crc32 = CRC32()
                    val cis = CheckedInputStream(stream, crc32)
                    val result = block(cis)
                    result to IntegrityCalculationResult(crc32.value.toString(16), algorithm)
                }

                IntegriteitAlgoritme.MD5,
                IntegriteitAlgoritme.SHA_1,
                IntegriteitAlgoritme.SHA_256,
                -> {
                    val digest = MessageDigest.getInstance(algo.toJavaMessageDigestName())
                    val digestStream = DigestInputStream(stream, digest)
                    val result = block(digestStream)
                    val hash = digest.digest().joinToString("") { "%02x".format(it) }
                    result to IntegrityCalculationResult(hash, algorithm)
                }

                // No standard streaming filter — buffer and hash after block completes.
                IntegriteitAlgoritme.CRC_16,
                IntegriteitAlgoritme.CRC_64,
                IntegriteitAlgoritme.FLETCHER_4,
                IntegriteitAlgoritme.FLETCHER_8,
                IntegriteitAlgoritme.FLETCHER_16,
                IntegriteitAlgoritme.FLETCHER_32,
                IntegriteitAlgoritme.HMAC,
                -> {
                    val buffer = stream.readAllBytes()
                    val result = block(buffer.inputStream())
                    result to IntegrityCalculationResult(computeHash(buffer, algo), algorithm)
                }
            }
        }
    }
}

data class IntegrityCalculationResult(val hash: String, val algorithm: String)
