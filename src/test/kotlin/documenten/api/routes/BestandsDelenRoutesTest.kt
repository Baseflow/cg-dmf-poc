// SPDX-License-Identifier: EUPL-1.2
// Copyright (C) 2026 Gemeente Utrecht
package com.baseflow.documenten.api.routes

import com.baseflow.shared.api.DOCUMENTEN_API_BASE_PATH
import com.baseflow.shared.api.middleware.AuditContext
import com.baseflow.shared.config.ApplicationConfig
import com.baseflow.shared.config.BestandsDeelConfig
import com.baseflow.shared.config.OpenZaakConfig
import com.baseflow.shared.services.AuditTrailService
import com.baseflow.shared.services.BestandsDeelService
import com.baseflow.shared.services.CatalogusService
import com.baseflow.shared.services.EnkelvoudigInformatieObjectService
import com.baseflow.shared.services.StorageService
import com.baseflow.testutils.TestDataFactory
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.InputStream
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Unit tests for the PUT /bestandsdelen/{uuid} route.
 *
 * Test data (bestandsdelen) is seeded directly via the service layer so the tests
 * remain self-contained and never need a running external service.
 */
class BestandsDelenRoutesTest : TestBase("bestandsdelen_routes") {

    companion object {
        private const val BESTANDSDELEN_PATH = "$DOCUMENTEN_API_BASE_PATH/bestandsdelen"

        /**
         * Tiny trigger/chunk config so an EIO with a small [com.baseflow.shared.entities.EIOVersions.bestandsomvang] triggers chunking
         * without needing multi-gigabyte values.
         */
        private val SMALL_CHUNK_CONFIG: BestandsDeelConfig = object : BestandsDeelConfig() {
            override val triggerSizeBytes: Long = 10L
            override val chunkSizeBytes: Long = 4L
        }
    }

