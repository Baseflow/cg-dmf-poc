// SPDX-License-Identifier: EUPL-1.2
// Copyright (C) 2026 Gemeente Utrecht
package com.baseflow.services

import com.baseflow.entities.EIORecordEntity
import com.baseflow.EIOVersionEntity
import com.baseflow.api.models.CreateOIORequest
import com.baseflow.api.models.SubjectTypeEnum
import com.baseflow.entities.OIORecordEntity
import com.baseflow.services.models.CreateOIOResult
import com.baseflow.services.models.DeleteOIOResult
import com.baseflow.services.models.QueryObjectInformatieObjectenFilter
import com.baseflow.tooling.AllTables
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import kotlin.test.*
import java.util.UUID

class ObjectInformatieObjectServiceTest {
    private lateinit var service: ObjectInformatieObjectService

    @BeforeTest
    fun setup() {
        Database.connect(
            "jdbc:h2:mem:test_oio;DB_CLOSE_DELAY=-1;",
            driver = "org.h2.Driver",
            user = "root",
            password = ""
        )
        transaction {
            // Create all tables
            AllTables.createMissing()
        }
        service = ObjectInformatieObjectService(resourceSegment = "objectinformatieobjecten")
    }

    @AfterTest
    fun teardown() {
        transaction {
            // Drop all tables in reverse order
            SchemaUtils.drop(*AllTables.tables.reversedArray())
        }
    }

    // Helper to create test EIO with versions for version detection tests
    private fun createTestEIO(versie: Int = 1): UUID {
        return transaction {
            val record = EIORecordEntity.new {}
            val now = Clock.System.now().toLocalDateTime(TimeZone.UTC)

            EIOVersionEntity.new {
                recordId = record
                this.versie = versie
                bronOrganisatie = "test"
                taal = "nld"
                bestandsnaam = "test.pdf"
                titel = "Test"
                auteur = "Test Author"
                creatieDatum = LocalDate(2025, 1, 1)
                beginRegistratie = now
            }
            record.id.value
        }
    }

    private fun createTestOIORequest(
        informatieobject: String = "https://example.com/documenten/api/v1/enkelvoudiginformatieobjecten/12345678-1234-1234-1234-123456789012",
        subjectObject: String = "https://example.com/zaken/api/v1/zaken/87654321-4321-4321-4321-210987654321",
        subjectType: SubjectTypeEnum = SubjectTypeEnum.ZAAK
    ): CreateOIORequest {
        return CreateOIORequest(
            informatieobject = informatieobject,
            subjectObject = subjectObject,
            subjectType = subjectType
        )
    }

    @Test
    fun `create should persist relation and return success`() {
        // Create an EIO first
        val eioId = createTestEIO(versie = 1)
        val eioUrl = "https://example.com/documenten/api/v1/enkelvoudiginformatieobjecten/$eioId"

        val req = createTestOIORequest(informatieobject = eioUrl)
        val result = service.create(req)

        assertTrue(result is CreateOIOResult.Success)
        val response = result.payload

        assertNotNull(response.url)
        // Verify the informatieobject URL contains the correct EIO UUID
        assertTrue(response.informatieobject.contains(eioId.toString()))
        assertEquals(req.subjectObject, response.subjectObject)
        assertEquals(req.subjectType, response.subjectType)
    }

    @Test
    fun `create should auto-detect version from EIO when it exists`() {
        // Create an EIO with version 3
        val eioId = createTestEIO(versie = 3)
        val eioUrl = "https://example.com/documenten/api/v1/enkelvoudiginformatieobjecten/$eioId"

        val req = createTestOIORequest(informatieobject = eioUrl)
        val result = service.create(req)

        assertTrue(result is CreateOIOResult.Success)

        // Verify version was stored in database
        transaction {
            val oioEntity = OIORecordEntity.all().first()
            assertEquals(3, oioEntity.informatieobjectVersie.versie)
        }
    }

    @Test
    fun `create should return conflict when EIO does not exist`() {
        val req = createTestOIORequest()
        val result = service.create(req)

        assertTrue(result is CreateOIOResult.Conflict)
    }

