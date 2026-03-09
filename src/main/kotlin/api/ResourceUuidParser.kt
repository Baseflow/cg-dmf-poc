// SPDX-License-Identifier: EUPL-1.2
// Copyright (C) 2026 Gemeente Utrecht
package com.baseflow.api

import org.slf4j.LoggerFactory
import java.util.*

/**
 * Utility class for extracting UUIDs from URLs
 */
object ResourceUuidParser {
    private val logger = LoggerFactory.getLogger(ResourceUuidParser::class.java)

    /**
     * Extract UUID from a URL like .../resourceSegment/{uuid}
     *
     * @param url The URL to extract the UUID from
     * @param resourceSegment The resource segment that must precede the UUID (e.g., "enkelvoudiginformatieobjecten")
     * @return The extracted UUID, or null if extraction fails or doesn't match the segment
     */
    fun parseUuid(url: String, resourceSegment: String): UUID? = try {
        // Check if URL contains /{resourceSegment}/{uuid}
        val pattern = Regex(
            ".*/$resourceSegment/([0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12})$",
            RegexOption.IGNORE_CASE,
        )
        val matchResult = pattern.find(url)

        if (matchResult != null) {
            val uuidString = matchResult.groupValues[1]
            UUID.fromString(uuidString)
        } else {
            logger.debug("URL does not match expected pattern .../$resourceSegment/{{uuid}}: $url")
            null
        }
    } catch (e: Exception) {
        logger.debug("Failed to extract UUID from URL: $url with segment: $resourceSegment", e)
        null
    }
}
