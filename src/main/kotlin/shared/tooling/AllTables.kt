// SPDX-License-Identifier: EUPL-1.2
// Copyright (C) 2026 Gemeente Utrecht
package com.baseflow.shared.tooling

import com.baseflow.shared.entities.AuditTrails
import com.baseflow.shared.entities.BestandsDelen
import com.baseflow.shared.entities.EIORecords
import com.baseflow.shared.entities.EIOVersionTrefwoorden
import com.baseflow.shared.entities.EIOVersions
import com.baseflow.shared.entities.OIORecords
import com.baseflow.shared.entities.Trefwoorden
import com.baseflow.shared.entities.settings.ApiConnectionSettingsTable
import com.baseflow.shared.entities.settings.ApplicationSettingsTable
import com.baseflow.shared.entities.settings.BlobStorageRepositorySettingsTable
import com.baseflow.shared.entities.settings.DmfSettingsTable
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
        BlobStorageRepositorySettingsTable,
        ApplicationSettingsTable,
        ApiConnectionSettingsTable,
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
