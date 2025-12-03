// SPDX-License-Identifier: EUPL-1.2
// Copyright (C) 2025 Gemeente Utrecht
package com.baseflow.services

import com.baseflow.EIORecords
import com.baseflow.EIOVersions
import com.baseflow.api.models.CreateEIORequest
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import kotlin.test.*
import java.util.UUID

class EnkelvoudigInformatieObjectServiceTest {
    private lateinit var service: EnkelvoudigInformatieObjectService

    @BeforeTest
    fun setup() {
        Database.connect(
            "jdbc:h2:mem:test;DB_CLOSE_DELAY=-1;",
            driver = "org.h2.Driver",
            user = "root",
            password = ""
        )
        transaction {
            SchemaUtils.create(EIORecords, EIOVersions)
        }
        service = EnkelvoudigInformatieObjectService()
    }

    @AfterTest
    fun teardown() {
        transaction {
            SchemaUtils.drop(EIOVersions, EIORecords)
        }
    }

    @Test
    fun `create should persist and return correct data`() {
        val req = CreateEIORequest(taal = "dut", bestandsnaam = "doc.pdf")
        val resp = service.create(req)
        assertEquals("dut", resp.taal)
        assertEquals("doc.pdf", resp.bestandsnaam)
        assertEquals(1, resp.versie)
        assertTrue(resp.id.isNotEmpty())
    }

    @Test
    fun `getById should return created object`() {
        val req = CreateEIORequest(taal = "dut", bestandsnaam = "doc.pdf")
        val created = service.create(req)
        val found = service.getById(UUID.fromString(created.id))
        assertNotNull(found)
        assertEquals(created.id, found!!.id)
        assertEquals("dut", found.taal)
        assertEquals("doc.pdf", found.bestandsnaam)
        assertEquals(1, found.versie)
    }

    @Test
    fun `update should increment version and persist new data`() {
        val req = CreateEIORequest(taal = "dut", bestandsnaam = "doc.pdf")
        val created = service.create(req)
        val updateReq = CreateEIORequest(taal = "eng", bestandsnaam = "doc2.pdf")
        val updated = service.update(UUID.fromString(created.id), updateReq)
        assertNotNull(updated)
        assertEquals(created.id, updated!!.id)
        assertEquals("eng", updated.taal)
        assertEquals("doc2.pdf", updated.bestandsnaam)
        assertEquals(2, updated.versie)
    }

    @Test
    fun `getById should return null for unknown id`() {
        val found = service.getById(UUID.randomUUID())
        assertNull(found)
    }
}