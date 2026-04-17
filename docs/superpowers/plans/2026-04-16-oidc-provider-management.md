# OIDC Provider Management Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the hardcoded Keycloak OIDC config with a database-backed system where admins can add, edit, delete, and activate OIDC providers via the admin portal.

**Architecture:** A new `oidc_providers` DB table stores provider configs. A `OidcProviderRegistrar` seeds the initial row from the `OIDC_ISSUER` env var on first start. The `AuthenticationModule` queries the DB for the active provider at startup instead of reading from the env var. The admin portal gets a list page and add/edit forms at `/keycloak`.

**Tech Stack:** Kotlin/Ktor, Exposed ORM (UUIDTable pattern), Flyway migrations, H2 for tests, Next.js 16 (App Router), React 19, shadcn/ui, Tailwind v4.

---

## File Map

### New backend files
| Path | Purpose |
|------|---------|
| `src/main/resources/db/migration/V11__OidcProviders.sql` | Create `oidc_providers` table |
| `src/main/resources/db/migration/U11__OidcProviders.sql` | Drop `oidc_providers` table (undo) |
| `src/main/kotlin/entities/OidcProvider.kt` | Exposed table object + entity class |
| `src/main/kotlin/api/models/OidcProviderModels.kt` | `OidcProviderResponse` and `OidcProviderRequest` |
| `src/main/kotlin/api/routes/OidcProviderRoutes.kt` | CRUD routes + activate endpoint |
| `src/main/kotlin/services/OidcProviderRegistrar.kt` | Seeds initial provider from env var on first start |
| `src/test/kotlin/api/routes/OidcProviderRoutesTest.kt` | Route integration tests |

### Modified backend files
| Path | What changes |
|------|-------------|
| `src/main/kotlin/tooling/AllTables.kt` | Add `OidcProviders` to `tables` array |
| `src/main/kotlin/api/DocumentenApiRoutes.kt` | Mount `oidcProviderRoutes()` in the `/admin` block |
| `src/main/kotlin/config/AuthenticationModule.kt` | Query DB for active provider at startup; fall back to env var |
| `src/main/kotlin/Main.kt` | Call `OidcProviderRegistrar.initialise()` after `BlobStorageRegistrar.initialise()` |

### New frontend files
| Path | Purpose |
|------|---------|
| `frontend/admin-portal/lib/api-client.ts` | Authenticated fetch wrapper |
| `frontend/admin-portal/app/keycloak/page.tsx` | Replace placeholder with provider list |
| `frontend/admin-portal/app/keycloak/new/page.tsx` | Add provider form |
| `frontend/admin-portal/app/keycloak/[id]/page.tsx` | Edit provider form |

### Modified frontend files
| Path | What changes |
|------|-------------|
| `frontend/admin-portal/.env.local` | Add `NEXT_PUBLIC_API_URL` |

---

## Task 1: DB Migration

**Files:**
- Create: `src/main/resources/db/migration/V11__OidcProviders.sql`
- Create: `src/main/resources/db/migration/U11__OidcProviders.sql`

- [ ] **Step 1: Create the versioned migration**

```sql
-- src/main/resources/db/migration/V11__OidcProviders.sql
CREATE TABLE oidc_providers
(
    id                                UUID          NOT NULL,
    identifier                        VARCHAR(255)  NOT NULL,
    discovery_endpoint                VARCHAR(1000),
    jwks_endpoint                     VARCHAR(1000),
    authorization_endpoint            VARCHAR(1000),
    token_endpoint                    VARCHAR(1000),
    user_endpoint                     VARCHAR(1000),
    logout_endpoint                   VARCHAR(1000),
    use_basic_auth_for_token_endpoint BOOLEAN       NOT NULL DEFAULT FALSE,
    use_nonce                         BOOLEAN       NOT NULL DEFAULT TRUE,
    nonce_size                        INTEGER       NOT NULL DEFAULT 32,
    state_size                        INTEGER       NOT NULL DEFAULT 32,
    is_active                         BOOLEAN       NOT NULL DEFAULT FALSE,
    created_at                        TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at                        TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_oidc_providers PRIMARY KEY (id),
    CONSTRAINT uq_oidc_providers_identifier UNIQUE (identifier)
);
```

- [ ] **Step 2: Create the undo migration**

```sql
-- src/main/resources/db/migration/U11__OidcProviders.sql
DROP TABLE oidc_providers;
```

- [ ] **Step 3: Commit**

```bash
git add src/main/resources/db/migration/V11__OidcProviders.sql \
        src/main/resources/db/migration/U11__OidcProviders.sql
git commit -m "feat(oidc): add oidc_providers database migration"
```

---

## Task 2: OidcProvider Entity + AllTables

**Files:**
- Create: `src/main/kotlin/entities/OidcProvider.kt`
- Modify: `src/main/kotlin/tooling/AllTables.kt`

- [ ] **Step 1: Create the entity file**

```kotlin
// src/main/kotlin/entities/OidcProvider.kt
// SPDX-License-Identifier: EUPL-1.2
// Copyright (C) 2025-2026 Gemeente Utrecht
package com.baseflow.entities

import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.dao.id.java.UUIDTable
import org.jetbrains.exposed.v1.dao.java.UUIDEntity
import org.jetbrains.exposed.v1.dao.java.UUIDEntityClass
import org.jetbrains.exposed.v1.datetime.CurrentDateTime
import org.jetbrains.exposed.v1.datetime.datetime
import java.util.UUID

object OidcProviders : UUIDTable("oidc_providers") {
    val identifier = varchar("identifier", 255).uniqueIndex()
    val discoveryEndpoint = varchar("discovery_endpoint", 1000).nullable()
    val jwksEndpoint = varchar("jwks_endpoint", 1000).nullable()
    val authorizationEndpoint = varchar("authorization_endpoint", 1000).nullable()
    val tokenEndpoint = varchar("token_endpoint", 1000).nullable()
    val userEndpoint = varchar("user_endpoint", 1000).nullable()
    val logoutEndpoint = varchar("logout_endpoint", 1000).nullable()
    val useBasicAuthForTokenEndpoint = bool("use_basic_auth_for_token_endpoint").default(false)
    val useNonce = bool("use_nonce").default(true)
    val nonceSize = integer("nonce_size").default(32)
    val stateSize = integer("state_size").default(32)
    val isActive = bool("is_active").default(false)
    val createdAt = datetime("created_at").defaultExpression(CurrentDateTime)
    val updatedAt = datetime("updated_at").defaultExpression(CurrentDateTime)
}

class OidcProviderEntity(id: EntityID<UUID>) : UUIDEntity(id) {
    companion object : UUIDEntityClass<OidcProviderEntity>(OidcProviders)

    var identifier by OidcProviders.identifier
    var discoveryEndpoint by OidcProviders.discoveryEndpoint
    var jwksEndpoint by OidcProviders.jwksEndpoint
    var authorizationEndpoint by OidcProviders.authorizationEndpoint
    var tokenEndpoint by OidcProviders.tokenEndpoint
    var userEndpoint by OidcProviders.userEndpoint
    var logoutEndpoint by OidcProviders.logoutEndpoint
    var useBasicAuthForTokenEndpoint by OidcProviders.useBasicAuthForTokenEndpoint
    var useNonce by OidcProviders.useNonce
    var nonceSize by OidcProviders.nonceSize
    var stateSize by OidcProviders.stateSize
    var isActive by OidcProviders.isActive
    var createdAt by OidcProviders.createdAt
    var updatedAt by OidcProviders.updatedAt
}
```

- [ ] **Step 2: Add OidcProviders to AllTables**

In `src/main/kotlin/tooling/AllTables.kt`, add the import and table entry:

```kotlin
// SPDX-License-Identifier: EUPL-1.2
// Copyright (C) 2026 Gemeente Utrecht
package com.baseflow.tooling

import com.baseflow.entities.AuditTrails
import com.baseflow.entities.BestandsDelen
import com.baseflow.entities.BlobStorageRepositories
import com.baseflow.entities.EIORecords
import com.baseflow.entities.EIOVersions
import com.baseflow.entities.OIORecords
import com.baseflow.entities.OidcProviders
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
        AuditTrails,
        BestandsDelen,
        BlobStorageRepositories,
        OidcProviders,
    )

    fun createMissing() {
        transaction {
            MigrationUtils.statementsRequiredForDatabaseMigration(*tables).forEach {
                exec(it)
            }
        }
    }
}
```

