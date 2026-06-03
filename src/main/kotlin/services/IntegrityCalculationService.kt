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
        // Definitions align with libscrc: the N in FLETCHER_N denotes the input word size.
        // FLETCHER_4  = libscrc.fletcher8  (4-bit nibble words, mod 16,       8-bit  output)
        // FLETCHER_8  = libscrc.fletcher16 (8-bit byte words,  mod 255,       16-bit output)
        // FLETCHER_16 = libscrc.fletcher32 (16-bit LE words,   mod 65535,     32-bit output, init 0xFFFF)
        // FLETCHER_32 = consistent extension (32-bit LE words, mod 2^32-1,    64-bit output)

        private fun fletcher4(data: ByteArray): Int {
            var s1 = 0
            var s2 = 0
            for (b in data) {
                s1 = (s1 + (b.toInt() and 0x0F)) % 16
                s2 = (s2 + s1) % 16
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
            var s1 = 0xFFFF
            var s2 = 0xFFFF
            var i = 0
            while (i + 1 < data.size) {
                val word = (data[i].toInt() and 0xFF) or ((data[i + 1].toInt() and 0xFF) shl 8)
                s1 = (s1 + word) % 65535
                s2 = (s2 + s1) % 65535
                i += 2
            }
            if (i < data.size) {
                s1 = (s1 + (data[i].toInt() and 0xFF)) % 65535
                s2 = (s2 + s1) % 65535
            }
            return (s2 shl 16) or s1
        }

        private fun fletcher32(data: ByteArray): Long {
            val mod = 4294967295L
            var s1 = 0L
            var s2 = 0L
            var i = 0
            while (i + 3 < data.size) {
                val word = (data[i].toLong() and 0xFF) or
                    ((data[i + 1].toLong() and 0xFF) shl 8) or
                    ((data[i + 2].toLong() and 0xFF) shl 16) or
                    ((data[i + 3].toLong() and 0xFF) shl 24)
                s1 = (s1 + word) % mod
                s2 = (s2 + s1) % mod
                i += 4
            }
            if (i < data.size) {
                var word = 0L
                for (j in 0 until data.size - i) word = word or ((data[i + j].toLong() and 0xFF) shl (8 * j))
                s1 = (s1 + word) % mod
                s2 = (s2 + s1) % mod
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

        // Used for FLETCHER_4 (mod=16, mask=0x0F) and FLETCHER_8 (mod=255, mask=0xFF).
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

        // Used for FLETCHER_16: 16-bit LE words, mod 65535, init 0xFFFF.
        // An odd trailing byte is fed as-is when result() is called.
        private class Fletcher16InputStream(inner: InputStream) : FilterInputStream(inner) {
            private var s1 = 0xFFFF
            private var s2 = 0xFFFF
            private var pending = -1 // low byte of an incomplete 16-bit word, or -1

            private fun feedWord(word: Int) {
                s1 = (s1 + word) % 65535
                s2 = (s2 + s1) % 65535
            }

            override fun read(): Int {
                val b = super.read()
                if (b < 0) return b
                if (pending < 0) {
                    pending = b and 0xFF
                } else {
                    feedWord(pending or ((b and 0xFF) shl 8))
                    pending = -1
                }
                return b
            }

            override fun read(buf: ByteArray, off: Int, len: Int): Int {
                val n = super.read(buf, off, len)
                if (n <= 0) return n
                var i = 0
                if (pending >= 0) {
                    feedWord(pending or ((buf[off].toInt() and 0xFF) shl 8))
                    pending = -1
                    i = 1
                }
                while (i + 1 < n) {
                    feedWord((buf[off + i].toInt() and 0xFF) or ((buf[off + i + 1].toInt() and 0xFF) shl 8))
                    i += 2
                }
                if (i < n) pending = buf[off + i].toInt() and 0xFF
                return n
            }

            fun result(): Int {
                if (pending >= 0) {
                    s1 = (s1 + pending) % 65535
                    s2 = (s2 + s1) % 65535
                    pending = -1
                }
                return (s2 shl 16) or s1
            }
        }

        // Used for FLETCHER_32: 32-bit LE words, mod 2^32-1, init 0.
        // 1–3 trailing bytes are fed as a partial LE word when result() is called.
        private class Fletcher32InputStream(inner: InputStream) : FilterInputStream(inner) {
            private val mod = 4294967295L
            private var s1 = 0L
            private var s2 = 0L
            private val buf = ByteArray(4)
            private var bufLen = 0

            private fun feedWord(word: Long) {
                s1 = (s1 + word) % mod
                s2 = (s2 + s1) % mod
            }

            override fun read(): Int {
                val b = super.read()
                if (b < 0) return b
                buf[bufLen++] = b.toByte()
                if (bufLen == 4) {
                    feedWord(
                        (buf[0].toLong() and 0xFF) or ((buf[1].toLong() and 0xFF) shl 8) or
                            ((buf[2].toLong() and 0xFF) shl 16) or ((buf[3].toLong() and 0xFF) shl 24),
                    )
                    bufLen = 0
                }
                return b
            }

            override fun read(data: ByteArray, off: Int, len: Int): Int {
                val n = super.read(data, off, len)
                if (n <= 0) return n
                var i = 0
                while (i < n) {
                    buf[bufLen++] = data[off + i++]
                    if (bufLen == 4) {
                        feedWord(
                            (buf[0].toLong() and 0xFF) or ((buf[1].toLong() and 0xFF) shl 8) or
                                ((buf[2].toLong() and 0xFF) shl 16) or ((buf[3].toLong() and 0xFF) shl 24),
                        )
                        bufLen = 0
                    }
                }
                return n
            }

            fun result(): Long {
                if (bufLen > 0) {
                    var word = 0L
                    for (j in 0 until bufLen) word = word or ((buf[j].toLong() and 0xFF) shl (8 * j))
                    feedWord(word)
                    bufLen = 0
                }
                return (s2 shl 32) or s1
            }
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
            IntegriteitAlgoritme.SHA_512 -> "SHA-512"
            IntegriteitAlgoritme.SHA_3 -> "SHA3-256"
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
            IntegriteitAlgoritme.SHA_512,
            IntegriteitAlgoritme.SHA_3,
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
                    val fis = FletcherInputStream(stream, mod = 16L, bits = 8, mask = 0x0F)
                    val result = block(fis)
                    result to IntegrityCalculationResult(fis.result().toString(16), algorithm)
                }

                IntegriteitAlgoritme.FLETCHER_8 -> {
                    val fis = FletcherInputStream(stream, mod = 255L, bits = 16)
                    val result = block(fis)
                    result to IntegrityCalculationResult(fis.result().toUInt().toString(16), algorithm)
                }

                IntegriteitAlgoritme.FLETCHER_16 -> {
                    val fis = Fletcher16InputStream(stream)
                    val result = block(fis)
                    result to IntegrityCalculationResult(fis.result().toUInt().toString(16), algorithm)
                }

                IntegriteitAlgoritme.FLETCHER_32 -> {
                    val fis = Fletcher32InputStream(stream)
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
                IntegriteitAlgoritme.SHA_512,
                IntegriteitAlgoritme.SHA_3,
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
