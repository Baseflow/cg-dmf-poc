// SPDX-License-Identifier: EUPL-1.2
// Copyright (C) 2026 Gemeente Utrecht
package com.baseflow.testutils

import java.net.URLDecoder

/**
 * Decodes RFC 5987-encoded strings.
 */
fun decodeRfc5987(value: String): String {
    // Format: charset'language'encoded-value (RFC 5987 / RFC 6266)
    val quote = '\'' // single quote (')
    val firstQuote = value.indexOf(quote)
    if (firstQuote < 0) return value
    val secondQuote = value.indexOf(quote, startIndex = firstQuote + 1)
    if (secondQuote < 0) return value

    val charsetToken = value.substring(0, firstQuote).ifEmpty { "UTF-8" }
    val encodedValue = value.substring(secondQuote + 1)

    val charset = runCatching { java.nio.charset.Charset.forName(charsetToken) }.getOrNull() ?: return value

    return runCatching {
        // RFC 5987 uses percent-encoding; '+' should remain '+' (URLDecoder treats '+' as space).
        URLDecoder.decode(encodedValue.replace("+", "%2B"), charset)
    }.getOrElse { value }
}