- [ ] **Step 3: Format and verify compilation**

```bash
cd /path/to/cg-dmf-poc
./gradlew spotlessApply
./gradlew compileKotlin
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add src/main/kotlin/entities/OidcProvider.kt \
        src/main/kotlin/tooling/AllTables.kt
git commit -m "feat(oidc): add OidcProviderEntity and register in AllTables"
```

---

## Task 3: API Models

**Files:**
- Create: `src/main/kotlin/api/models/OidcProviderModels.kt`

- [ ] **Step 1: Create the models file**

```kotlin
// src/main/kotlin/api/models/OidcProviderModels.kt
// SPDX-License-Identifier: EUPL-1.2
// Copyright (C) 2025-2026 Gemeente Utrecht
package com.baseflow.api.models

import kotlinx.serialization.Serializable

@Serializable
data class OidcProviderResponse(
    val id: String,
    val identifier: String,
    val discoveryEndpoint: String? = null,
    val jwksEndpoint: String? = null,
    val authorizationEndpoint: String? = null,
    val tokenEndpoint: String? = null,
    val userEndpoint: String? = null,
    val logoutEndpoint: String? = null,
    val useBasicAuthForTokenEndpoint: Boolean,
    val useNonce: Boolean,
    val nonceSize: Int,
    val stateSize: Int,
    val isActive: Boolean,
    val createdAt: String,
    val updatedAt: String,
)

@Serializable
data class OidcProviderRequest(
    val identifier: String,
    val discoveryEndpoint: String? = null,
    val jwksEndpoint: String? = null,
    val authorizationEndpoint: String? = null,
    val tokenEndpoint: String? = null,
    val userEndpoint: String? = null,
    val logoutEndpoint: String? = null,
    val useBasicAuthForTokenEndpoint: Boolean = false,
    val useNonce: Boolean = true,
    val nonceSize: Int = 32,
    val stateSize: Int = 32,
)
```

- [ ] **Step 2: Format and compile**

```bash
./gradlew spotlessApply
./gradlew compileKotlin
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add src/main/kotlin/api/models/OidcProviderModels.kt
git commit -m "feat(oidc): add OidcProviderResponse and OidcProviderRequest models"
```

---

## Task 4: Route Tests (TDD — Write Failing Tests First)

**Files:**
- Create: `src/test/kotlin/api/routes/OidcProviderRoutesTest.kt`

- [ ] **Step 1: Write the test file**

```kotlin
// src/test/kotlin/api/routes/OidcProviderRoutesTest.kt
// SPDX-License-Identifier: EUPL-1.2
// Copyright (C) 2026 Gemeente Utrecht
package com.baseflow.api.routes

import com.baseflow.api.models.OidcProviderRequest
import com.baseflow.api.models.OidcProviderResponse
import com.baseflow.entities.OidcProviderEntity
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class OidcProviderRoutesTest : TestBase("oidc_provider_routes") {

    // -------------------------------------------------------------------------
    // Fixtures
    // -------------------------------------------------------------------------

    private fun insertProvider(
        name: String,
        jwksEndpoint: String = "https://example.com/jwks",
        isActive: Boolean = false,
    ): UUID = transaction {
        OidcProviderEntity.new {
            identifier = name
            this.jwksEndpoint = jwksEndpoint
            discoveryEndpoint = "https://example.com/"
            authorizationEndpoint = null
            tokenEndpoint = null
            userEndpoint = null
            logoutEndpoint = null
            useBasicAuthForTokenEndpoint = false
            useNonce = true
            nonceSize = 32
            stateSize = 32
            this.isActive = isActive
        }.id.value
    }

    // -------------------------------------------------------------------------
    // GET /admin/oidc-providers
    // -------------------------------------------------------------------------

    @Test
    fun `GET list returns empty array when no providers exist`() = testApplication {
        application { setup() }

        val response = client.get("/admin/oidc-providers")

        assertEquals(HttpStatusCode.OK, response.status)
        val body = Json.decodeFromString<List<OidcProviderResponse>>(response.bodyAsText())
        assertTrue(body.isEmpty())
    }

    @Test
    fun `GET list returns all providers`() = testApplication {
        application { setup() }
        insertProvider("keycloak")
        insertProvider("auth0")

        val response = client.get("/admin/oidc-providers")

        assertEquals(HttpStatusCode.OK, response.status)
        val body = Json.decodeFromString<List<OidcProviderResponse>>(response.bodyAsText())
        assertEquals(2, body.size)
        val identifiers = body.map { it.identifier }
        assertTrue(identifiers.contains("keycloak"))
        assertTrue(identifiers.contains("auth0"))
    }

    // -------------------------------------------------------------------------
    // POST /admin/oidc-providers
    // -------------------------------------------------------------------------

    @Test
    fun `POST creates provider and returns 201`() = testApplication {
        application { setup() }
        val request = OidcProviderRequest(
            identifier = "keycloak",
            jwksEndpoint = "https://example.com/jwks",
        )

        val response = client.post("/admin/oidc-providers") {
            contentType(ContentType.Application.Json)
            setBody(Json.encodeToString(OidcProviderRequest.serializer(), request))
        }

        assertEquals(HttpStatusCode.Created, response.status)
        val body = Json.decodeFromString<OidcProviderResponse>(response.bodyAsText())
        assertEquals("keycloak", body.identifier)
        assertEquals("https://example.com/jwks", body.jwksEndpoint)
        assertFalse(body.isActive)
    }

    @Test
    fun `POST returns 400 for blank identifier`() = testApplication {
        application { setup() }
        val request = OidcProviderRequest(identifier = "", jwksEndpoint = "https://example.com/jwks")

        val response = client.post("/admin/oidc-providers") {
            contentType(ContentType.Application.Json)
            setBody(Json.encodeToString(OidcProviderRequest.serializer(), request))
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `POST returns 400 when both discoveryEndpoint and jwksEndpoint are null`() = testApplication {
        application { setup() }
        val request = OidcProviderRequest(identifier = "keycloak")

        val response = client.post("/admin/oidc-providers") {
            contentType(ContentType.Application.Json)
            setBody(Json.encodeToString(OidcProviderRequest.serializer(), request))
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `POST returns 400 for duplicate identifier`() = testApplication {
        application { setup() }
        insertProvider("keycloak")
        val request = OidcProviderRequest(identifier = "keycloak", jwksEndpoint = "https://example.com/jwks")

        val response = client.post("/admin/oidc-providers") {
            contentType(ContentType.Application.Json)
            setBody(Json.encodeToString(OidcProviderRequest.serializer(), request))
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    // -------------------------------------------------------------------------
    // GET /admin/oidc-providers/{id}
    // -------------------------------------------------------------------------

    @Test
    fun `GET by id returns provider`() = testApplication {
        application { setup() }
        val id = insertProvider("keycloak", jwksEndpoint = "https://example.com/jwks")

        val response = client.get("/admin/oidc-providers/$id")

        assertEquals(HttpStatusCode.OK, response.status)
        val body = Json.decodeFromString<OidcProviderResponse>(response.bodyAsText())
        assertEquals(id.toString(), body.id)
        assertEquals("keycloak", body.identifier)
        assertEquals("https://example.com/jwks", body.jwksEndpoint)
    }

    @Test
    fun `GET by id returns 404 for unknown id`() = testApplication {
        application { setup() }

        val response = client.get("/admin/oidc-providers/${UUID.randomUUID()}")

        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    @Test
    fun `GET by id returns 400 for malformed UUID`() = testApplication {
        application { setup() }

        val response = client.get("/admin/oidc-providers/not-a-uuid")

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    // -------------------------------------------------------------------------
    // PUT /admin/oidc-providers/{id}
    // -------------------------------------------------------------------------

    @Test
    fun `PUT updates provider and returns 200`() = testApplication {
        application { setup() }
        val id = insertProvider("keycloak")
        val request = OidcProviderRequest(
            identifier = "keycloak-updated",
            jwksEndpoint = "https://new.example.com/jwks",
        )

        val response = client.put("/admin/oidc-providers/$id") {
            contentType(ContentType.Application.Json)
            setBody(Json.encodeToString(OidcProviderRequest.serializer(), request))
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val body = Json.decodeFromString<OidcProviderResponse>(response.bodyAsText())
        assertEquals("keycloak-updated", body.identifier)
        assertEquals("https://new.example.com/jwks", body.jwksEndpoint)
    }

    @Test
    fun `PUT returns 404 for unknown id`() = testApplication {
        application { setup() }
        val request = OidcProviderRequest(identifier = "keycloak", jwksEndpoint = "https://example.com/jwks")

        val response = client.put("/admin/oidc-providers/${UUID.randomUUID()}") {
            contentType(ContentType.Application.Json)
            setBody(Json.encodeToString(OidcProviderRequest.serializer(), request))
        }

        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    @Test
    fun `PUT returns 400 for blank identifier`() = testApplication {
        application { setup() }
        val id = insertProvider("keycloak")
        val request = OidcProviderRequest(identifier = "", jwksEndpoint = "https://example.com/jwks")

        val response = client.put("/admin/oidc-providers/$id") {
            contentType(ContentType.Application.Json)
            setBody(Json.encodeToString(OidcProviderRequest.serializer(), request))
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    // -------------------------------------------------------------------------
    // DELETE /admin/oidc-providers/{id}
    // -------------------------------------------------------------------------

    @Test
    fun `DELETE removes inactive provider and returns 204`() = testApplication {
        application { setup() }
        val id = insertProvider("keycloak", isActive = false)

        val response = client.delete("/admin/oidc-providers/$id")

        assertEquals(HttpStatusCode.NoContent, response.status)
        val count = transaction { OidcProviderEntity.all().count() }
        assertEquals(0L, count)
    }

    @Test
    fun `DELETE returns 400 when deleting active provider`() = testApplication {
        application { setup() }
        val id = insertProvider("keycloak", isActive = true)

        val response = client.delete("/admin/oidc-providers/$id")

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `DELETE returns 404 for unknown id`() = testApplication {
        application { setup() }

        val response = client.delete("/admin/oidc-providers/${UUID.randomUUID()}")

        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    // -------------------------------------------------------------------------
    // PUT /admin/oidc-providers/{id}/activate
    // -------------------------------------------------------------------------

    @Test
    fun `activate sets provider as active and returns 200`() = testApplication {
        application { setup() }
        val id = insertProvider("keycloak", isActive = false)

        val response = client.put("/admin/oidc-providers/$id/activate")

        assertEquals(HttpStatusCode.OK, response.status)
        val body = Json.decodeFromString<OidcProviderResponse>(response.bodyAsText())
        assertTrue(body.isActive)
    }

    @Test
    fun `activate deactivates all other providers`() = testApplication {
        application { setup() }
        val id1 = insertProvider("keycloak", isActive = true)
        val id2 = insertProvider("auth0", isActive = false)

        client.put("/admin/oidc-providers/$id2/activate")

        val keycloak = transaction { OidcProviderEntity.findById(id1) }!!
        assertFalse(keycloak.isActive)
    }

    @Test
    fun `activate returns 404 for unknown id`() = testApplication {
        application { setup() }

        val response = client.put("/admin/oidc-providers/${UUID.randomUUID()}/activate")

        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    // -------------------------------------------------------------------------
    // Response shape
    // -------------------------------------------------------------------------

    @Test
    fun `response includes all expected fields`() = testApplication {
        application { setup() }
        val id = insertProvider("keycloak", jwksEndpoint = "https://example.com/jwks")

        val response = client.get("/admin/oidc-providers/$id")

        assertEquals(HttpStatusCode.OK, response.status)
        val body = Json.decodeFromString<OidcProviderResponse>(response.bodyAsText())
        assertNotNull(body.id)
        assertNotNull(body.identifier)
        assertNotNull(body.createdAt)
        assertNotNull(body.updatedAt)
        assertEquals(32, body.nonceSize)
        assertEquals(32, body.stateSize)
        assertTrue(body.useNonce)
        assertFalse(body.useBasicAuthForTokenEndpoint)
    }
}
```

