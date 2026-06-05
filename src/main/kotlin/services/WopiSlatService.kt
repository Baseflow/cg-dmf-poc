// SPDX-License-Identifier: EUPL-1.2
// Copyright (C) 2026 Gemeente Utrecht
package com.baseflow.services

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.Instant
import java.util.Base64
import java.util.UUID
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Issues and validates self-contained, HMAC-SHA256 signed WOPI Short-Lived Access Tokens (SLATs).
 *
 * Token format (on the wire): `<base64url(payload)>.<base64url(signature)>`
 * - `payload`   — JSON object: `{ "fileId": "<uuid>", "expiresAt": <epochSeconds>, "userId": "<issuer>" }`
 * - `fileId`    — UUID of the EnkelvoudigInformatieObject
 * - `expiresAt` — Unix epoch seconds (long) when the token expires
 * - `userId`    — Identifier of the authenticated user/application that requested the token
 * - `signature` — HMAC-SHA256 of `payload` using [secret]
 *
 * No database storage is required; the token is fully self-contained and
 * verified by re-computing the HMAC on each request.
 */
class WopiSlatService(
    private val secret: String,
    /** Token lifetime in seconds. Defaults to 1 hour. */
    private val ttlSeconds: Long = 3600L,
) {

    private val algorithm = "HmacSHA256"
    private val encoder = Base64.getUrlEncoder().withoutPadding()
    private val decoder = Base64.getUrlDecoder()
    private val json = Json { ignoreUnknownKeys = false }

    @Serializable
    private data class SlatPayload(val fileId: String, val expiresAt: Long, val userId: String)

    /**
     * Issues a new SLAT for [fileId] and [userId].
     *
     * @return A pair of (token string, expiry as Unix epoch seconds).
     */
    fun issue(fileId: UUID, userId: String): Pair<String, Long> {
        val expiresAt = Instant.now().epochSecond + ttlSeconds
        val payload = json.encodeToString(
            SlatPayload.serializer(),
            SlatPayload(fileId = fileId.toString(), expiresAt = expiresAt, userId = userId),
        )
        val sig = sign(payload)
        val token = "${encoder.encodeToString(payload.toByteArray())}.${encoder.encodeToString(sig)}"
        return token to expiresAt
    }

    /**
     * Validates a SLAT and extracts the [UUID] of the file it grants access to.
     *
     * @return The file UUID if the token is valid and not expired, `null` otherwise.
     */
    fun validate(token: String): UUID? {
        return try {
            val lastDot = token.lastIndexOf('.')
            if (lastDot < 0) return null

            val payloadEncoded = token.substring(0, lastDot)
            val sigEncoded = token.substring(lastDot + 1)

            val payloadBytes = decoder.decode(payloadEncoded)
            val payload = String(payloadBytes, Charsets.UTF_8)
            val providedSig = decoder.decode(sigEncoded)

            // Constant-time comparison to prevent timing attacks
            val expectedSig = sign(payload)
            if (!constantTimeEquals(expectedSig, providedSig)) return null

            val decodedPayload = json.decodeFromString(SlatPayload.serializer(), payload)
            if (decodedPayload.userId.isBlank()) return null
            if (Instant.now().epochSecond > decodedPayload.expiresAt) return null

            UUID.fromString(decodedPayload.fileId)
        } catch (_: Exception) {
            null
        }
    }

    private fun sign(data: String): ByteArray {
        val mac = Mac.getInstance(algorithm)
        mac.init(SecretKeySpec(secret.toByteArray(Charsets.UTF_8), algorithm))
        return mac.doFinal(data.toByteArray(Charsets.UTF_8))
    }

    private fun constantTimeEquals(a: ByteArray, b: ByteArray): Boolean {
        if (a.size != b.size) return false
        var result = 0
        for (i in a.indices) {
            result = result or (a[i].toInt() xor b[i].toInt())
        }
        return result == 0
    }
}
