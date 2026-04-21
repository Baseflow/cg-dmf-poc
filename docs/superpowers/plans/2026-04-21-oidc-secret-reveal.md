# OIDC Secret Reveal Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Return the decrypted OIDC client secret in the GET response and show it in the frontend read view with an eye-toggle.

**Architecture:** The backend decrypts `clientSecretEncrypted` in `toResponse()` and includes it as `clientSecret: String?` in the response model. The frontend stores this value in state and shows/hides it using the existing `showSecret` toggle, which is also used in the edit form.

**Tech Stack:** Kotlin/Ktor (backend), Next.js 16 / React 19 / TypeScript (frontend), Kotlin test + Ktor testApplication (backend tests)

---

## File Map

| File | Change |
|---|---|
| `src/main/kotlin/api/models/OidcSettingsModels.kt` | Add `clientSecret: String?` field to `OidcSettingsResponse` |
| `src/main/kotlin/api/admin/routes/OidcSettingsRoutes.kt` | Decrypt secret in `toResponse()` |
| `build.gradle.kts` | Add `OIDC_CLIENT_SECRET_ENCRYPTION_KEY` to test environment |
| `src/test/kotlin/api/admin/routes/OidcSettingsRoutesTest.kt` | New — integration tests for GET/PUT oidc-settings |
| `frontend/admin-portal/app/instellingen/oidc/page.tsx` | Add `clientSecret` to interface/state, add eye-toggle in read view |

---

## Task 1: Update `OidcSettingsResponse` model

**Files:**
- Modify: `src/main/kotlin/api/models/OidcSettingsModels.kt`

- [ ] **Step 1: Add `clientSecret: String?` to the response model**

Replace the entire file content:

```kotlin
// SPDX-License-Identifier: EUPL-1.2
// Copyright (C) 2026 Gemeente Utrecht
package com.baseflow.api.models

import kotlinx.serialization.Serializable

@Serializable
data class OidcSettingsResponse(
    val issuer: String,
    val clientId: String,
    val hasSecret: Boolean,
    val clientSecret: String?,
    val updatedAt: String,
)

@Serializable
data class UpdateOidcSettingsRequest(
    val issuer: String,
    val clientId: String,
    /** Leave null or omit to keep the existing secret unchanged. */
    val clientSecret: String? = null,
)
```

- [ ] **Step 2: Verify it compiles**

```bash
./gradlew compileKotlin
```

Expected: `BUILD SUCCESSFUL`

---

## Task 2: Set up test environment variable

**Files:**
- Modify: `build.gradle.kts`

The test for the route will call `OidcCrypto.decrypt()`, which requires the `OIDC_CLIENT_SECRET_ENCRYPTION_KEY` env var. Add it to `tasks.test`.

- [ ] **Step 1: Add env var to `tasks.test {}` block**

In `build.gradle.kts`, update the existing `tasks.test` block (currently at line ~220):

```kotlin
tasks.test {
    useJUnitPlatform()
    environment("OIDC_CLIENT_SECRET_ENCRYPTION_KEY", "test-encryption-key-for-unit-tests")
}
```

---

## Task 3: Write failing integration test for GET returning decrypted secret

**Files:**
- Create: `src/test/kotlin/api/admin/routes/OidcSettingsRoutesTest.kt`

- [ ] **Step 1: Create the test file**

```kotlin
// SPDX-License-Identifier: EUPL-1.2
// Copyright (C) 2026 Gemeente Utrecht
package com.baseflow.api.admin.routes

import com.baseflow.api.routes.TestBase
import com.baseflow.config.OidcCrypto
import com.baseflow.entities.OidcSettingsEntity
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.boolean
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

private val SETTINGS_ID = UUID.fromString("00000000-0000-0000-0000-000000000001")

class OidcSettingsRoutesTest : TestBase("oidc_settings") {

    @Test
    fun `GET returns 404 when no settings are configured`() = testApplication {
        application { setup() }

        val response = client.get("/admin/oidc-settings") {
            header(HttpHeaders.Authorization, "Bearer test")
        }

        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    @Test
    fun `GET returns settings with decrypted clientSecret when a secret is stored`() = testApplication {
        application { setup() }

        val plainSecret = "my-super-secret"
        transaction {
            OidcSettingsEntity.new(SETTINGS_ID) {
                issuer = "https://auth.example.com"
                clientId = "my-client"
                clientSecretEncrypted = OidcCrypto.encrypt(plainSecret)
                updatedAt = kotlinx.datetime.Clock.System.now()
                    .toLocalDateTime(kotlinx.datetime.TimeZone.UTC)
            }
        }

        val response = client.get("/admin/oidc-settings") {
            header(HttpHeaders.Authorization, "Bearer test")
        }

        assertEquals(HttpStatusCode.OK, response.status)

        val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
        assertEquals("https://auth.example.com", body["issuer"]?.jsonPrimitive?.content)
        assertEquals("my-client", body["clientId"]?.jsonPrimitive?.content)
        assertEquals(true, body["hasSecret"]?.jsonPrimitive?.boolean)
        assertEquals(plainSecret, body["clientSecret"]?.jsonPrimitive?.content)
    }

    @Test
    fun `GET returns null clientSecret when no secret is stored`() = testApplication {
        application { setup() }

        transaction {
            OidcSettingsEntity.new(SETTINGS_ID) {
                issuer = "https://auth.example.com"
                clientId = "my-client"
                clientSecretEncrypted = null
                updatedAt = kotlinx.datetime.Clock.System.now()
                    .toLocalDateTime(kotlinx.datetime.TimeZone.UTC)
            }
        }

        val response = client.get("/admin/oidc-settings") {
            header(HttpHeaders.Authorization, "Bearer test")
        }

        assertEquals(HttpStatusCode.OK, response.status)

        val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
        assertEquals(false, body["hasSecret"]?.jsonPrimitive?.boolean)
        assertEquals(JsonNull, body["clientSecret"])
    }
}
```