- [ ] **Step 2: Run tests — confirm they fail**

```bash
./gradlew test --tests "com.baseflow.api.routes.OidcProviderRoutesTest"
```

Expected: tests FAIL (routes not yet defined — Ktor returns 404 or 405 for unknown paths). The test output will show assertion errors like `expected: <200 OK> but was: <404 Not Found>`.

---

## Task 5: Route Implementation + Mount

**Files:**
- Create: `src/main/kotlin/api/routes/OidcProviderRoutes.kt`
- Modify: `src/main/kotlin/api/DocumentenApiRoutes.kt`

- [ ] **Step 1: Create the routes file**

```kotlin
// src/main/kotlin/api/routes/OidcProviderRoutes.kt
// SPDX-License-Identifier: EUPL-1.2
// Copyright (C) 2025-2026 Gemeente Utrecht
package com.baseflow.api.routes

import com.baseflow.api.models.OidcProviderRequest
import com.baseflow.api.models.OidcProviderResponse
import com.baseflow.api.models.badRequest
import com.baseflow.api.models.notFound
import com.baseflow.api.models.respondProblem
import com.baseflow.entities.OidcProviderEntity
import com.baseflow.entities.OidcProviders
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.util.UUID

/**
 * Admin routes for managing OIDC provider configurations.
 *
 * Mounted at `/admin/oidc-providers`.
 *
 * Endpoints:
 * - `GET  /`               — list all providers
 * - `POST /`               — create a new provider
 * - `GET  /{id}`           — get a single provider by UUID
 * - `PUT  /{id}`           — update a provider
 * - `DELETE /{id}`         — delete an inactive provider
 * - `PUT  /{id}/activate`  — set as the active provider (deactivates all others)
 */
fun Route.oidcProviderRoutes() {
    route("/oidc-providers") {
        // GET /admin/oidc-providers
        get {
            val providers = transaction {
                OidcProviderEntity.all().map { it.toResponse() }
            }
            call.respond(providers)
        }

        // POST /admin/oidc-providers
        post {
            val body = runCatching { call.receive<OidcProviderRequest>() }.getOrNull()
                ?: return@post call.respondProblem(
                    HttpStatusCode.BadRequest,
                    badRequest("Request body must be valid JSON.", call.request.path()),
                )

            if (body.identifier.isBlank()) {
                return@post call.respondProblem(
                    HttpStatusCode.BadRequest,
                    badRequest("'identifier' must not be blank.", call.request.path()),
                )
            }

            if (body.discoveryEndpoint == null && body.jwksEndpoint == null) {
                return@post call.respondProblem(
                    HttpStatusCode.BadRequest,
                    badRequest(
                        "At least one of 'discoveryEndpoint' or 'jwksEndpoint' must be provided.",
                        call.request.path(),
                    ),
                )
            }

            val existing = transaction {
                OidcProviderEntity.all().firstOrNull { it.identifier == body.identifier }
            }
            if (existing != null) {
                return@post call.respondProblem(
                    HttpStatusCode.BadRequest,
                    badRequest(
                        "An OIDC provider with identifier '${body.identifier}' already exists.",
                        call.request.path(),
                    ),
                )
            }

            val entity = transaction {
                OidcProviderEntity.new {
                    identifier = body.identifier
                    discoveryEndpoint = body.discoveryEndpoint
                    jwksEndpoint = body.jwksEndpoint
                    authorizationEndpoint = body.authorizationEndpoint
                    tokenEndpoint = body.tokenEndpoint
                    userEndpoint = body.userEndpoint
                    logoutEndpoint = body.logoutEndpoint
                    useBasicAuthForTokenEndpoint = body.useBasicAuthForTokenEndpoint
                    useNonce = body.useNonce
                    nonceSize = body.nonceSize
                    stateSize = body.stateSize
                    isActive = false
                }.toResponse()
            }

            call.respond(HttpStatusCode.Created, entity)
        }

        // GET /admin/oidc-providers/{id}
        get("/{id}") {
            val id = call.parameters["id"]
                ?.let { runCatching { UUID.fromString(it) }.getOrNull() }
                ?: return@get call.respondProblem(
                    HttpStatusCode.BadRequest,
                    badRequest("Invalid UUID.", call.request.path()),
                )

            val entity = transaction {
                OidcProviderEntity.findById(id)?.toResponse()
            } ?: return@get call.respondProblem(
                HttpStatusCode.NotFound,
                notFound("OIDC provider with id '$id' not found.", call.request.path()),
            )

            call.respond(entity)
        }

        // PUT /admin/oidc-providers/{id}
        put("/{id}") {
            val id = call.parameters["id"]
                ?.let { runCatching { UUID.fromString(it) }.getOrNull() }
                ?: return@put call.respondProblem(
                    HttpStatusCode.BadRequest,
                    badRequest("Invalid UUID.", call.request.path()),
                )

            val body = runCatching { call.receive<OidcProviderRequest>() }.getOrNull()
                ?: return@put call.respondProblem(
                    HttpStatusCode.BadRequest,
                    badRequest("Request body must be valid JSON.", call.request.path()),
                )

            if (body.identifier.isBlank()) {
                return@put call.respondProblem(
                    HttpStatusCode.BadRequest,
                    badRequest("'identifier' must not be blank.", call.request.path()),
                )
            }

            if (body.discoveryEndpoint == null && body.jwksEndpoint == null) {
                return@put call.respondProblem(
                    HttpStatusCode.BadRequest,
                    badRequest(
                        "At least one of 'discoveryEndpoint' or 'jwksEndpoint' must be provided.",
                        call.request.path(),
                    ),
                )
            }

            val updated = transaction {
                val entity = OidcProviderEntity.findById(id)
                    ?: return@transaction null

                entity.identifier = body.identifier
                entity.discoveryEndpoint = body.discoveryEndpoint
                entity.jwksEndpoint = body.jwksEndpoint
                entity.authorizationEndpoint = body.authorizationEndpoint
                entity.tokenEndpoint = body.tokenEndpoint
                entity.userEndpoint = body.userEndpoint
                entity.logoutEndpoint = body.logoutEndpoint
                entity.useBasicAuthForTokenEndpoint = body.useBasicAuthForTokenEndpoint
                entity.useNonce = body.useNonce
                entity.nonceSize = body.nonceSize
                entity.stateSize = body.stateSize
                entity.toResponse()
            } ?: return@put call.respondProblem(
                HttpStatusCode.NotFound,
                notFound("OIDC provider with id '$id' not found.", call.request.path()),
            )

            call.respond(HttpStatusCode.OK, updated)
        }

        // DELETE /admin/oidc-providers/{id}
        delete("/{id}") {
            val id = call.parameters["id"]
                ?.let { runCatching { UUID.fromString(it) }.getOrNull() }
                ?: return@delete call.respondProblem(
                    HttpStatusCode.BadRequest,
                    badRequest("Invalid UUID.", call.request.path()),
                )

            val entity = transaction {
                OidcProviderEntity.findById(id)
            } ?: return@delete call.respondProblem(
                HttpStatusCode.NotFound,
                notFound("OIDC provider with id '$id' not found.", call.request.path()),
            )

            if (entity.isActive) {
                return@delete call.respondProblem(
                    HttpStatusCode.BadRequest,
                    badRequest(
                        "Cannot delete the active OIDC provider. Activate a different provider first.",
                        call.request.path(),
                    ),
                )
            }

            transaction { entity.delete() }
            call.respond(HttpStatusCode.NoContent)
        }

        // PUT /admin/oidc-providers/{id}/activate
        put("/{id}/activate") {
            val id = call.parameters["id"]
                ?.let { runCatching { UUID.fromString(it) }.getOrNull() }
                ?: return@put call.respondProblem(
                    HttpStatusCode.BadRequest,
                    badRequest("Invalid UUID.", call.request.path()),
                )

            val updated = transaction {
                OidcProviderEntity.findById(id) ?: return@transaction null
                OidcProviderEntity.all().forEach { it.isActive = false }
                val target = OidcProviderEntity.findById(id)!!
                target.isActive = true
                target.toResponse()
            } ?: return@put call.respondProblem(
                HttpStatusCode.NotFound,
                notFound("OIDC provider with id '$id' not found.", call.request.path()),
            )

            call.respond(HttpStatusCode.OK, updated)
        }
    }
}

private fun OidcProviderEntity.toResponse() = OidcProviderResponse(
    id = id.value.toString(),
    identifier = identifier,
    discoveryEndpoint = discoveryEndpoint,
    jwksEndpoint = jwksEndpoint,
    authorizationEndpoint = authorizationEndpoint,
    tokenEndpoint = tokenEndpoint,
    userEndpoint = userEndpoint,
    logoutEndpoint = logoutEndpoint,
    useBasicAuthForTokenEndpoint = useBasicAuthForTokenEndpoint,
    useNonce = useNonce,
    nonceSize = nonceSize,
    stateSize = stateSize,
    isActive = isActive,
    createdAt = createdAt.toString(),
    updatedAt = updatedAt.toString(),
)
```

