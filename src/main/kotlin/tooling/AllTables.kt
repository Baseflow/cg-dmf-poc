// SPDX-License-Identifier: EUPL-1.2
// Copyright (C) 2026 Gemeente Utrecht
package com.baseflow.tooling

import com.baseflow.entities.AuditTrails
import com.baseflow.entities.BestandsDelen
import com.baseflow.entities.BlobStorageRepositories
import com.baseflow.entities.EIORecords
import com.baseflow.entities.EIOVersionTrefwoorden
import com.baseflow.entities.EIOVersions
import com.baseflow.entities.OIORecords
import com.baseflow.entities.Trefwoorden
import com.baseflow.entities.settings.ApplicationSettingsTable
import com.baseflow.entities.settings.BlobStorageRepositorySettingsTable
import com.baseflow.entities.settings.DmfSettingsTable
import com.baseflow.entities.settings.OidcProviderSettingsTable
import com.baseflow.entities.settings.ZgwApiSettingsTable
import org.jetbrains.exposed.v1.core.ExperimentalDatabaseMigrationApi
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.migration.jdbc.MigrationUtils

@OptIn(ExperimentalDatabaseMigrationApi::class)
object AllTables {
    val tables: Array<Table> = arrayOf(
        EIORecords,
        EIOVersions,
        Trefwoorden,
        EIOVersionTrefwoorden,
        OIORecords,
        AuditTrails,
        BestandsDelen,
        BlobStorageRepositories,
        BlobStorageRepositorySettingsTable,
        OidcProviderSettingsTable,
        ApplicationSettingsTable,
        ZgwApiSettingsTable,
        DmfSettingsTable,
    )

    fun createMissing() {
        transaction {
            MigrationUtils.statementsRequiredForDatabaseMigration(*tables).forEach {
                exec(it)
            }
        }
    }
}