    @Test
    fun `create should return conflict for duplicate relation`() {
        val eioId = createTestEIO(versie = 1)
        val eioUrl = "https://example.com/documenten/api/v1/enkelvoudiginformatieobjecten/$eioId"
        val req = createTestOIORequest(informatieobject = eioUrl)

        // Create first relation
        val first = service.create(req)
        assertTrue(first is CreateOIOResult.Success)

        // Try to create duplicate
        val second = service.create(req)
        assertTrue(second is CreateOIOResult.Conflict)
    }

    @Test
    fun `create should allow same informatieobject with different subjectObject`() {
        val eioId = createTestEIO(versie = 1)
        val informatieobject = "https://example.com/documenten/api/v1/enkelvoudiginformatieobjecten/$eioId"

        val req1 = createTestOIORequest(
            informatieobject = informatieobject,
            subjectObject = "https://example.com/zaken/api/v1/zaken/11111111-1111-1111-1111-111111111111"
        )
        val req2 = createTestOIORequest(
            informatieobject = informatieobject,
            subjectObject = "https://example.com/zaken/api/v1/zaken/22222222-2222-2222-2222-222222222222"
        )

        val result1 = service.create(req1)
        val result2 = service.create(req2)

        assertTrue(result1 is CreateOIOResult.Success)
        assertTrue(result2 is CreateOIOResult.Success)
    }

    @Test
    fun `create should allow same subjectObject with different informatieobject`() {
        val subjectObject = "https://example.com/zaken/api/v1/zaken/87654321-4321-4321-4321-210987654321"

        val eioId1 = createTestEIO(versie = 1)
        val eioId2 = createTestEIO(versie = 1)

        val req1 = createTestOIORequest(
            informatieobject = "https://example.com/documenten/api/v1/enkelvoudiginformatieobjecten/$eioId1",
            subjectObject = subjectObject
        )
        val req2 = createTestOIORequest(
            informatieobject = "https://example.com/documenten/api/v1/enkelvoudiginformatieobjecten/$eioId2",
            subjectObject = subjectObject
        )

        val result1 = service.create(req1)
        val result2 = service.create(req2)

        assertTrue(result1 is CreateOIOResult.Success)
        assertTrue(result2 is CreateOIOResult.Success)
    }

    @Test
    fun `create should store timestamps`() {
        val eioId = createTestEIO(versie = 1)
        val eioUrl = "https://example.com/documenten/api/v1/enkelvoudiginformatieobjecten/$eioId"
        val req = createTestOIORequest(informatieobject = eioUrl)
        val result = service.create(req)

        assertTrue(result is CreateOIOResult.Success)

        transaction {
            val entity = OIORecordEntity.all().first()
            assertNotNull(entity.createdAt)
            assertNotNull(entity.updatedAt)
        }
    }

    @Test
    fun `getById should return created relation`() {
        val eioId = createTestEIO(versie = 1)
        val eioUrl = "https://example.com/documenten/api/v1/enkelvoudiginformatieobjecten/$eioId"
        val req = createTestOIORequest(informatieobject = eioUrl)
        val createResult = service.create(req) as CreateOIOResult.Success
        val createdId = createResult.payload.url!!.substringAfterLast('/')

        val found = service.getById(UUID.fromString(createdId))

        assertNotNull(found)
        // Verify the informatieobject URL contains the correct EIO UUID
        assertTrue(found.informatieobject.contains(eioId.toString()))
        assertEquals(req.subjectObject, found.subjectObject)
        assertEquals(req.subjectType, found.subjectType)
    }

    @Test
    fun `getById should return null for unknown id`() {
        val found = service.getById(UUID.randomUUID())
        assertNull(found)
    }

    @Test
    fun `exists should return true for existing id and false for random id`() {
        val eioId = createTestEIO(versie = 1)
        val eioUrl = "https://example.com/documenten/api/v1/enkelvoudiginformatieobjecten/$eioId"
        val req = createTestOIORequest(informatieobject = eioUrl)
        val createResult = service.create(req) as CreateOIOResult.Success
        val createdId = createResult.payload.url!!.substringAfterLast('/')
        val id = UUID.fromString(createdId)

        assertTrue(service.exists(id))
        assertFalse(service.exists(UUID.randomUUID()))
    }

