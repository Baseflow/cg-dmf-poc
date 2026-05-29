// SPDX-License-Identifier: EUPL-1.2
// Copyright (C) 2026 Gemeente Utrecht
package com.baseflow.services

import com.baseflow.api.models.IntegriteitAlgoritme
import java.io.InputStream
import java.security.DigestInputStream
import java.security.MessageDigest
import java.util.zip.CRC32
import java.util.zip.CheckedInputStream

class IntegrityCalculationService {

    companion object {

        fun calculateIntegrity(data: ByteArray, algorithm: String?): IntegrityCalculationResult {
            if (algorithm.isNullOrEmpty()) return IntegrityCalculationResult("", "")
            val integriteitAlgoritme = IntegriteitAlgoritme.valueOf(algorithm)
            return when (integriteitAlgoritme) {
                IntegriteitAlgoritme.CRC_32 -> {
                    val crc32 = CRC32()
                    crc32.update(data)
                    IntegrityCalculationResult(crc32.value.toString(16), algorithm)
                }

                IntegriteitAlgoritme.MD5,
                IntegriteitAlgoritme.SHA_1,
                IntegriteitAlgoritme.SHA_256,
                -> {
                    val javaAlgo = when (integriteitAlgoritme) {
                        IntegriteitAlgoritme.MD5 -> "MD5"
                        IntegriteitAlgoritme.SHA_1 -> "SHA-1"
                        else -> "SHA-256"
                    }
                    val hash = MessageDigest.getInstance(javaAlgo).digest(data)
                        .joinToString("") { "%02x".format(it) }
                    IntegrityCalculationResult(hash, algorithm)
                }

                else -> IntegrityCalculationResult("", "")
            }
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
         */
        fun <T> withIntegrity(stream: InputStream, algorithm: String?, block: (InputStream) -> T): Pair<T, IntegrityCalculationResult> {
            if (algorithm.isNullOrEmpty()) {
                return block(stream) to IntegrityCalculationResult("", "")
            }
            val integriteitAlgoritme = IntegriteitAlgoritme.valueOf(algorithm)
            return when (integriteitAlgoritme) {
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
                    val javaAlgo = when (integriteitAlgoritme) {
                        IntegriteitAlgoritme.MD5 -> "MD5"
                        IntegriteitAlgoritme.SHA_1 -> "SHA-1"
                        else -> "SHA-256"
                    }
                    val digest = MessageDigest.getInstance(javaAlgo)
                    val digestStream = DigestInputStream(stream, digest)
                    val result = block(digestStream)
                    val hash = digest.digest().joinToString("") { "%02x".format(it) }
                    result to IntegrityCalculationResult(hash, algorithm)
                }

                else -> block(stream) to IntegrityCalculationResult("", "")
            }
        }
    }
}

data class IntegrityCalculationResult(val hash: String, val algorithm: String)
