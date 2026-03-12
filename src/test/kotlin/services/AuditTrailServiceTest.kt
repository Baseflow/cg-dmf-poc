// SPDX-License-Identifier: EUPL-1.2
// Copyright (C) 2026 Gemeente Utrecht
package com.baseflow.services

import com.baseflow.api.middleware.AuditContext
import com.baseflow.api.models.EnkelvoudigInformatieObjectResponse
import com.baseflow.api.models.EnkelvoudigInformatieObjectStatus
import com.baseflow.api.models.Vertrouwelijkheidaanduiding
import com.baseflow.entities.AuditTrailEntity
import com.baseflow.entities.IAuditContext
import com.baseflow.tooling.AllTables
import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.auth.principal
import io.ktor.server.request.httpMethod
import io.mockk.*
import kotlinx.datetime.LocalDate
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.util.*
import kotlin.test.*

class AuditTrailServiceTest {
    private lateinit var auditContext: AuditContext
    private lateinit var service: AuditTrailService

    @BeforeTest
    fun setup() {
        Database.connect(
            "jdbc:h2:mem:test_audit;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
            driver = "org.h2.Driver",
            user = "root",
            password = ""
        )
        transaction {
            AllTables.createMissing()
        }
        auditContext = AuditContext()
        service = AuditTrailService(auditContext)
    }

    @AfterTest
    fun teardown() {
        transaction {
            SchemaUtils.drop(*AllTables.tables.reversedArray())
        }
    }

    // Test data class implementing IAuditContext for testing
    data class TestAuditContext(
        override var bronOrganisatie: String = "012345678",
        override var vertrouwlijkheidsAanduiding: String = "openbaar",
        override var identificatie: String = "TEST-001",
        override var informatieobject_type: String = "https://example.com/type/1"
    ) : IAuditContext

    /**
     * Helper function to create a test entity response.
     */
    private fun createTestEntity(
        entityId: String = UUID.randomUUID().toString(),
        name: String = "test"
    ): EnkelvoudigInformatieObjectResponse {
        return EnkelvoudigInformatieObjectResponse(
            id = entityId,
            url = "https://example.com/resource/$entityId",
            identificatie = "TEST-$name",
            bronorganisatie = "012345678",
            creatiedatum = LocalDate(2026, 1, 1),
            titel = name,
            versie = 1,
            vertrouwelijkheidaanduiding = Vertrouwelijkheidaanduiding.OPENBAAR,
            auteur = "test-author",
            status = EnkelvoudigInformatieObjectStatus.CONCEPT,
            taal = "dut",
            beginRegistratie = "2026-01-01T00:00:00Z",
            informatieobjecttype = "https://example.com/type/1",
            lock = "",
            locked = false
        )
    }

    private fun createMockCall(
        httpMethod: HttpMethod = HttpMethod.Post,
        userId: String = "test-user-id",
        username: String = "testuser",
        clientId: String = "test-client",
        auditToelichting: String? = null,
        statusCode: HttpStatusCode = HttpStatusCode.Created
    ): PipelineCall {
        val call = mockk<PipelineCall>(relaxed = true)
        val headers = mockk<Headers>(relaxed = true)

        every { call.request.httpMethod } returns httpMethod
        every { call.request.headers } returns headers
        every { headers["X-Audit-Toelichting"] } returns auditToelichting
        every { call.response.status() } returns statusCode

        // Create a real JWT token for testing
        val token = JWT.create()
            .withSubject(userId)
            .withClaim("username", username)
            .withClaim("client_id", clientId)
            .sign(Algorithm.HMAC256("secret"))

        val decodedJwt = JWT.decode(token)
        val principal = JWTPrincipal(decodedJwt)

        every { call.principal<JWTPrincipal>() } returns principal

        return call
    }

    @Test
    fun `create should not persist audit when no changes captured`() {
        val call = createMockCall()

        service.create(call)

        transaction {
            val count = AuditTrailEntity.all().count()
            assertEquals(0, count)
        }
    }

