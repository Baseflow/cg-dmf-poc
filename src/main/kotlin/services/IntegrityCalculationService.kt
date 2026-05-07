// SPDX-License-Identifier: EUPL-1.2
// Copyright (C) 2026 Gemeente Utrecht
package com.baseflow.services

import com.baseflow.api.models.IntegriteitAlgoritme
import java.util.zip.CRC32

class IntegrityCalculationService {

    companion object {

        fun calculateIntegrity(data: ByteArray, algorithm: String?): IntegrityCalculationResult {
            if (algorithm.isNullOrEmpty()) {
                return IntegrityCalculationResult("", "")
            }

            val integriteitAlgoritme = IntegriteitAlgoritme.valueOf(algorithm)
            when (integriteitAlgoritme) {
                IntegriteitAlgoritme.CRC_32 -> {
                    val crc32 = CRC32()
                    crc32.update(data)
                    val hashValue = crc32.value
                    return IntegrityCalculationResult(hashValue.toString(16), algorithm)
                }

                IntegriteitAlgoritme.MD5 -> {
                    val digest = java.security.MessageDigest.getInstance("MD5")
                    val hashBytes = digest.digest(data)
                    return IntegrityCalculationResult(hashBytes.joinToString("") { "%02x".format(it) }, algorithm)
                }

                IntegriteitAlgoritme.SHA_1 -> {
                    val digest = java.security.MessageDigest.getInstance("SHA-1")
                    val hashBytes = digest.digest(data)
                    return IntegrityCalculationResult(hashBytes.joinToString("") { "%02x".format(it) }, algorithm)
                }

                IntegriteitAlgoritme.SHA_256 -> {
                    val digest = java.security.MessageDigest.getInstance("SHA-256")
                    val hashBytes = digest.digest(data)
                    return IntegrityCalculationResult(hashBytes.joinToString("") { "%02x".format(it) }, algorithm)
                }

                else -> {
                    return IntegrityCalculationResult("", "")
                }
            }
        }
    }
}

class IntegrityCalculationResult(val hash: String, val algorithm: String)
