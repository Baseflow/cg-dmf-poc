// SPDX-License-Identifier: EUPL-1.2
// Copyright (C) 2026 Gemeente Utrecht
package com.baseflow.services

import com.baseflow.api.models.IntegriteitAlgoritme
import java.io.FilterInputStream
import java.io.InputStream
import java.security.DigestInputStream
import java.security.MessageDigest
import java.util.zip.CRC32
import java.util.zip.CheckedInputStream

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

        // ── CRC-64/ECMA-182 (reflected poly 0xC96C5795D7870F42, init 0) ──────────────
        private const val CRC64_ECMA_POLY: Long = -0x3693A86A2878F0BEL

        private val CRC64_TABLE: LongArray = LongArray(256) { i ->
            var crc = i.toLong()
            repeat(8) {
                crc = if ((crc and 1L) != 0L) (crc ushr 1) xor CRC64_ECMA_POLY else crc ushr 1
            }
            crc
        }

        private fun crc64(data: ByteArray): Long {
            var crc = 0L
            for (b in data) {
                val idx = ((crc xor (b.toLong() and 0xFF)) and 0xFF).toInt()
                crc = (crc ushr 8) xor CRC64_TABLE[idx]
            }
            return crc
        }

        // ── Fletcher checksums ───────────────────────────────────────────────
        private fun fletcher4(data: ByteArray): Int {
            var s1 = 0
            var s2 = 0
            for (b in data) {
                s1 = (s1 + (b.toInt() and 0x0F)) % 15
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

        // ── Streaming filter wrappers ─────────────────────────────────────────

        private class Crc16InputStream(inner: InputStream) : FilterInputStream(inner) {
            var crc = 0
                private set

            private fun feed(b: Int) {
                if (b >= 0) crc = (crc ushr 8) xor CRC16_TABLE[(crc xor b) and 0xFF]
            }

            override fun read(): Int = super.read().also { feed(it) }
            override fun read(b: ByteArray, off: Int, len: Int): Int = super.read(b, off, len).also { n ->
                if (n > 0) {
                    for (i in off until off + n) crc = (crc ushr 8) xor CRC16_TABLE[(crc xor (b[i].toInt() and 0xFF)) and 0xFF]
                }
            }
        }

        private class Crc64InputStream(inner: InputStream) : FilterInputStream(inner) {
            var crc = 0L
                private set

            override fun read(): Int = super.read().also { b ->
                if (b >= 0) {
                    val idx = ((crc xor (b.toLong() and 0xFF)) and 0xFF).toInt()
                    crc = (crc ushr 8) xor CRC64_TABLE[idx]
                }
            }

            override fun read(b: ByteArray, off: Int, len: Int): Int = super.read(b, off, len).also { n ->
                if (n > 0) {
                    for (i in off until off + n) {
                        val idx = ((crc xor (b[i].toLong() and 0xFF)) and 0xFF).toInt()
                        crc = (crc ushr 8) xor CRC64_TABLE[idx]
                    }
                }
            }
        }

        private class FletcherInputStream(inner: InputStream, private val mod: Long, private val bits: Int, private val mask: Int = 0xFF) :
            FilterInputStream(inner) {
            var s1 = 0L
                private set
            var s2 = 0L
                private set

            private fun feed(b: Int) {
                s1 = (s1 + (b.toLong() and mask.toLong())) % mod
                s2 = (s2 + s1) % mod
            }

            override fun read(): Int = super.read().also { if (it >= 0) feed(it) }
            override fun read(b: ByteArray, off: Int, len: Int): Int =
                super.read(b, off, len).also { n -> if (n > 0) for (i in off until off + n) feed(b[i].toInt()) }

            fun result(): Long = (s2 shl (bits / 2)) or s1
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
            IntegriteitAlgoritme.FLETCHER_16 -> fletcher16(data).toUInt().toString(16)
            IntegriteitAlgoritme.FLETCHER_32 -> fletcher32(data).toULong().toString(16)
            // HMAC requires a shared key. The Documenten API accepts HMAC as an algorithm choice
            // but does not define key exchange or verification — callers must compute HMAC
            // externally and supply the resulting value directly as integriteit_waarde.
            IntegriteitAlgoritme.HMAC -> throw UnsupportedOperationException(
                "HMAC requires a shared secret key and cannot be computed by this service. " +
                    "Compute the HMAC externally and supply the value as integriteit_waarde.",
            )
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
         * The hash accumulates as a side-effect of whatever [block] does with the stream —
         * bytes are never buffered in memory; all algorithms use true streaming filters.
         *
         * When [algorithm] is null or blank [block] is called with the original [stream] and
         * empty strings are returned, so the upload still proceeds normally.
         */
        fun <T> withIntegrity(stream: InputStream, algorithm: String?, block: (InputStream) -> T): Pair<T, IntegrityCalculationResult> {
            if (algorithm.isNullOrEmpty()) {
                return block(stream) to IntegrityCalculationResult("", "")
            }
            val algo = IntegriteitAlgoritme.valueOf(algorithm)
            return when (algo) {
                IntegriteitAlgoritme.CRC_16 -> {
                    val cis = Crc16InputStream(stream)
                    val result = block(cis)
                    result to IntegrityCalculationResult(cis.crc.toString(16), algorithm)
                }

                IntegriteitAlgoritme.CRC_32 -> {
                    val crc32 = CRC32()
                    val cis = CheckedInputStream(stream, crc32)
                    val result = block(cis)
                    result to IntegrityCalculationResult(crc32.value.toString(16), algorithm)
                }

                IntegriteitAlgoritme.CRC_64 -> {
                    val cis = Crc64InputStream(stream)
                    val result = block(cis)
                    result to IntegrityCalculationResult(cis.crc.toULong().toString(16), algorithm)
                }

                IntegriteitAlgoritme.FLETCHER_4 -> {
                    val fis = FletcherInputStream(stream, mod = 15L, bits = 8, mask = 0x0F)
                    val result = block(fis)
                    result to IntegrityCalculationResult(fis.result().toString(16), algorithm)
                }

                IntegriteitAlgoritme.FLETCHER_8 -> {
                    val fis = FletcherInputStream(stream, mod = 255L, bits = 16)
                    val result = block(fis)
                    result to IntegrityCalculationResult(fis.result().toUInt().toString(16), algorithm)
                }

                IntegriteitAlgoritme.FLETCHER_16 -> {
                    val fis = FletcherInputStream(stream, mod = 65535L, bits = 32)
                    val result = block(fis)
                    result to IntegrityCalculationResult(fis.result().toUInt().toString(16), algorithm)
                }

                IntegriteitAlgoritme.FLETCHER_32 -> {
                    val fis = FletcherInputStream(stream, mod = 4294967295L, bits = 64)
                    val result = block(fis)
                    result to IntegrityCalculationResult(fis.result().toULong().toString(16), algorithm)
                }

                IntegriteitAlgoritme.HMAC -> throw UnsupportedOperationException(
                    "HMAC requires a shared secret key and cannot be computed by this service. " +
                        "Compute the HMAC externally and supply the value as integriteit_waarde.",
                )

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
            }
        }
    }
}

data class IntegrityCalculationResult(val hash: String, val algorithm: String)