    @Test
    fun `create should persist audit trail for POST request with new entity`() {
        val entityId = UUID.randomUUID().toString()
        val testEntity = createTestEntity(entityId = entityId)
        val sourceRequest = TestAuditContext()

        auditContext.captureNew(testEntity, sourceRequest)

        val call = createMockCall(
            httpMethod = HttpMethod.Post,
            statusCode = HttpStatusCode.Created
        )

        service.create(call)

        transaction {
            val audits = AuditTrailEntity.all().toList()
            assertEquals(1, audits.size)

            val audit = audits.first()
            assertEquals("create", audit.actie)
            assertEquals("Object aangemaakt", audit.actieWeergave)
            assertEquals("drc", audit.bron)
            assertEquals("enkelvoudiginformatieobjecten", audit.resource)
            assertEquals(201, audit.resultaat)
            assertEquals("test-user-id", audit.gebruikersId)
            assertEquals("testuser", audit.gebruikersWeergave)
            assertEquals("test-client", audit.applicatieId)
            assertTrue(audit.resourceUrl.contains(entityId))
            assertNotNull(audit.wijzigingen.nieuw)
            assertNull(audit.wijzigingen.oud)
        }
    }

    @Test
    fun `create should persist audit trail for PATCH request with old and new entity`() {
        val entityId = UUID.randomUUID().toString()
        val oldEntity = createTestEntity(entityId = entityId, name = "old")
        val newEntity = createTestEntity(entityId = entityId, name = "new")
        val sourceRequest = TestAuditContext()

        auditContext.captureOld(oldEntity)
        auditContext.captureNew(newEntity, sourceRequest)

        val call = createMockCall(
            httpMethod = HttpMethod.Patch,
            statusCode = HttpStatusCode.OK
        )

        service.create(call)

        transaction {
            val audits = AuditTrailEntity.all().toList()
            assertEquals(1, audits.size)

            val audit = audits.first()
            assertEquals("partial_update", audit.actie)
            assertEquals("Object deels bijgewerkt", audit.actieWeergave)
            assertNotNull(audit.wijzigingen.oud)
            assertNotNull(audit.wijzigingen.nieuw)
        }
    }

    @Test
    fun `create should persist audit trail for PUT request`() {
        val entityId = UUID.randomUUID().toString()
        val oldEntity = createTestEntity(entityId = entityId, name = "old")
        val newEntity = createTestEntity(entityId = entityId, name = "new")
        val sourceRequest = TestAuditContext()

        auditContext.captureOld(oldEntity)
        auditContext.captureNew(newEntity, sourceRequest)

        val call = createMockCall(
            httpMethod = HttpMethod.Put,
            statusCode = HttpStatusCode.OK
        )

        service.create(call)

        transaction {
            val audits = AuditTrailEntity.all().toList()
            assertEquals(1, audits.size)

            val audit = audits.first()
            assertEquals("update", audit.actie)
            assertEquals("Object bijgewerkt", audit.actieWeergave)
        }
    }

    @Test
    fun `create should persist audit trail for DELETE request`() {
        val entityId = UUID.randomUUID().toString()
        val oldEntity = createTestEntity(entityId = entityId)
        val sourceRequest = TestAuditContext()

        auditContext.captureOld(oldEntity)
        auditContext.captureNew(null, sourceRequest)

        val call = createMockCall(
            httpMethod = HttpMethod.Delete,
            statusCode = HttpStatusCode.NoContent
        )

        service.create(call)

        transaction {
            val audits = AuditTrailEntity.all().toList()
            assertEquals(1, audits.size)

            val audit = audits.first()
            assertEquals("destroy", audit.actie)
            assertEquals("Object verwijderd", audit.actieWeergave)
            assertNotNull(audit.wijzigingen.oud)
            assertNull(audit.wijzigingen.nieuw)
        }
    }

    @Test
    fun `create should persist audit trail for GET request as retrieve action`() {
        val entityId = UUID.randomUUID().toString()
        val entity = createTestEntity(entityId = entityId)

        auditContext.captureOld(entity)

        val call = createMockCall(
            httpMethod = HttpMethod.Get,
            statusCode = HttpStatusCode.OK
        )

        service.create(call)

        transaction {
            val audits = AuditTrailEntity.all().toList()
            assertEquals(1, audits.size)

            val audit = audits.first()
            assertEquals("retrieve", audit.actie)
            assertEquals("Object opgehaald", audit.actieWeergave)
        }
    }

    @Test
    fun `create should include X-Audit-Toelichting header in audit trail`() {
        val entityId = UUID.randomUUID().toString()
        val testEntity = createTestEntity(entityId = entityId)
        val sourceRequest = TestAuditContext()

        auditContext.captureNew(testEntity, sourceRequest)

        val call = createMockCall(
            httpMethod = HttpMethod.Post,
            auditToelichting = "Custom toelichting for audit"
        )

        service.create(call)

        transaction {
            val audits = AuditTrailEntity.all().toList()
            assertEquals(1, audits.size)
            assertEquals("Custom toelichting for audit", audits.first().toelichting)
        }
    }

