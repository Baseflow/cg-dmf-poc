// SPDX-License-Identifier: EUPL-1.2
// Copyright (C) 2026 Gemeente Utrecht
package com.baseflow.config

import org.slf4j.LoggerFactory

/**
 * Configuration for the Open Notificaties API connection.
 * Values are loaded from environment variables.
 */
object NotificationConfig : Config() {
    private val logger = LoggerFactory.getLogger(NotificationConfig::class.java)

    /**
     * URL of the Open Notificaties API endpoint for publishing notifications.
     * Example: https://notificaties.example.com/api/v1/notificaties
     */
    val url: String? = envOrSystem("NOTIFICATION_API_URL", "").ifBlank { null }

    /**
     * Bearer token for authenticating with the Open Notificaties API.
     * This token should have the 'notificaties.publiceren' scope.
     */
    val bearerToken: String? = envOrSystem("NOTIFICATION_API_TOKEN", "").ifBlank { null }

    /**
     * The name of the notification channel (kanaal).
     * Defaults to "documenten" as per the Documenten API standard.
     */
    val kanaal: String = envOrSystem("NOTIFICATION_KANAAL", "documenten")

    /**
     * Source identifier for notifications.
     * This identifies the origin of the notification.
     */
    val source: String = envOrSystem("NOTIFICATION_SOURCE", "drc")

    /**
     * Whether notifications are enabled.
     * Returns true only if both URL and token are configured.
     */
    val isEnabled: Boolean
        get() = !url.isNullOrBlank() && !bearerToken.isNullOrBlank()

    override fun printConfig() {
        logger.info(
            "NotificationConfig: enabled={}, url={}, kanaal={}, source={}",
            isEnabled,
            url?.take(50)?.let { "$it..." } ?: "<not configured>",
            kanaal,
            source
        )
        if (!isEnabled) {
            logger.warn("Notifications are disabled. Set NOTIFICATION_API_URL and NOTIFICATION_API_TOKEN to enable.")
        }
    }
}
