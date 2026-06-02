// SPDX-License-Identifier: EUPL-1.2
// Copyright (C) 2026 Gemeente Utrecht
package com.baseflow.tooling

import java.net.URLDecoder

/**
 * Decodes RFC 5987-encoded strings.
 */
fun decodeRfc5987(value: String): String {
    // Format: charset'language'encoded-value
    val parts = value.split("'")
    if (parts.size != 3) return value // not RFC 5987 encoded, return as-is

    val charset = parts[0].ifEmpty { "UTF-8" }
    // parts[1] is the language tag (e.g. "en"), which we ignore
    val encodedValue = parts[2]

    return URLDecoder.decode(encodedValue, charset)
}