    @Test
    fun `delete should return NotFound for unknown id`() {
        val result = service.delete(UUID.randomUUID())
        assertTrue(result is DeleteOIOResult.NotFound)
    }

    @Test
    fun `delete should return Success when relation exists`() {
        val eioId = createTestEIO(versie = 1)
        val eioUrl = "https://example.com/documenten/api/v1/enkelvoudiginformatieobjecten/$eioId"
        val req = createTestOIORequest(informatieobject = eioUrl)
        val createResult = service.create(req) as CreateOIOResult.Success
        val createdId = createResult.payload.url!!.substringAfterLast('/')
        val id = UUID.fromString(createdId)

        val result = service.delete(id)
        assertTrue(result is DeleteOIOResult.Success)

        // Verify it no longer exists
        assertFalse(service.exists(id))
    }

    @Test
    fun `getAll should return all relations when no filter`() {
        val eioId1 = createTestEIO(versie = 1)
        val eioId2 = createTestEIO(versie = 1)

        val req1 = createTestOIORequest(
            informatieobject = "https://example.com/documenten/api/v1/enkelvoudiginformatieobjecten/$eioId1"
        )
        val req2 = createTestOIORequest(
            informatieobject = "https://example.com/documenten/api/v1/enkelvoudiginformatieobjecten/$eioId2"
        )

        service.create(req1)
        service.create(req2)

        val filter = QueryObjectInformatieObjectenFilter()
        val results = service.getAll(filter).first

        assertEquals(2, results.size)
    }

    @Test
    fun `getAll should filter by informatieobject`() {
        val eioId1 = createTestEIO(versie = 1)
        val eioId2 = createTestEIO(versie = 1)

        val informatieobject1 = "https://example.com/documenten/api/v1/enkelvoudiginformatieobjecten/$eioId1"
        val informatieobject2 = "https://example.com/documenten/api/v1/enkelvoudiginformatieobjecten/$eioId2"

        val req1 = createTestOIORequest(informatieobject = informatieobject1, subjectObject = "https://example.com/zaken/api/v1/zaken/11111111-1111-1111-1111-111111111111")
        val req2 = createTestOIORequest(informatieobject = informatieobject1, subjectObject = "https://example.com/zaken/api/v1/zaken/22222222-2222-2222-2222-222222222222")
        val req3 = createTestOIORequest(informatieobject = informatieobject2, subjectObject = "https://example.com/zaken/api/v1/zaken/33333333-3333-3333-3333-333333333333")

        val result1 = service.create(req1) as CreateOIOResult.Success
        service.create(req2)
        service.create(req3)

        // Use the informatieobject URL from the response for filtering
        val filterUrl = result1.payload.informatieobject
        val filter = QueryObjectInformatieObjectenFilter(informatieobject = filterUrl)
        val (results, _) = service.getAll(filter)

        assertEquals(2, results.size)
        assertTrue(results.all { it.informatieobject == filterUrl })
    }

    @Test
    fun `getAll should filter by subjectObject`() {
        val subjectObject = "https://example.com/zaken/api/v1/zaken/87654321-4321-4321-4321-210987654321"

        val eioId1 = createTestEIO(versie = 1)
        val eioId2 = createTestEIO(versie = 1)
        val eioId3 = createTestEIO(versie = 1)

        val req1 = createTestOIORequest(informatieobject = "https://example.com/documenten/api/v1/enkelvoudiginformatieobjecten/$eioId1", subjectObject = subjectObject)
        val req2 = createTestOIORequest(informatieobject = "https://example.com/documenten/api/v1/enkelvoudiginformatieobjecten/$eioId2", subjectObject = subjectObject)
        val req3 = createTestOIORequest(informatieobject = "https://example.com/documenten/api/v1/enkelvoudiginformatieobjecten/$eioId3", subjectObject = "https://example.com/zaken/api/v1/zaken/99999999-9999-9999-9999-999999999999")

        service.create(req1)
        service.create(req2)
        service.create(req3)

        val filter = QueryObjectInformatieObjectenFilter(subjectObject = subjectObject)
        val (results, _) = service.getAll(filter)

        assertEquals(2, results.size)
        assertTrue(results.all { it.subjectObject == subjectObject })
    }