    @Test
    fun `listByResource should return audit trails for specific resource`() {
        val resourceUuid = UUID.randomUUID()

        // Create some audit trails manually
        transaction {
            AuditTrailEntity.new {
                bron = "drc"
                applicatieId = "test-app"
                applicatieWeergave = "Test App"
                actie = "create"
                actieWeergave = "Object aangemaakt"
                hoofdObject = "https://example.com/resource/$resourceUuid"
                resource = "enkelvoudiginformatieobjecten"
                resourceUrl = "https://example.com/resource/$resourceUuid"
                resourceWeergave = "test"
                resultaat = 201
            }

            // Create another audit for different resource
            AuditTrailEntity.new {
                bron = "drc"
                applicatieId = "test-app"
                applicatieWeergave = "Test App"
                actie = "create"
                actieWeergave = "Object aangemaakt"
                hoofdObject = "https://example.com/resource/${UUID.randomUUID()}"
                resource = "enkelvoudiginformatieobjecten"
                resourceUrl = "https://example.com/resource/${UUID.randomUUID()}"
                resourceWeergave = "test"
                resultaat = 201
            }
        }

        val results = service.listByResource(resourceUuid)

        assertEquals(1, results.size)
        assertTrue(results.first().resourceUrl?.contains(resourceUuid.toString()) == true)
    }

    @Test
    fun `getByUuid should return specific audit trail for resource`() {
        val resourceUuid = UUID.randomUUID()
        var auditTrailUuid: UUID? = null

        transaction {
            val entity = AuditTrailEntity.new {
                bron = "drc"
                applicatieId = "test-app"
                applicatieWeergave = "Test App"
                actie = "create"
                actieWeergave = "Object aangemaakt"
                hoofdObject = "https://example.com/resource/$resourceUuid"
                resource = "enkelvoudiginformatieobjecten"
                resourceUrl = "https://example.com/resource/$resourceUuid"
                resourceWeergave = "test"
                resultaat = 201
            }
            auditTrailUuid = entity.id.value
        }

        val result = service.getByUuid(resourceUuid, auditTrailUuid!!)

        assertNotNull(result)
        assertEquals(auditTrailUuid.toString(), result.uuid)
    }

    @Test
    fun `getByUuid should return null for non-matching resource`() {
        val resourceUuid = UUID.randomUUID()
        val otherResourceUuid = UUID.randomUUID()
        var auditTrailUuid: UUID? = null

        transaction {
            val entity = AuditTrailEntity.new {
                bron = "drc"
                applicatieId = "test-app"
                applicatieWeergave = "Test App"
                actie = "create"
                actieWeergave = "Object aangemaakt"
                hoofdObject = "https://example.com/resource/$resourceUuid"
                resource = "enkelvoudiginformatieobjecten"
                resourceUrl = "https://example.com/resource/$resourceUuid"
                resourceWeergave = "test"
                resultaat = 201
            }
            auditTrailUuid = entity.id.value
        }

        // Try to get audit trail using wrong resource UUID
        val result = service.getByUuid(otherResourceUuid, auditTrailUuid!!)

        assertNull(result)
    }

    @Test
    fun `removeAuditTrailsForResource should delete all audits for resource`() {
        val resourceUuid = UUID.randomUUID()
        val otherResourceUuid = UUID.randomUUID()

        transaction {
            // Create two audits for same resource
            repeat(2) {
                AuditTrailEntity.new {
                    bron = "drc"
                    applicatieId = "test-app"
                    applicatieWeergave = "Test App"
                    actie = "create"
                    actieWeergave = "Object aangemaakt"
                    hoofdObject = "https://example.com/resource/$resourceUuid"
                    resource = "enkelvoudiginformatieobjecten"
                    resourceUrl = "https://example.com/resource/$resourceUuid"
                    resourceWeergave = "test"
                    resultaat = 201
                }
            }

            // Create audit for different resource
            AuditTrailEntity.new {
                bron = "drc"
                applicatieId = "test-app"
                applicatieWeergave = "Test App"
                actie = "create"
                actieWeergave = "Object aangemaakt"
                hoofdObject = "https://example.com/resource/$otherResourceUuid"
                resource = "enkelvoudiginformatieobjecten"
                resourceUrl = "https://example.com/resource/$otherResourceUuid"
                resourceWeergave = "test"
                resultaat = 201
            }
        }

        service.removeAuditTrailsForResource(resourceUuid)

        transaction {
            val allAudits = AuditTrailEntity.all().toList()
            assertEquals(1, allAudits.size)
            assertTrue(allAudits.first().resourceUrl.contains(otherResourceUuid.toString()))
        }
    }