- [ ] **Step 2: Run the tests to verify they fail**

```bash
./gradlew test --tests "com.baseflow.api.admin.routes.OidcSettingsRoutesTest"
```

Expected: Tests fail — `clientSecret` field missing from response JSON because `toResponse()` doesn't include it yet.

---

## Task 4: Update `toResponse()` to decrypt the secret

**Files:**
- Modify: `src/main/kotlin/api/admin/routes/OidcSettingsRoutes.kt`

- [ ] **Step 1: Update `toResponse()` at the bottom of the file**

Replace the existing `toResponse()` extension function (lines 109–114):

```kotlin
private fun OidcSettingsEntity.toResponse() = OidcSettingsResponse(
    issuer = issuer,
    clientId = clientId,
    hasSecret = clientSecretEncrypted != null,
    clientSecret = clientSecretEncrypted?.let { OidcCrypto.decrypt(it) },
    updatedAt = updatedAt.toString(),
)
```

- [ ] **Step 2: Run the tests to verify they pass**

```bash
./gradlew test --tests "com.baseflow.api.admin.routes.OidcSettingsRoutesTest"
```

Expected: All 3 tests pass.

- [ ] **Step 3: Run full test suite to check for regressions**

```bash
./gradlew test
```

Expected: `BUILD SUCCESSFUL`, all tests pass.

- [ ] **Step 4: Commit backend changes**

```bash
git add src/main/kotlin/api/models/OidcSettingsModels.kt \
        src/main/kotlin/api/admin/routes/OidcSettingsRoutes.kt \
        src/test/kotlin/api/admin/routes/OidcSettingsRoutesTest.kt \
        build.gradle.kts
git commit -m "feat(oidc): return decrypted client secret in GET oidc-settings response"
```

---

## Task 5: Update the frontend

**Files:**
- Modify: `frontend/admin-portal/app/instellingen/oidc/page.tsx`

This task updates the frontend in one go: interface, state, fetch handling, and the read-view eye-toggle.

- [ ] **Step 1: Update `page.tsx`**

Apply these changes to `frontend/admin-portal/app/instellingen/oidc/page.tsx`:

**1. Extend the `OidcSettings` interface** (lines 13–18):

```typescript
interface OidcSettings {
  issuer: string
  clientId: string
  hasSecret: boolean
  clientSecret: string | null
  updatedAt: string
}
```

**2. Add `currentSecret` state** after the existing `const [hasSecret, setHasSecret] = React.useState(false)` line (after line 32):

```typescript
const [currentSecret, setCurrentSecret] = React.useState<string | null>(null)
```

**3. In `fetchSettings`, populate `currentSecret` from the response** — add after `setHasSecret(data.hasSecret)` inside the `try` block (after line 51):

```typescript
setCurrentSecret(data.clientSecret)
```

**4. After `handleSave` sets `setHasSecret(data.hasSecret)` (line 95), also update `currentSecret`** — add after that line:

```typescript
setCurrentSecret(data.clientSecret)
```

**5. In `handleCancel`, reset `currentSecret`** — the cancel handler already resets `showSecret`. No change needed for `currentSecret` (it holds the last saved value, which is still valid after cancel).

**6. Replace the read-view "Client secret" field** (the `else` branch inside `<Field label="Client secret">`, lines 203–212):

Replace:
```typescript
) : (
  <Value className="tracking-widest">
    {hasSecret ? (
      "••••••••••••"
    ) : (
      <span className="tracking-normal italic">
        Niet geconfigureerd
      </span>
    )}
  </Value>
)}
```

With:
```typescript
) : (
  <div className="flex items-center gap-2">
    <Value className={currentSecret ? "tracking-widest" : undefined}>
      {currentSecret ? (
        showSecret ? currentSecret : "••••••••••••"
      ) : (
        <span className="tracking-normal italic">
          Niet geconfigureerd
        </span>
      )}
    </Value>
    {currentSecret && (
      <button
        type="button"
        onClick={() => setShowSecret((v) => !v)}
        className="text-muted-foreground transition-colors hover:text-foreground"
        aria-label={showSecret ? "Verberg secret" : "Toon secret"}
      >
        {showSecret ? (
          <EyeOff className="size-4" />
        ) : (
          <Eye className="size-4" />
        )}
      </button>
    )}
  </div>
)}
```

- [ ] **Step 2: Run type-check**

```bash
cd frontend/admin-portal && npm run typecheck
```

Expected: No errors.

- [ ] **Step 3: Run formatter**

```bash
cd frontend/admin-portal && npm run format
```

Expected: Files formatted with no errors.

- [ ] **Step 4: Commit frontend changes**

```bash
git add frontend/admin-portal/app/instellingen/oidc/page.tsx
git commit -m "feat(oidc): show decrypted client secret in read view with eye-toggle"
```
