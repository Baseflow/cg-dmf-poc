package com.baseflow.tooling

import com.baseflow.entities.AuditTrails
import com.baseflow.entities.EIORecords
import com.baseflow.EIOVersions
import com.baseflow.entities.OIORecords
import org.jetbrains.exposed.v1.core.ExperimentalDatabaseMigrationApi
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.migration.jdbc.MigrationUtils

@OptIn(ExperimentalDatabaseMigrationApi::class)
object AllTables {
    val tables: Array<Table> = arrayOf(
        EIORecords,
        EIOVersions,
        OIORecords,
        AuditTrails
    )

    fun createMissing() {
        transaction {
            MigrationUtils.statementsRequiredForDatabaseMigration(*tables).forEach {
                exec(it)
            }
        }
    }
}