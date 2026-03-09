// SPDX-License-Identifier: EUPL-1.2
// Copyright (C) 2025 Gemeente Utrecht
package com.baseflow.api.middleware

import io.ktor.http.ContentType
import io.ktor.http.content.EntityTagVersion
import io.ktor.http.content.OutgoingContent
import io.ktor.http.content.TextContent
import io.ktor.http.content.Version
import io.ktor.server.application.ApplicationCall
import java.security.MessageDigest

/**
 * Implementation helper for ConditionalHeaders: when given an [OutgoingContent],
 * returns an `EntityTagVersion` for JSON `TextContent` based on SHA‑1 of the
 * exact serialized body; otherwise returns `null`.
 */
fun jsonEtagVersionFor(content: OutgoingContent): EntityTagVersion? {
    val contentType = content.contentType?.withoutParameters()
    val text = (content as? TextContent)?.text
    return if (contentType == ContentType.Application.Json && text != null) {
        val tag = sha1Hex(text)
        EntityTagVersion(tag)
    } else {
        null
    }
}

// Reusable provider to plug into ConditionalHeaders.version { ... }
// if you need to vary on additional factors, add those to this list
val ApiConditionalHeadersProvider: suspend (ApplicationCall, OutgoingContent) -> List<Version> = { _, content ->
    listOfNotNull(jsonEtagVersionFor(content))
}

// sha-1 is not secure, but good enough for caching
private fun sha1Hex(data: String): String {
    val md = MessageDigest.getInstance("SHA-1")
    val hash = md.digest(data.toByteArray(Charsets.UTF_8))
    return hash.joinToString("") { b ->
        val v = b.toInt() and 0xff
        (if (v < 16) "0" else "") + v.toString(16)
    }
}
