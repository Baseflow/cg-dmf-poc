// SPDX-License-Identifier: EUPL-1.2
// Copyright (C) 2026 Gemeente Utrecht
package com.baseflow.shared.services

import com.baseflow.shared.config.Config.Companion.envOrSystem
import com.baseflow.shared.entities.settings.ApiConnectionSettingEntity
import com.baseflow.shared.entities.settings.ApiConnectionSettingsTable
import com.baseflow.shared.entities.settings.ApiConnectionType
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.slf4j.LoggerFactory
import kotlin.time.Clock

/**
 * One-time bootstrap: if OPENZAAK_CLIENT_SECRET is set, upserts two entries:
 *   - ZTC at <endpoint>/catalogi/api/v1
 *   - ZRC at <endpoint>/zaken/api/v1
 * Matches existing entries by base_url + api_type and updates credentials in place,
 * or inserts a new entry if no URL match exists. Entries are marked readonly so the
 * admin UI shows them as env-managed.
 */
object OpenZaakMigrator {
    private val logger = LoggerFactory.getLogger(OpenZaakMigrator::class.java)

    private data class ConnectionSpec(val name: String, val apiType: ApiConnectionType, val baseUrl: String)

    fun migrateIfNeeded() {
        val clientSecret = envOrSystem("OPENZAAK_CLIENT_SECRET", "")
        if (clientSecret.isBlank()) {
            logger.debug("OPENZAAK_CLIENT_SECRET not set, skipping OpenZaak migration")
            return
        }

        val endpoint = envOrSystem("OPENZAAK_ENDPOINT", "https://openzaak.dev.baseflow.com")
            .trimEnd('/')
        val clientId = envOrSystem("OPENZAAK_CLIENT_ID", "cg-dmf")
        val validationEnabled = envOrSystem("OPENZAAK_VALIDATION_ENABLED", "true").toBoolean()

        val specs = listOf(
            ConnectionSpec("openzaak-ztc", ApiConnectionType.ZTC, "$endpoint/catalogi/api/v1"),
            ConnectionSpec("openzaak-zrc", ApiConnectionType.ZRC, "$endpoint/zaken/api/v1"),
        )

        transaction {
            for (spec in specs) {
                upsert(spec, clientId, clientSecret, validationEnabled)
            }
        }
    }

    private fun upsert(spec: ConnectionSpec, clientId: String, clientSecret: String, validationEnabled: Boolean) {
        val existing = ApiConnectionSettingEntity.find {
            ApiConnectionSettingsTable.apiType eq spec.apiType.value
        }.firstOrNull { it.baseUrl.trimEnd('/') == spec.baseUrl.trimEnd('/') }

        if (existing != null) {
            existing.clientId = clientId
            existing.clientSecret = clientSecret
            existing.validationEnabled = validationEnabled
            existing.readonly = true
            existing.updatedAt = Clock.System.now().toLocalDateTime(TimeZone.UTC)
            logger.info(
                "Updated {} connection '{}' from OPENZAAK_* env vars (url: {})",
                spec.apiType.value.uppercase(),
                existing.name,
                spec.baseUrl,
            )
        } else {
            ApiConnectionSettingEntity.new {
                name = spec.name
                baseUrl = spec.baseUrl
                this.clientId = clientId
                this.clientSecret = clientSecret
                apiType = spec.apiType.value
                this.validationEnabled = validationEnabled
                enabled = true
                readonly = true
                updatedAt = Clock.System.now().toLocalDateTime(TimeZone.UTC)
            }
            logger.info(
                "Inserted {} connection '{}' from OPENZAAK_* env vars (url: {})",
                spec.apiType.value.uppercase(),
                spec.name,
                spec.baseUrl,
            )
        }
    }
}
