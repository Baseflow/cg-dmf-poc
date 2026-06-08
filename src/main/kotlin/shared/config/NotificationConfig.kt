// SPDX-License-Identifier: EUPL-1.2
// Copyright (C) 2026 Gemeente Utrecht
package com.baseflow.shared.config

import org.slf4j.LoggerFactory

/**
 * Configuration for the Open Notificaties API connection.
 * Values are loaded from environment variables.
 */
object NotificationConfig : Config() {
    private val logger = LoggerFactory.getLogger(NotificationConfig::class.java)

    /**
     * Base URL of the Open Notificaties API (without specific endpoint paths).
     *
     * The application will append its own path segments (e.g. "/notificaties", "/kanaal")
     * to this base URL when calling the API. Do not include these segments here.
     *
     * Example: https://notificaties.example.com/api/v1
     */
    val url: String? = envOrSystem("NOTIFICATION_API_URL", "").ifBlank { null }

    /**
     * Client ID used to sign JWT tokens for the Open Notificaties API.
     * Must match the client registered in the Open Notificaties service.
     */
    val clientId: String? = envOrSystem("NOTIFICATION_API_CLIENT_ID", "").ifBlank { null }

    /**
     * Client secret (shared key) used to sign JWT tokens for the Open Notificaties API
     * using the HS256 algorithm.
     */
    val clientSecret: String? = envOrSystem("NOTIFICATION_API_CLIENT_SECRET", "").ifBlank { null }

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
     * Returns true only if URL, client ID and client secret are all configured.
     */
    val isEnabled: Boolean
        get() = !url.isNullOrBlank() && !clientId.isNullOrBlank() && !clientSecret.isNullOrBlank()

    override fun printConfig() {
        logger.info(
            "NotificationConfig: enabled={}, url={}, kanaal={}, source={}",
            isEnabled,
            url?.take(50)?.let { "$it..." } ?: "<not configured>",
            kanaal,
            source,
        )
        if (!isEnabled) {
            logger.warn(
                "Notifications are disabled. Set NOTIFICATION_API_URL, NOTIFICATION_API_CLIENT_ID and NOTIFICATION_API_CLIENT_SECRET to enable.",
            )
        }
    }
}