- [ ] **Step 2: Mount the routes in DocumentenApiRoutes.kt**

In `src/main/kotlin/api/DocumentenApiRoutes.kt`, add the import and add `oidcProviderRoutes()` calls in both the authenticated and unauthenticated blocks:

```kotlin
// Add this import at the top:
import com.baseflow.api.routes.oidcProviderRoutes

// In the routing block, both if/else branches:
// BEFORE (both branches):
route("/admin") {
    blobStorageRepositoryRoutes()
}

// AFTER (both branches):
route("/admin") {
    blobStorageRepositoryRoutes()
    oidcProviderRoutes()
}
```

The full updated routing block looks like:

```kotlin
routing {
    if (useAuthentication) {
        authenticate("auth-jwt", "auth-zgw", strategy = AuthenticationStrategy.FirstSuccessful) {
            documentenApiRoutes()
            route("/admin") {
                blobStorageRepositoryRoutes()
                oidcProviderRoutes()
            }
        }
    } else {
        documentenApiRoutes()
        route("/admin") {
            blobStorageRepositoryRoutes()
            oidcProviderRoutes()
        }
    }
}
```

- [ ] **Step 3: Format**

```bash
./gradlew spotlessApply
```

- [ ] **Step 4: Run the tests — confirm they pass**

```bash
./gradlew test --tests "com.baseflow.api.routes.OidcProviderRoutesTest"
```

Expected: BUILD SUCCESSFUL, all tests PASS.

- [ ] **Step 5: Run all tests to catch regressions**

```bash
./gradlew test
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: Commit**

```bash
git add src/main/kotlin/api/routes/OidcProviderRoutes.kt \
        src/main/kotlin/api/DocumentenApiRoutes.kt \
        src/test/kotlin/api/routes/OidcProviderRoutesTest.kt
git commit -m "feat(oidc): implement OIDC provider admin routes with tests"
```

---

## Task 6: OidcProviderRegistrar (Startup Seeding)

Seeds an initial provider from the `OIDC_ISSUER` env var when the DB has no providers yet. This preserves backwards compatibility for existing deployments after the migration runs.

**Files:**
- Create: `src/main/kotlin/services/OidcProviderRegistrar.kt`
- Modify: `src/main/kotlin/Main.kt`

- [ ] **Step 1: Create the registrar**

```kotlin
// src/main/kotlin/services/OidcProviderRegistrar.kt
// SPDX-License-Identifier: EUPL-1.2
// Copyright (C) 2025-2026 Gemeente Utrecht
package com.baseflow.services

import com.baseflow.config.AuthenticationConfig
import com.baseflow.entities.OidcProviderEntity
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.slf4j.LoggerFactory

object OidcProviderRegistrar {
    private val logger = LoggerFactory.getLogger(OidcProviderRegistrar::class.java)