    @Test
    fun `create should use unknown user when no JWT principal`() {
        val entityId = UUID.randomUUID().toString()
        val testEntity = createTestEntity(entityId = entityId)
        val sourceRequest = TestAuditContext()

        auditContext.captureNew(testEntity, sourceRequest)

        val call = mockk<PipelineCall>(relaxed = true)
        val headers = mockk<Headers>(relaxed = true)

        every { call.request.httpMethod } returns HttpMethod.Post
        every { call.request.headers } returns headers
        every { headers["X-Audit-Toelichting"] } returns null
        every { call.response.status() } returns HttpStatusCode.Created
        every { call.principal<JWTPrincipal>() } returns null

        service.create(call)

        transaction {
            val audits = AuditTrailEntity.all().toList()
            assertEquals(1, audits.size)
            assertEquals("unknown", audits.first().gebruikersId)
            assertEquals("unknown", audits.first().gebruikersWeergave)
        }
    }

    @Test
    fun `create should use X-NLX-Request-Application-Id header when no JWT`() {
        val entityId = UUID.randomUUID().toString()
        val testEntity = createTestEntity(entityId = entityId)
        val sourceRequest = TestAuditContext()

        auditContext.captureNew(testEntity, sourceRequest)

        val call = mockk<PipelineCall>(relaxed = true)
        val headers = mockk<Headers>(relaxed = true)

        every { call.request.httpMethod } returns HttpMethod.Post
        every { call.request.headers } returns headers
        every { headers["X-Audit-Toelichting"] } returns null
        every { headers["X-NLX-Request-Application-Id"] } returns "nlx-app-123"
        every { call.response.status() } returns HttpStatusCode.Created
        every { call.principal<JWTPrincipal>() } returns null

        service.create(call)

        transaction {
            val audits = AuditTrailEntity.all().toList()
            assertEquals(1, audits.size)
            assertEquals("nlx-app-123", audits.first().applicatieId)
            assertEquals("nlx-app-123", audits.first().applicatieWeergave)
        }
    }

    @Test
    fun `AuditAction enum should have correct values and display names`() {
        assertEquals("create", AuditAction.CREATE.value)
        assertEquals("Object aangemaakt", AuditAction.CREATE.weergave)

        assertEquals("retrieve", AuditAction.RETRIEVE.value)
        assertEquals("Object opgehaald", AuditAction.RETRIEVE.weergave)

        assertEquals("list", AuditAction.LIST.value)
        assertEquals("Lijst van objecten opgehaald", AuditAction.LIST.weergave)

        assertEquals("partial_update", AuditAction.PARTIAL_UPDATE.value)
        assertEquals("Object deels bijgewerkt", AuditAction.PARTIAL_UPDATE.weergave)

        assertEquals("destroy", AuditAction.DESTROY.value)
        assertEquals("Object verwijderd", AuditAction.DESTROY.weergave)

        assertEquals("update", AuditAction.UPDATE.value)
        assertEquals("Object bijgewerkt", AuditAction.UPDATE.weergave)
    }

    @Test
    fun `AuditSource enum should have correct display names`() {
        assertEquals("drc", AuditSource.DRC.weergave)
        assertEquals("zrc", AuditSource.ZRC.weergave)
        assertEquals("ztc", AuditSource.ZTC.weergave)
    }

    @Test
    fun `httpMethodToAction mapping should be correct`() {
        assertEquals(AuditAction.RETRIEVE, httpMethodToAction[HttpMethod.Get])
        assertEquals(AuditAction.CREATE, httpMethodToAction[HttpMethod.Post])
        assertEquals(AuditAction.PARTIAL_UPDATE, httpMethodToAction[HttpMethod.Patch])
        assertEquals(AuditAction.DESTROY, httpMethodToAction[HttpMethod.Delete])
        assertEquals(AuditAction.UPDATE, httpMethodToAction[HttpMethod.Put])
        assertEquals(AuditAction.HEAD, httpMethodToAction[HttpMethod.Head])
    }
}
