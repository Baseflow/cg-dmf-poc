// SPDX-License-Identifier: EUPL-1.2
// Copyright (C) 2026 Gemeente Utrecht

package com.baseflow.api.middleware

import com.baseflow.config.NotificationConfig
import com.baseflow.services.NotificationService
import io.ktor.server.application.*
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("NotificationPlugin")

/**
 * Ktor plugin that sends notifications to the Open Notificaties API after
 * successful mutation operations (create, update, delete).
 *
 * This plugin works in conjunction with AuditTrailPlugin and uses the same
 * AuditContext to capture entity changes. It should be installed on routes
 * where the AuditTrailPlugin is also installed.
 *
 * Configuration is done via environment variables:
 * - NOTIFICATION_API_URL: The URL of the Open Notificaties API endpoint
 * - NOTIFICATION_API_TOKEN: Bearer token for authentication
 * - NOTIFICATION_KANAAL: The notification channel name (default: "documenten")
 * - NOTIFICATION_SOURCE: The source identifier (default: "drc")
 *
 * Example usage:
 * ```kotlin
 * route("/api/v1") {
 *     install(NotificationPlugin)
 *     install(AuditTrailPlugin)
 *
 *     route("/enkelvoudiginformatieobjecten") {
 *         enkelvoudigInformatieObjectenRoutes()
 *     }
 * }
 * ```
 */
val NotificationPlugin = createRouteScopedPlugin("NotificationPlugin") {

    onCall {
        if (!NotificationConfig.isEnabled) {
            logger.trace("NotificationPlugin active but notifications disabled - no URL/token configured")
        }
    }

    onCallRespond { call, _ ->
        val context = call.attributes.getOrNull(AuditContextKey) ?: return@onCallRespond

        // Only send notifications if there were actual changes
        if (!context.hasChanges()) {
            return@onCallRespond
        }

        val requestScope = call.attributes.getOrNull(RequestScopeKey) ?: return@onCallRespond

        try {
            val notificationService: NotificationService = requestScope.get()
            notificationService.send(call)
        } catch (e: Exception) {
            // Don't fail the request if notification fails
            logger.error("Failed to send notification: {}", e.message)
        }
    }
}
