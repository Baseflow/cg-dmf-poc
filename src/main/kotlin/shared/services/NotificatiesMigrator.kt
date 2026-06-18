// SPDX-License-Identifier: EUPL-1.2
// Copyright (C) 2026 Gemeente Utrecht
package com.baseflow.shared.services

import com.baseflow.shared.config.Config.Companion.envOrSystem
import com.baseflow.shared.entities.settings.ApiConnectionSettingEntity
import com.baseflow.shared.entities.settings.ApiConnectionSettingsTable
import com.baseflow.shared.entities.settings.ApiConnectionType

import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.slf4j.LoggerFactory
import kotlin.time.Clock

/**
 * Bootstrap: if NOTIFICATION_API_CLIENT_SECRET is set, finds any NRC entry in
 * api_connection_settings whose base_url matches NOTIFICATION_API_URL and updates its credentials,
 * or inserts a new entry if no URL match exists. The entry is marked readonly so the admin UI
 * shows it as env-managed.
 */
object NotificatiesMigrator {
    private val logger = LoggerFactory.getLogger(NotificatiesMigrator::class.java)

    fun migrateIfNeeded() {
        val clientSecret = envOrSystem("NOTIFICATION_API_CLIENT_SECRET", "")
        if (clientSecret.isBlank()) {
            logger.debug("NOTIFICATION_API_CLIENT_SECRET not set, skipping Notificaties migration")
            return
        }

        val url = envOrSystem("NOTIFICATION_API_URL", "").trimEnd('/')
        if (url.isBlank()) {
            logger.debug("NOTIFICATION_API_URL not set, skipping Notificaties migration")
            return
        }

        val clientId = envOrSystem("NOTIFICATION_API_CLIENT_ID", "")
        if (clientId.isBlank()) {
            logger.debug("NOTIFICATION_API_CLIENT_ID not set, skipping Notificaties migration")
            return
        }

        migrate(url, clientId, clientSecret)
    }

    internal fun migrate(url: String, clientId: String, clientSecret: String) {
        val normalizedUrl = url.trimEnd('/')
        transaction {
            val existing = ApiConnectionSettingEntity.find {
                ApiConnectionSettingsTable.apiType eq ApiConnectionType.NRC.value
            }.firstOrNull { it.baseUrl.trimEnd('/') == normalizedUrl }

            if (existing != null) {
                existing.clientId = clientId
                existing.clientSecret = clientSecret
                existing.readonly = true
                existing.updatedAt = Clock.System.now()
                logger.info(
                    "Updated NRC connection '{}' from NOTIFICATION_API_* env vars (url: {})",
                    existing.name,
                    normalizedUrl,
                )
            } else {
                ApiConnectionSettingEntity.new {
                    name = "open-notificaties"
                    baseUrl = normalizedUrl
                    this.clientId = clientId
                    this.clientSecret = clientSecret
                    apiType = ApiConnectionType.NRC.value
                    validationEnabled = false
                    enabled = true
                    readonly = true
                    updatedAt = Clock.System.now()
                }
                logger.info(
                    "Inserted NRC connection 'open-notificaties' from NOTIFICATION_API_* env vars (url: {})",
                    normalizedUrl,
                )
            }
        }
    }
}