    /**
     * Seeds the initial OIDC provider from the OIDC_ISSUER environment variable
     * if no providers exist in the database yet.
     *
     * Derives standard Keycloak endpoint URLs from the issuer base URL.
     * Called once at application startup, after Flyway migrations.
     */
    fun initialise() {
        val count = transaction { OidcProviderEntity.all().count() }
        if (count > 0L) {
            logger.info("OidcProviderRegistrar: {} provider(s) already configured, skipping seed.", count)
            return
        }

        val issuer = AuthenticationConfig.issuer
        logger.info("OidcProviderRegistrar: no providers found, seeding from OIDC_ISSUER={}", issuer)

        transaction {
            OidcProviderEntity.new {
                identifier = "keycloak"
                discoveryEndpoint = issuer
                jwksEndpoint = "$issuer/protocol/openid-connect/certs"
                authorizationEndpoint = "$issuer/protocol/openid-connect/auth"
                tokenEndpoint = "$issuer/protocol/openid-connect/token"
                userEndpoint = "$issuer/protocol/openid-connect/userinfo"
                logoutEndpoint = "$issuer/protocol/openid-connect/logout"
                useBasicAuthForTokenEndpoint = false
                useNonce = true
                nonceSize = 32
                stateSize = 32
                isActive = true
            }
        }

        logger.info("OidcProviderRegistrar: seeded initial provider 'keycloak' from OIDC_ISSUER.")
    }
}
```

- [ ] **Step 2: Call it in Main.kt**

In `src/main/kotlin/Main.kt`, add the import and a call after `BlobStorageRegistrar.initialise()`:

```kotlin
// Add import (with other service imports):
import com.baseflow.services.OidcProviderRegistrar

// In main(), after BlobStorageRegistrar.initialise():
BlobStorageRegistrar.initialise()
OidcProviderRegistrar.initialise()   // ← add this line
```

- [ ] **Step 3: Format and build**

```bash
./gradlew spotlessApply
./gradlew build
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add src/main/kotlin/services/OidcProviderRegistrar.kt \
        src/main/kotlin/Main.kt
git commit -m "feat(oidc): seed initial OIDC provider from OIDC_ISSUER on first startup"
```

---

## Task 7: Update AuthenticationModule to Read from DB

At startup, query the DB for the active provider and use its `jwksEndpoint` and `discoveryEndpoint` (as issuer) instead of the hardcoded env var derivation.

**Files:**
- Modify: `src/main/kotlin/config/AuthenticationModule.kt`

- [ ] **Step 1: Update authenticationModule()**

Replace the top of `authenticationModule()` — from `val issuer = AuthenticationConfig.issuer` through the `JwkProviderBuilder` call — with the DB-backed version. The rest of the function (JWT validation, OpenAPI registration) remains unchanged.

The full updated function:

```kotlin
// SPDX-License-Identifier: EUPL-1.2
// Copyright (C) 2025-2026 Gemeente Utrecht
package com.baseflow.config

import com.auth0.jwk.JwkProviderBuilder
import com.auth0.jwt.JWT
import com.auth0.jwt.interfaces.JWTVerifier
import com.baseflow.entities.OidcProviderEntity
import io.ktor.http.HttpStatusCode
import io.ktor.http.auth.parseAuthorizationHeader
import io.ktor.openapi.HttpSecurityScheme
import io.ktor.openapi.OAuth2SecurityScheme
import io.ktor.openapi.OAuthFlow
import io.ktor.openapi.OAuthFlows
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.response.respondText
import io.ktor.server.routing.openapi.registerSecurityScheme
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.slf4j.LoggerFactory
import java.net.URI
import java.util.concurrent.TimeUnit

fun Application.authenticationModule() {
    val logger = LoggerFactory.getLogger("AuthenticationModule")
    val zgwAllowedClientIds = AuthenticationConfig.zgwAllowedClientIds

    // Load the active OIDC provider from DB; fall back to OIDC_ISSUER env var
    val activeProvider = transaction {
        OidcProviderEntity.all().firstOrNull { it.isActive }
    }

    val issuer: String
    val jwksUrl: String

    if (activeProvider != null) {
        issuer = activeProvider.discoveryEndpoint ?: AuthenticationConfig.issuer
        jwksUrl = activeProvider.jwksEndpoint ?: "$issuer/protocol/openid-connect/certs"
        logger.info("AuthenticationModule: using DB-configured provider '{}', jwksUrl={}", activeProvider.identifier, jwksUrl)
    } else {
        issuer = AuthenticationConfig.issuer
        jwksUrl = "$issuer/protocol/openid-connect/certs"
        logger.warn("AuthenticationModule: no active OIDC provider in DB; falling back to OIDC_ISSUER={}", issuer)
    }

    // Configure JWK provider to fetch signing keys from the active provider's JWKS endpoint
    val jwkProvider = JwkProviderBuilder(URI(jwksUrl).toURL())
        .cached(10, 24, TimeUnit.HOURS)
        .rateLimited(10, 1, TimeUnit.MINUTES)
        .build()

    install(Authentication) {
        jwt("auth-jwt") {
            authHeader { call ->
                val header = call.request.headers["Authorization"]
                logger.info("Raw Authorization header: {}", header)
                header?.let { parseAuthorizationHeader(it) }
            }

            verifier(jwkProvider, issuer) {
                acceptLeeway(3)
            }

            validate { credential ->
                val token = credential.payload
                logger.info(
                    "JWT token received - subject: {}, issuer: {}, claims: {}",
                    token.subject,
                    token.issuer,
                    token.claims.keys,
                )
                if (credential.payload.getClaim("username").asString() != "" ||
                    credential.payload.getClaim("user_id").asString() != ""
                ) {
                    JWTPrincipal(credential.payload)
                } else {
                    null
                }
            }

            challenge { _, _ ->
                call.respondText(
                    text = "Unauthorized",
                    status = HttpStatusCode.Unauthorized,
                )
            }
        }

        // ZGW-style JWT authentication (used by GZAC/Valtimo, Open Zaak, etc.)
        // These tokens are HS256-signed but we don't have access to the shared secret,
        // so we skip signature verification and only validate the client_id claim.
        jwt("auth-zgw") {
            authHeader { call ->
                val header = call.request.headers["Authorization"]
                logger.info("[ZGW] Raw Authorization header: {}", header)
                // <!-- FIXME unsafe -->
                if (header?.trim() == "Bearer bypass") {
                    logger.warn(
                        "[ZGW] UNSAFE BYPASS AUTH: request authenticated via hardcoded bypass token. " +
                            "This must not be used in production.",
                    )
                    return@authHeader parseAuthorizationHeader(
                        "Bearer eyJhbGciOiJub25lIiwidHlwIjoiSldUIn0.eyJieXBhc3MiOnRydWV9.",
                    )
                }
                header?.let { parseAuthorizationHeader(it) }
            }

            verifier(
                object : JWTVerifier {
                    override fun verify(token: String): com.auth0.jwt.interfaces.DecodedJWT = JWT.decode(token)
                    override fun verify(jwt: com.auth0.jwt.interfaces.DecodedJWT): com.auth0.jwt.interfaces.DecodedJWT = jwt
                },
            )

            validate { credential ->
                val token = credential.payload
                // <!-- FIXME unsafe -->
                if (token.getClaim("bypass").asBoolean() == true) {
                    logger.warn(
                        "[ZGW] UNSAFE BYPASS AUTH: bypass principal granted. " +
                            "This must not be used in production.",
                    )
                    return@validate JWTPrincipal(token)
                }
                val clientId = token.getClaim("client_id").asString()
                logger.info(
                    "[ZGW] JWT token received - issuer: {}, client_id: {}, claims: {}",
                    token.issuer,
                    clientId,
                    token.claims.keys,
                )
                if (clientId in zgwAllowedClientIds) {
                    JWTPrincipal(credential.payload)
                } else {
                    logger.warn("[ZGW] Rejected token with unknown client_id: {}", clientId)
                    null
                }
            }

            challenge { _, _ ->
                call.respondText(
                    text = "Unauthorized",
                    status = HttpStatusCode.Unauthorized,
                )
            }
        }
    }

    registerSecurityScheme(
        providerName = "auth-jwt",
        securityScheme = OAuth2SecurityScheme(
            description = "OIDC login via Keycloak (Authorization Code + PKCE). " +
                "Klik 'Authorize', log in met uw Keycloak-account en het token wordt automatisch gebruikt.",
            flows = OAuthFlows(
                authorizationCode = OAuthFlow(
                    authorizationUrl = "$issuer/protocol/openid-connect/auth",
                    tokenUrl = "$issuer/protocol/openid-connect/token",
                    refreshUrl = "$issuer/protocol/openid-connect/token",
                    scopes = mapOf(
                        "openid" to "OpenID Connect scope",
                        "profile" to "Profiel informatie",
                        "email" to "E-mailadres",
                    ),
                ),
            ),
        ),
    )
    // <!-- FIXME unsafe -->
    registerSecurityScheme(
        providerName = "auth-zgw",
        securityScheme = HttpSecurityScheme(
            scheme = "bearer",
            bearerFormat = "JWT",
            description = "ZGW-stijl HS256 JWT (GZAC/OpenZaak/Valtimo). " +
                "Plak een token gegenereerd via de ZGW token-tool. " +
                "Het token wordt niet op handtekening gecontroleerd; alleen client_id wordt gevalideerd.\n\n" +
                "⚠️ UNSAFE BYPASS: typ de letterlijke waarde `bypass` om alle JWT-validatie over te slaan. " +
                "Uitsluitend bedoeld voor lokale ontwikkeling en testen. NOOIT gebruiken in productie.",
        ),
    )
}
```

- [ ] **Step 2: Format and run all tests**

```bash
./gradlew spotlessApply
./gradlew test
```

Expected: BUILD SUCCESSFUL, all tests pass.

- [ ] **Step 3: Commit**

```bash
git add src/main/kotlin/config/AuthenticationModule.kt
git commit -m "feat(oidc): read active OIDC provider JWKS from database at startup"
```

---

## Task 8: Frontend — Env Var + API Client

**Files:**
- Modify: `frontend/admin-portal/.env.local`
- Create: `frontend/admin-portal/lib/api-client.ts`

- [ ] **Step 1: Add backend URL to .env.local**

Add `NEXT_PUBLIC_API_URL` to `frontend/admin-portal/.env.local`:

```
NEXT_PUBLIC_KEYCLOAK_URL=https://auth.gzac.baseflow.com
NEXT_PUBLIC_KEYCLOAK_REALM=valtimo
NEXT_PUBLIC_KEYCLOAK_CLIENT_ID=dmf-dashboard
NEXT_PUBLIC_API_URL=http://localhost:8080
```

Adjust the URL to match your actual backend address.

- [ ] **Step 2: Create the API client**

```typescript
// frontend/admin-portal/lib/api-client.ts
const API_BASE = process.env.NEXT_PUBLIC_API_URL ?? ""