    @Test
    fun `getAll should filter by both informatieobject and subjectObject`() {
        val eioId1 = createTestEIO(versie = 1)
        val eioId2 = createTestEIO(versie = 1)

        val informatieobject = "https://example.com/documenten/api/v1/enkelvoudiginformatieobjecten/$eioId1"
        val subjectObject = "https://example.com/zaken/api/v1/zaken/87654321-4321-4321-4321-210987654321"

        val req1 = createTestOIORequest(informatieobject = informatieobject, subjectObject = subjectObject)
        val req2 = createTestOIORequest(informatieobject = informatieobject, subjectObject = "https://example.com/zaken/api/v1/zaken/99999999-9999-9999-9999-999999999999")
        val req3 = createTestOIORequest(informatieobject = "https://example.com/documenten/api/v1/enkelvoudiginformatieobjecten/$eioId2", subjectObject = subjectObject)

        val result1 = service.create(req1) as CreateOIOResult.Success
        service.create(req2)
        service.create(req3)

        // Use the informatieobject URL from the response for filtering
        val filterUrl = result1.payload.informatieobject
        val filter = QueryObjectInformatieObjectenFilter(informatieobject = filterUrl, subjectObject = subjectObject)
        val (results, _) = service.getAll(filter)

        assertEquals(1, results.size)
        assertEquals(filterUrl, results[0].informatieobject)
        assertEquals(subjectObject, results[0].subjectObject)
    }

    @Test
    fun `create should support all ObjectType enums`() {
        val eioId1 = createTestEIO(versie = 1)
        val eioId2 = createTestEIO(versie = 1)
        val eioId3 = createTestEIO(versie = 1)

        val zaakReq = createTestOIORequest(
            informatieobject = "https://example.com/documenten/api/v1/enkelvoudiginformatieobjecten/$eioId1",
            subjectObject = "https://example.com/zaken/api/v1/zaken/11111111-1111-1111-1111-111111111111",
            subjectType = SubjectTypeEnum.ZAAK
        )
        val besluitReq = createTestOIORequest(
            informatieobject = "https://example.com/documenten/api/v1/enkelvoudiginformatieobjecten/$eioId2",
            subjectObject = "https://example.com/besluiten/api/v1/besluiten/22222222-2222-2222-2222-222222222222",
            subjectType = SubjectTypeEnum.BESLUIT
        )
        val verzoekReq = createTestOIORequest(
            informatieobject = "https://example.com/documenten/api/v1/enkelvoudiginformatieobjecten/$eioId3",
            subjectObject = "https://example.com/verzoeken/api/v1/verzoeken/33333333-3333-3333-3333-333333333333",
            subjectType = SubjectTypeEnum.VERZOEK
        )

        val zaakResult = service.create(zaakReq)
        val besluitResult = service.create(besluitReq)
        val verzoekResult = service.create(verzoekReq)

        assertTrue(zaakResult is CreateOIOResult.Success)
        assertTrue(besluitResult is CreateOIOResult.Success)
        assertTrue(verzoekResult is CreateOIOResult.Success)

        assertEquals(SubjectTypeEnum.ZAAK, zaakResult.payload.subjectType)
        assertEquals(SubjectTypeEnum.BESLUIT, besluitResult.payload.subjectType)
        assertEquals(SubjectTypeEnum.VERZOEK, verzoekResult.payload.subjectType)
    }
    @Test
    fun `create should return conflict for invalid informatieobject URL`() {
        val req = createTestOIORequest(informatieobject = "not-a-url")
        val result = service.create(req)

        assertTrue(result is CreateOIOResult.Conflict)
        assertEquals("Invalid informatieobject URL", result.message)
    }

    @Test
    fun `getAll with invalid informatieobject filter URL should return empty list`() {
        // Create an EIO first
        val eioId = createTestEIO(versie = 1)
        val eioUrl = "https://example.com/documenten/api/v1/enkelvoudiginformatieobjecten/$eioId"
        service.create(createTestOIORequest(informatieobject = eioUrl))

        // Filter with invalid URL
        val filter = QueryObjectInformatieObjectenFilter(informatieobject = "invalid-url")
        val (results, _) = service.getAll(filter)

        assertTrue(results.isEmpty())
    }
}