    /**
     * Creates an EIO with a large enough [com.baseflow.shared.entities.EIOVersions.bestandsomvang] to trigger chunked upload,
     * then returns a pair of (bestandsdeelUuid, lockToken) for the first chunk.
     *w
     * Uses the small config so we don't need actual gigabyte-sized files in tests.
     */
    private fun createBestandsdeelInDb(): Pair<UUID, String> = runBlocking {
        val openZaakConfig = OpenZaakConfig(validationEnabled = false)
        val auditContext = AuditContext()
        val bestandsDeelService = BestandsDeelService(SMALL_CHUNK_CONFIG)
        val storageService = mockk<StorageService>().also {
            every { it.uploadFile(any<String>(), any<ByteArray>(), anyNullable()) } answers { secondArg<ByteArray>().size.toLong() }
            every { it.uploadFile(any<String>(), any<InputStream>(), any<Long>(), anyNullable()) } answers { thirdArg<Long>() }
        }
        val service = EnkelvoudigInformatieObjectService(
            storageService = storageService,
            applicationConfig = ApplicationConfig,
            catalogusService = CatalogusService(openZaakConfig),
            auditTrailService = AuditTrailService(auditContext),
            auditContext = auditContext,
            bestandsDeelService = bestandsDeelService,
        )

        // bestandsomvang = 11 bytes > trigger of 10  →  3 chunks: [4, 4, 3]
        val request = TestDataFactory.generateTestDocument().copy(bestandsomvang = 11L)
        val response = service.create(request)

        assertTrue(response.bestandsdelen.isNotEmpty(), "Expected bestandsDelen to be created")
        val firstPart = response.bestandsdelen.first()
        val uuid = UUID.fromString(firstPart.url.substringAfterLast("/"))
        val lock = firstPart.lock
        Pair(uuid, lock)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Happy path
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `PUT bestandsdeel with valid uuid and correct lock returns 200`() = testApplication {
        application { setup() }

        val (uuid, lock) = createBestandsdeelInDb()

        val response = client.put("$BESTANDSDELEN_PATH/$uuid") {
            setBody(
                MultiPartFormDataContent(
                    formData {
                        append(
                            "inhoud",
                            ByteArray(4),
                            Headers.build {
                                append(HttpHeaders.ContentDisposition, "form-data; name=\"inhoud\"; filename=\"chunk\"")
                                append(HttpHeaders.ContentType, ContentType.Application.OctetStream.toString())
                            },
                        )
                        append("lock", lock)
                    },
                ),
            )
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
        assertEquals(uuid.toString(), body["url"]?.jsonPrimitive?.content?.substringAfterLast("/"))
        assertEquals(true, body["voltooid"]?.jsonPrimitive?.content?.toBoolean())
    }

    @Test
    fun `PUT bestandsdeel without inhoud field still succeeds when lock is present`() = testApplication {
        application { setup() }

        val (uuid, lock) = createBestandsdeelInDb()

        // Only send the lock field – inhoud is consumed/discarded anyway
        val response = client.put("$BESTANDSDELEN_PATH/$uuid") {
            setBody(
                MultiPartFormDataContent(
                    formData {
                        append("lock", lock)
                    },
                ),
            )
        }

        assertEquals(HttpStatusCode.OK, response.status)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 400 Bad Request
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `PUT bestandsdeel with invalid UUID returns 400`() = testApplication {
        application { setup() }

        val response = client.put("$BESTANDSDELEN_PATH/not-a-valid-uuid") {
            setBody(
                MultiPartFormDataContent(
                    formData {
                        append("lock", "any-token")
                    },
                ),
            )
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
        val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
        assertTrue(body["detail"]?.jsonPrimitive?.content?.isNotBlank() == true)
    }

    @Test
    fun `PUT bestandsdeel without lock field returns 400`() = testApplication {
        application { setup() }

        val (uuid, _) = createBestandsdeelInDb()

        val response = client.put("$BESTANDSDELEN_PATH/$uuid") {
            setBody(
                MultiPartFormDataContent(
                    formData {
                        // no lock field
                        append(
                            "inhoud",
                            ByteArray(4),
                            Headers.build {
                                append(HttpHeaders.ContentDisposition, "form-data; name=\"inhoud\"; filename=\"chunk\"")
                            },
                        )
                    },
                ),
            )
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
        val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
        assertTrue(body["detail"]?.jsonPrimitive?.content?.isNotBlank() == true)
    }

    @Test
    fun `PUT bestandsdeel with blank lock value returns 400`() = testApplication {
        application { setup() }

        val (uuid, _) = createBestandsdeelInDb()

        val response = client.put("$BESTANDSDELEN_PATH/$uuid") {
            setBody(
                MultiPartFormDataContent(
                    formData {
                        append("lock", "   ")
                    },
                ),
            )
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 403 Forbidden
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `PUT bestandsdeel with wrong lock token returns 403`() = testApplication {
        application { setup() }

        val (uuid, _) = createBestandsdeelInDb()

        val response = client.put("$BESTANDSDELEN_PATH/$uuid") {
            setBody(
                MultiPartFormDataContent(
                    formData {
                        append("lock", "wrong-token-${UUID.randomUUID()}")
                    },
                ),
            )
        }

        assertEquals(HttpStatusCode.Forbidden, response.status)
        val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
        assertTrue(body["detail"]?.jsonPrimitive?.content?.isNotBlank() == true)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 404 Not Found
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `PUT bestandsdeel with unknown UUID returns 404`() = testApplication {
        application { setup() }

        val unknownUuid = UUID.randomUUID()

        val response = client.put("$BESTANDSDELEN_PATH/$unknownUuid") {
            setBody(
                MultiPartFormDataContent(
                    formData {
                        append("lock", "any-token")
                    },
                ),
            )
        }

        assertEquals(HttpStatusCode.NotFound, response.status)
        val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
        assertTrue(body["detail"]?.jsonPrimitive?.content?.isNotBlank() == true)
    }
}
