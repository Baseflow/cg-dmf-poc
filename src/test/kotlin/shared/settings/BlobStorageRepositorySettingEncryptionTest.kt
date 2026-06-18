// SPDX-License-Identifier: EUPL-1.2
// Copyright (C) 2026 Gemeente Utrecht
package com.baseflow.shared.entities.settings

import com.baseflow.shared.tooling.AllTables
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.util.UUID
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Clock

class BlobStorageRepositorySettingEncryptionTest {

    @BeforeTest
    fun setUp() {
        Database.connect(
            "jdbc:h2:mem:enc_test_${UUID.randomUUID()};DB_CLOSE_DELAY=-1;",
            driver = "org.h2.Driver",
            user = "root",
            password = "",
        )
        transaction { AllTables.createMissing() }
    }

    @Test
    fun `encrypted value persists and is transparently decrypted on read`() {
        val id = transaction {
            BlobStorageRepositorySettingEntity.new {
                repoName = "test-repo-${UUID.randomUUID()}"
                storageType = "S3"
                url = "http://localhost:9000"
                accessKey = "my-access-key"
                secretKey = null
                bucket = "test"
                storageAccountName = null
                createdAt = Clock.System.now()
                updatedAt = Clock.System.now()
            }.id.value
        }

        assertEquals("my-access-key", transaction { BlobStorageRepositorySettingEntity[id].accessKey })
    }
}
