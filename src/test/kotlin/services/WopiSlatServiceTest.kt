// SPDX-License-Identifier: EUPL-1.2
// Copyright (C) 2026 Gemeente Utrecht
package com.baseflow.services

import java.time.Instant
import java.util.Base64
import java.util.UUID
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class WopiSlatServiceTest {

    private val secret = "test-slat-secret-32-chars-minimum!"
    private val service = WopiSlatService(secret = secret, ttlSeconds = 3600L)

    // ── Roundtrip ─────────────────────────────────────────────────────────────

    @Test
    fun `issue and validate returns the correct file UUID`() {
        val fileId = UUID.randomUUID()
        val (token, _) = service.issue(fileId)
        val result = service.validate(token)
        assertEquals(fileId, result)
    }

    @Test
    fun `issued token TTL is approximately now plus ttlSeconds`() {
        val before = Instant.now().epochSecond
        val (_, ttl) = service.issue(UUID.randomUUID())
        val after = Instant.now().epochSecond
        assert(ttl in (before + 3600)..(after + 3600)) {
            "Expected TTL between ${before + 3600} and ${after + 3600}, got $ttl"
        }
    }

    @Test
    fun `validate returns same UUID for multiple consecutive calls`() {
        val fileId = UUID.randomUUID()
        val (token, _) = service.issue(fileId)
        repeat(3) {
            assertEquals(fileId, service.validate(token))
        }
    }

    @Test
    fun `tokens issued for different UUIDs validate to their respective UUIDs`() {
        val id1 = UUID.randomUUID()
        val id2 = UUID.randomUUID()
        val (token1, _) = service.issue(id1)
        val (token2, _) = service.issue(id2)
        assertEquals(id1, service.validate(token1))
        assertEquals(id2, service.validate(token2))
    }

    // ── Tampered signature ────────────────────────────────────────────────────

    @Test
    fun `tampered signature returns null`() {
        val (token, _) = service.issue(UUID.randomUUID())
        // Flip the last character of the token (the signature portion)
        val tampered = token.dropLast(1) + if (token.last() == 'A') 'B' else 'A'
        assertNull(service.validate(tampered))
    }

    @Test
    fun `token signed with a different secret returns null`() {
        val otherService = WopiSlatService(secret = "completely-different-secret-value", ttlSeconds = 3600L)
        val (token, _) = otherService.issue(UUID.randomUUID())
        assertNull(service.validate(token))
    }

    @Test
    fun `manually constructed token with wrong HMAC returns null`() {
        val fileId = UUID.randomUUID()
        val expiresAt = Instant.now().epochSecond + 3600
        val encoder = Base64.getUrlEncoder().withoutPadding()
        val payload = "$fileId.$expiresAt"
        val fakeSig = ByteArray(32) { 0x00 } // all-zero signature
        val token = "${encoder.encodeToString(payload.toByteArray())}." +
            encoder.encodeToString(fakeSig)
        assertNull(service.validate(token))
    }

    // ── Expired token ─────────────────────────────────────────────────────────

    @Test
    fun `expired token returns null`() {
        val expiredService = WopiSlatService(secret = secret, ttlSeconds = -1L)
        val (token, _) = expiredService.issue(UUID.randomUUID())
        assertNull(service.validate(token))
    }

    @Test
    fun `token expiring exactly now is rejected`() {
        // Build a token whose expiresAt is one second in the past — definitively expired.
        val fileId = UUID.randomUUID()
        val expiresAt = Instant.now().epochSecond - 1
        val token = buildToken(fileId, expiresAt)
        assertNull(service.validate(token))
    }

    // ── Malformed payloads ────────────────────────────────────────────────────

    @Test
    fun `empty string returns null`() {
        assertNull(service.validate(""))
    }

    @Test
    fun `token with no dot separator returns null`() {
        assertNull(service.validate("nodotsinhere"))
    }

    @Test
    fun `token with only one segment returns null`() {
        val encoder = Base64.getUrlEncoder().withoutPadding()
        assertNull(service.validate(encoder.encodeToString("justpayload".toByteArray())))
    }

    @Test
    fun `token with non-base64 signature returns null`() {
        val encoder = Base64.getUrlEncoder().withoutPadding()
        val payload = encoder.encodeToString("some.payload".toByteArray())
        assertNull(service.validate("$payload.!!!notbase64!!!"))
    }

    @Test
    fun `token with non-base64 payload returns null`() {
        assertNull(service.validate("!!!notbase64!!!.AAAA"))
    }

    @Test
    fun `token payload with non-UUID first segment returns null`() {
        val token = buildToken(fileId = null, expiresAt = Instant.now().epochSecond + 3600, fileIdOverride = "not-a-uuid")
        assertNull(service.validate(token))
    }

    @Test
    fun `token payload with non-numeric expiry returns null`() {
        val token = buildToken(fileId = null, expiresAt = 0, expiresAtOverride = "not-a-number")
        assertNull(service.validate(token))
    }

    @Test
    fun `token payload with extra segments returns null`() {
        // Three-part payload (too many dots after decoding)
        val token = buildToken(fileId = UUID.randomUUID(), expiresAt = Instant.now().epochSecond + 3600, extraSegment = "extra")
        assertNull(service.validate(token))
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    /**
     * Builds a properly HMAC-signed token using [secret], but with the ability to
     * inject bad values for individual fields to test validation edge cases.
     */
    private fun buildToken(
        fileId: UUID?,
        expiresAt: Long,
        fileIdOverride: String? = null,
        expiresAtOverride: String? = null,
        extraSegment: String? = null,
    ): String {
        val id = fileIdOverride ?: fileId?.toString() ?: UUID.randomUUID().toString()
        val exp = expiresAtOverride ?: expiresAt.toString()
        val payload = if (extraSegment != null) "$id.$exp.$extraSegment" else "$id.$exp"

        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secret.toByteArray(Charsets.UTF_8), "HmacSHA256"))
        val sig = mac.doFinal(payload.toByteArray(Charsets.UTF_8))

        val encoder = Base64.getUrlEncoder().withoutPadding()
        return "${encoder.encodeToString(payload.toByteArray(Charsets.UTF_8))}.${encoder.encodeToString(sig)}"
    }
}