export async function apiFetch(
  path: string,
  token: string,
  options?: RequestInit,
): Promise<Response> {
  return fetch(`${API_BASE}${path}`, {
    ...options,
    headers: {
      "Content-Type": "application/json",
      Authorization: `Bearer ${token}`,
      ...options?.headers,
    },
  })
}
```

- [ ] **Step 3: Format and typecheck**

```bash
cd frontend/admin-portal
npm run format
npm run typecheck
```

Expected: no errors.

- [ ] **Step 4: Commit**

```bash
git add frontend/admin-portal/.env.local \
        frontend/admin-portal/lib/api-client.ts
git commit -m "feat(oidc): add API client utility and NEXT_PUBLIC_API_URL env var"
```

---

## Task 9: Install shadcn Components

- [ ] **Step 1: Install input, label, and checkbox**

```bash
cd frontend/admin-portal
npx shadcn@latest add input
npx shadcn@latest add label
npx shadcn@latest add checkbox
```

Each command copies a component into `components/ui/`. Confirm the files exist:
- `components/ui/input.tsx`
- `components/ui/label.tsx`
- `components/ui/checkbox.tsx`

- [ ] **Step 2: Format**

```bash
npm run format
```

- [ ] **Step 3: Commit**

```bash
git add components/ui/input.tsx \
        components/ui/label.tsx \
        components/ui/checkbox.tsx
git commit -m "feat(oidc): install shadcn input, label, and checkbox components"
```

---

## Task 10: OIDC Providers List Page

Replaces the placeholder at `app/keycloak/page.tsx` with a table listing all providers, with actions to add, edit, delete, and activate.

**Files:**
- Modify: `frontend/admin-portal/app/keycloak/page.tsx`

- [ ] **Step 1: Replace the page**

```tsx
// frontend/admin-portal/app/keycloak/page.tsx
"use client"

import { useEffect, useState } from "react"
import Link from "next/link"
import { useAuth } from "@/contexts/auth-context"
import { apiFetch } from "@/lib/api-client"
import { Button } from "@/components/ui/button"

interface OidcProvider {
  id: string
  identifier: string
  discoveryEndpoint: string | null
  jwksEndpoint: string | null
  isActive: boolean
}

export default function Page() {
  const { authenticated, keycloak } = useAuth()
  const [providers, setProviders] = useState<OidcProvider[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  async function load() {
    if (!keycloak.token) return
    setLoading(true)
    setError(null)
    try {
      const res = await apiFetch("/admin/oidc-providers", keycloak.token)
      if (!res.ok) throw new Error(`Failed to load providers (${res.status})`)
      setProviders(await res.json())
    } catch (e) {
      setError(e instanceof Error ? e.message : "Unknown error")
    } finally {
      setLoading(false)
    }
  }

  async function handleDelete(id: string) {
    if (!keycloak.token) return
    if (!confirm("Delete this provider?")) return
    const res = await apiFetch(`/admin/oidc-providers/${id}`, keycloak.token, {
      method: "DELETE",
    })
    if (res.ok || res.status === 204) {
      await load()
    } else {
      const body = await res.json().catch(() => ({}))
      alert(body.detail ?? `Delete failed (${res.status})`)
    }
  }

  async function handleActivate(id: string) {
    if (!keycloak.token) return
    const res = await apiFetch(`/admin/oidc-providers/${id}/activate`, keycloak.token, {
      method: "PUT",
    })
    if (res.ok) {
      await load()
    } else {
      const body = await res.json().catch(() => ({}))
      alert(body.detail ?? `Activate failed (${res.status})`)
    }
  }

  useEffect(() => {
    if (authenticated) load()
  }, [authenticated])

  if (!authenticated) {
    return (
      <div className="flex min-h-svh p-6">
        <div className="flex max-w-md flex-col gap-4 text-sm">
          <p>Sign in to manage OIDC providers.</p>
          <Button onClick={() => keycloak.login()}>Sign in</Button>
        </div>
      </div>
    )
  }

  return (
    <div className="flex min-h-svh flex-col p-6">
      <div className="mb-4 flex items-center justify-between">
        <h1 className="text-base font-medium">OIDC Providers</h1>
        <Button asChild size="sm">
          <Link href="/keycloak/new">Add provider</Link>
        </Button>
      </div>

      {loading && <p className="text-sm text-muted-foreground">Loading…</p>}
      {error && <p className="text-sm text-destructive">{error}</p>}

      {!loading && !error && providers.length === 0 && (
        <p className="text-sm text-muted-foreground">No providers configured yet.</p>
      )}

      {!loading && !error && providers.length > 0 && (
        <div className="overflow-x-auto">
          <table className="w-full text-sm">
            <thead>
              <tr className="border-b text-left text-muted-foreground">
                <th className="pb-2 pr-4 font-medium">Identifier</th>
                <th className="pb-2 pr-4 font-medium">Discovery endpoint</th>
                <th className="pb-2 pr-4 font-medium">Status</th>
                <th className="pb-2 font-medium">Actions</th>
              </tr>
            </thead>
            <tbody>
              {providers.map((p) => (
                <tr key={p.id} className="border-b last:border-0">
                  <td className="py-2 pr-4 font-medium">{p.identifier}</td>
                  <td className="py-2 pr-4 text-muted-foreground">
                    {p.discoveryEndpoint ?? p.jwksEndpoint ?? "—"}
                  </td>
                  <td className="py-2 pr-4">
                    {p.isActive ? (
                      <span className="rounded-full bg-primary px-2 py-0.5 text-xs text-primary-foreground">
                        Active
                      </span>
                    ) : (
                      <span className="rounded-full bg-muted px-2 py-0.5 text-xs text-muted-foreground">
                        Inactive
                      </span>
                    )}
                  </td>
                  <td className="py-2">
                    <div className="flex gap-2">
                      <Button asChild size="xs" variant="outline">
                        <Link href={`/keycloak/${p.id}`}>Edit</Link>
                      </Button>
                      {!p.isActive && (
                        <Button
                          size="xs"
                          variant="outline"
                          onClick={() => handleActivate(p.id)}
                        >
                          Set active
                        </Button>
                      )}
                      {!p.isActive && (
                        <Button
                          size="xs"
                          variant="destructive"
                          onClick={() => handleDelete(p.id)}
                        >
                          Delete
                        </Button>
                      )}
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  )
}
```

- [ ] **Step 2: Format and typecheck**

```bash
cd frontend/admin-portal
npm run format
npm run typecheck
```

Expected: no errors.

- [ ] **Step 3: Commit**

```bash
git add frontend/admin-portal/app/keycloak/page.tsx
git commit -m "feat(oidc): add OIDC providers list page"
```

---

## Task 11: Add Provider Form

**Files:**
- Create: `frontend/admin-portal/app/keycloak/new/page.tsx`

- [ ] **Step 1: Create the directory and file**

```bash
mkdir -p frontend/admin-portal/app/keycloak/new
```

```tsx
// frontend/admin-portal/app/keycloak/new/page.tsx
"use client"

import { useState } from "react"
import { useRouter } from "next/navigation"
import { useAuth } from "@/contexts/auth-context"
import { apiFetch } from "@/lib/api-client"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import { Checkbox } from "@/components/ui/checkbox"

export default function Page() {
  const { keycloak } = useAuth()
  const router = useRouter()
  const [error, setError] = useState<string | null>(null)
  const [saving, setSaving] = useState(false)

  const [identifier, setIdentifier] = useState("")
  const [discoveryEndpoint, setDiscoveryEndpoint] = useState("")
  const [jwksEndpoint, setJwksEndpoint] = useState("")
  const [authorizationEndpoint, setAuthorizationEndpoint] = useState("")
  const [tokenEndpoint, setTokenEndpoint] = useState("")
  const [userEndpoint, setUserEndpoint] = useState("")
  const [logoutEndpoint, setLogoutEndpoint] = useState("")
  const [useBasicAuth, setUseBasicAuth] = useState(false)
  const [useNonce, setUseNonce] = useState(true)
  const [nonceSize, setNonceSize] = useState(32)
  const [stateSize, setStateSize] = useState(32)

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault()
    if (!keycloak.token) return
    setSaving(true)
    setError(null)

    const body = {
      identifier,
      discoveryEndpoint: discoveryEndpoint || null,
      jwksEndpoint: jwksEndpoint || null,
      authorizationEndpoint: authorizationEndpoint || null,
      tokenEndpoint: tokenEndpoint || null,
      userEndpoint: userEndpoint || null,
      logoutEndpoint: logoutEndpoint || null,
      useBasicAuthForTokenEndpoint: useBasicAuth,
      useNonce,
      nonceSize,
      stateSize,
    }

    const res = await apiFetch("/admin/oidc-providers", keycloak.token, {
      method: "POST",
      body: JSON.stringify(body),
    })

    if (res.ok || res.status === 201) {
      router.push("/keycloak")
    } else {
      const data = await res.json().catch(() => ({}))
      setError(data.detail ?? `Save failed (${res.status})`)
      setSaving(false)
    }
  }

  return (
    <div className="flex min-h-svh p-6">
      <div className="w-full max-w-lg">
        <h1 className="mb-6 text-base font-medium">Add OIDC Provider</h1>
        <form onSubmit={handleSubmit} className="flex flex-col gap-4">
          <div className="flex flex-col gap-1.5">
            <Label htmlFor="identifier">Identifier</Label>
            <Input
              id="identifier"
              value={identifier}
              onChange={(e) => setIdentifier(e.target.value)}
              required
            />
          </div>

          <div className="flex flex-col gap-1.5">
            <Label htmlFor="discoveryEndpoint">Discovery endpoint</Label>
            <Input
              id="discoveryEndpoint"
              type="url"
              value={discoveryEndpoint}
              onChange={(e) => setDiscoveryEndpoint(e.target.value)}
              placeholder="https://auth.example.com/realms/myrealm"
            />
          </div>

          <div className="flex flex-col gap-1.5">
            <Label htmlFor="jwksEndpoint">JSON Web Key Set endpoint</Label>
            <Input
              id="jwksEndpoint"
              type="url"
              value={jwksEndpoint}
              onChange={(e) => setJwksEndpoint(e.target.value)}
              placeholder="https://auth.example.com/realms/myrealm/protocol/openid-connect/certs"
            />
          </div>

          <div className="flex flex-col gap-1.5">
            <Label htmlFor="authorizationEndpoint">Authorization endpoint</Label>
            <Input
              id="authorizationEndpoint"
              type="url"
              value={authorizationEndpoint}
              onChange={(e) => setAuthorizationEndpoint(e.target.value)}
            />
          </div>

          <div className="flex flex-col gap-1.5">
            <Label htmlFor="tokenEndpoint">Token endpoint</Label>
            <Input
              id="tokenEndpoint"
              type="url"
              value={tokenEndpoint}
              onChange={(e) => setTokenEndpoint(e.target.value)}
            />
          </div>

          <div className="flex flex-col gap-1.5">
            <Label htmlFor="userEndpoint">User endpoint</Label>
            <Input
              id="userEndpoint"
              type="url"
              value={userEndpoint}
              onChange={(e) => setUserEndpoint(e.target.value)}
            />
          </div>

          <div className="flex flex-col gap-1.5">
            <Label htmlFor="logoutEndpoint">Logout endpoint</Label>
            <Input
              id="logoutEndpoint"
              type="url"
              value={logoutEndpoint}
              onChange={(e) => setLogoutEndpoint(e.target.value)}
            />
          </div>

          <div className="flex items-center gap-2">
            <Checkbox
              id="useBasicAuth"
              checked={useBasicAuth}
              onCheckedChange={(v) => setUseBasicAuth(v === true)}
            />
            <Label htmlFor="useBasicAuth">Use Basic auth for token endpoint</Label>
          </div>

          <div className="flex items-center gap-2">
            <Checkbox
              id="useNonce"
              checked={useNonce}
              onCheckedChange={(v) => setUseNonce(v === true)}
            />
            <Label htmlFor="useNonce">Use nonce</Label>
          </div>

          <div className="flex flex-col gap-1.5">
            <Label htmlFor="nonceSize">Nonce size</Label>
            <Input
              id="nonceSize"
              type="number"
              min={1}
              value={nonceSize}
              onChange={(e) => setNonceSize(Number(e.target.value))}
              className="w-24"
            />
          </div>

          <div className="flex flex-col gap-1.5">
            <Label htmlFor="stateSize">State size</Label>
            <Input
              id="stateSize"
              type="number"
              min={1}
              value={stateSize}
              onChange={(e) => setStateSize(Number(e.target.value))}
              className="w-24"
            />
          </div>

          {error && <p className="text-sm text-destructive">{error}</p>}

          <div className="flex gap-2">
            <Button type="submit" disabled={saving}>
              {saving ? "Saving…" : "Save"}
            </Button>
            <Button type="button" variant="outline" onClick={() => router.push("/keycloak")}>
              Cancel
            </Button>
          </div>
        </form>
      </div>
    </div>
  )
}
```

- [ ] **Step 2: Format and typecheck**

```bash
cd frontend/admin-portal
npm run format
npm run typecheck
```

Expected: no errors.

- [ ] **Step 3: Commit**

```bash
git add frontend/admin-portal/app/keycloak/new/
git commit -m "feat(oidc): add OIDC provider add form"
```

---

## Task 12: Edit Provider Form

**Files:**
- Create: `frontend/admin-portal/app/keycloak/[id]/page.tsx`

- [ ] **Step 1: Create the directory and file**

```bash
mkdir -p "frontend/admin-portal/app/keycloak/[id]"
```

```tsx
// frontend/admin-portal/app/keycloak/[id]/page.tsx
"use client"

import { useEffect, useState } from "react"
import { useRouter, useParams } from "next/navigation"
import { useAuth } from "@/contexts/auth-context"
import { apiFetch } from "@/lib/api-client"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import { Checkbox } from "@/components/ui/checkbox"

export default function Page() {
  const { keycloak } = useAuth()
  const router = useRouter()
  const params = useParams()
  const id = params.id as string

  const [loading, setLoading] = useState(true)
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const [identifier, setIdentifier] = useState("")
  const [discoveryEndpoint, setDiscoveryEndpoint] = useState("")
  const [jwksEndpoint, setJwksEndpoint] = useState("")
  const [authorizationEndpoint, setAuthorizationEndpoint] = useState("")
  const [tokenEndpoint, setTokenEndpoint] = useState("")
  const [userEndpoint, setUserEndpoint] = useState("")
  const [logoutEndpoint, setLogoutEndpoint] = useState("")
  const [useBasicAuth, setUseBasicAuth] = useState(false)
  const [useNonce, setUseNonce] = useState(true)
  const [nonceSize, setNonceSize] = useState(32)
  const [stateSize, setStateSize] = useState(32)

  useEffect(() => {
    async function load() {
      if (!keycloak.token) return
      const res = await apiFetch(`/admin/oidc-providers/${id}`, keycloak.token)
      if (!res.ok) {
        setError(`Failed to load provider (${res.status})`)
        setLoading(false)
        return
      }
      const data = await res.json()
      setIdentifier(data.identifier ?? "")
      setDiscoveryEndpoint(data.discoveryEndpoint ?? "")
      setJwksEndpoint(data.jwksEndpoint ?? "")
      setAuthorizationEndpoint(data.authorizationEndpoint ?? "")
      setTokenEndpoint(data.tokenEndpoint ?? "")
      setUserEndpoint(data.userEndpoint ?? "")
      setLogoutEndpoint(data.logoutEndpoint ?? "")
      setUseBasicAuth(data.useBasicAuthForTokenEndpoint ?? false)
      setUseNonce(data.useNonce ?? true)
      setNonceSize(data.nonceSize ?? 32)
      setStateSize(data.stateSize ?? 32)
      setLoading(false)
    }
    load()
  }, [id, keycloak.token])

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault()
    if (!keycloak.token) return
    setSaving(true)
    setError(null)

    const body = {
      identifier,
      discoveryEndpoint: discoveryEndpoint || null,
      jwksEndpoint: jwksEndpoint || null,
      authorizationEndpoint: authorizationEndpoint || null,
      tokenEndpoint: tokenEndpoint || null,
      userEndpoint: userEndpoint || null,
      logoutEndpoint: logoutEndpoint || null,
      useBasicAuthForTokenEndpoint: useBasicAuth,
      useNonce,
      nonceSize,
      stateSize,
    }

    const res = await apiFetch(`/admin/oidc-providers/${id}`, keycloak.token, {
      method: "PUT",
      body: JSON.stringify(body),
    })

    if (res.ok) {
      router.push("/keycloak")
    } else {
      const data = await res.json().catch(() => ({}))
      setError(data.detail ?? `Save failed (${res.status})`)
      setSaving(false)
    }
  }

  if (loading) {
    return (
      <div className="flex min-h-svh p-6">
        <p className="text-sm text-muted-foreground">Loading…</p>
      </div>
    )
  }

  return (
    <div className="flex min-h-svh p-6">
      <div className="w-full max-w-lg">
        <h1 className="mb-6 text-base font-medium">Edit OIDC Provider</h1>
        <form onSubmit={handleSubmit} className="flex flex-col gap-4">
          <div className="flex flex-col gap-1.5">
            <Label htmlFor="identifier">Identifier</Label>
            <Input
              id="identifier"
              value={identifier}
              onChange={(e) => setIdentifier(e.target.value)}
              required
            />
          </div>

          <div className="flex flex-col gap-1.5">
            <Label htmlFor="discoveryEndpoint">Discovery endpoint</Label>
            <Input
              id="discoveryEndpoint"
              type="url"
              value={discoveryEndpoint}
              onChange={(e) => setDiscoveryEndpoint(e.target.value)}
              placeholder="https://auth.example.com/realms/myrealm"
            />
          </div>

          <div className="flex flex-col gap-1.5">
            <Label htmlFor="jwksEndpoint">JSON Web Key Set endpoint</Label>
            <Input
              id="jwksEndpoint"
              type="url"
              value={jwksEndpoint}
              onChange={(e) => setJwksEndpoint(e.target.value)}
            />
          </div>

          <div className="flex flex-col gap-1.5">
            <Label htmlFor="authorizationEndpoint">Authorization endpoint</Label>
            <Input
              id="authorizationEndpoint"
              type="url"
              value={authorizationEndpoint}
              onChange={(e) => setAuthorizationEndpoint(e.target.value)}
            />
          </div>

          <div className="flex flex-col gap-1.5">
            <Label htmlFor="tokenEndpoint">Token endpoint</Label>
            <Input
              id="tokenEndpoint"
              type="url"
              value={tokenEndpoint}
              onChange={(e) => setTokenEndpoint(e.target.value)}
            />
          </div>

          <div className="flex flex-col gap-1.5">
            <Label htmlFor="userEndpoint">User endpoint</Label>
            <Input
              id="userEndpoint"
              type="url"
              value={userEndpoint}
              onChange={(e) => setUserEndpoint(e.target.value)}
            />
          </div>

          <div className="flex flex-col gap-1.5">
            <Label htmlFor="logoutEndpoint">Logout endpoint</Label>
            <Input
              id="logoutEndpoint"
              type="url"
              value={logoutEndpoint}
              onChange={(e) => setLogoutEndpoint(e.target.value)}
            />
          </div>

          <div className="flex items-center gap-2">
            <Checkbox
              id="useBasicAuth"
              checked={useBasicAuth}
              onCheckedChange={(v) => setUseBasicAuth(v === true)}
            />
            <Label htmlFor="useBasicAuth">Use Basic auth for token endpoint</Label>
          </div>

          <div className="flex items-center gap-2">
            <Checkbox
              id="useNonce"
              checked={useNonce}
              onCheckedChange={(v) => setUseNonce(v === true)}
            />
            <Label htmlFor="useNonce">Use nonce</Label>
          </div>

          <div className="flex flex-col gap-1.5">
            <Label htmlFor="nonceSize">Nonce size</Label>
            <Input
              id="nonceSize"
              type="number"
              min={1}
              value={nonceSize}
              onChange={(e) => setNonceSize(Number(e.target.value))}
              className="w-24"
            />
          </div>

          <div className="flex flex-col gap-1.5">
            <Label htmlFor="stateSize">State size</Label>
            <Input
              id="stateSize"
              type="number"
              min={1}
              value={stateSize}
              onChange={(e) => setStateSize(Number(e.target.value))}
              className="w-24"
            />
          </div>

          {error && <p className="text-sm text-destructive">{error}</p>}

          <div className="flex gap-2">
            <Button type="submit" disabled={saving}>
              {saving ? "Saving…" : "Save"}
            </Button>
            <Button type="button" variant="outline" onClick={() => router.push("/keycloak")}>
              Cancel
            </Button>
          </div>
        </form>
      </div>
    </div>
  )
}
```

- [ ] **Step 2: Format and typecheck**

```bash
cd frontend/admin-portal
npm run format
npm run typecheck
```

Expected: no errors.

- [ ] **Step 3: Commit**

```bash
git add "frontend/admin-portal/app/keycloak/[id]/"
git commit -m "feat(oidc): add OIDC provider edit form"
```

---

## Self-Review Notes

- All 12 spec requirements (6 URL fields, 2 booleans, 2 integers, list + add/edit/delete/activate) are covered.
- `OidcProviders` is added to `AllTables` (Task 2) so H2 tests create the table.
- Route tests (Task 4) are written before the routes (Task 5) — true TDD order.
- `OidcProviderRegistrar` (Task 6) seeds from `OIDC_ISSUER` only when the table is empty — safe to run repeatedly.
- `AuthenticationModule` (Task 7) falls back to the env var if no active DB provider exists — no hard dependency on the DB row at startup.
- Frontend API client uses `NEXT_PUBLIC_API_URL` — must be set in `.env.local` before running.
- The `Button` component's `size="xs"` variant is already defined (per CLAUDE.md).
- **Potential issue:** If the frontend and backend are on different origins in local dev, you may see CORS preflight errors. Add `io.ktor.server.plugins.cors.*` configuration to the backend if needed — not covered in this plan.
