# OIDC Settings Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Store OIDC settings (issuer, client ID, AES-encrypted client secret) in PostgreSQL and expose them via a `GET`/`PUT` admin API that the Next.js admin portal reads and writes.

**Architecture:** A single `oidc_settings` row is created/updated via upsert on a fixed UUID. The client secret is AES-256-GCM encrypted using a server-side key from an env var before being stored; the API response never returns the plaintext or ciphertext — only a `hasSecret` boolean. The frontend fetches on mount (showing a skeleton) and submits changes with the Keycloak bearer token.

**Tech Stack:** Kotlin + Ktor 3.4.2 · Exposed ORM 1.2.0 · Flyway 12 · PostgreSQL · Java `javax.crypto` (AES-GCM, no extra deps) · Next.js 16 / React 19 · shadcn/ui · keycloak-js

---

## File Map

**Create:**
- `src/main/resources/db/migration/V12__OidcSettings.sql` — table DDL
- `src/main/kotlin/entities/OidcSettings.kt` — Exposed table object + entity class
- `src/main/kotlin/config/OidcCrypto.kt` — AES-256-GCM encrypt/decrypt utility
- `src/main/kotlin/api/models/OidcSettingsModels.kt` — `OidcSettingsResponse` + `UpdateOidcSettingsRequest` data classes
- `src/main/kotlin/api/routes/OidcSettingsRoutes.kt` — `GET /admin/oidc-settings` and `PUT /admin/oidc-settings`

**Modify:**
- `src/main/kotlin/api/DocumentenApiRoutes.kt` — register `oidcSettingsRoutes()` inside the `/admin` block
- `.env` — add `OIDC_CLIENT_SECRET_KEY`
- `frontend/admin-portal/.env.local` — add `NEXT_PUBLIC_API_URL`
- `frontend/admin-portal/app/instellingen/oidc/page.tsx` — wire fetch + submit to the API

---

## Task 1: Flyway migration — `oidc_settings` table

**Files:**
- Create: `src/main/resources/db/migration/V12__OidcSettings.sql`

- [ ] **Step 1: Write the migration**

```sql
CREATE TABLE oidc_settings
(
    id                       UUID    NOT NULL,
    issuer                   TEXT    NOT NULL,
    client_id                TEXT    NOT NULL,
    client_secret_encrypted  TEXT,
    updated_at               TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_oidc_settings PRIMARY KEY (id)
);
```

Save to `src/main/resources/db/migration/V12__OidcSettings.sql`.

- [ ] **Step 2: Verify Flyway picks it up**

Start the backend (`./gradlew run` or via IDE). Check logs for:
```
Successfully applied 1 migration to schema "public", now at version v12
```
If the backend is not running, this will be validated in Task 6 instead.

- [ ] **Step 3: Commit**

```bash
git add src/main/resources/db/migration/V12__OidcSettings.sql
git commit -m "feat(oidc): add oidc_settings table migration (V12)"
```

---

## Task 2: Exposed ORM entity

**Files:**
- Create: `src/main/kotlin/entities/OidcSettings.kt`

- [ ] **Step 1: Write the entity**

```kotlin
// SPDX-License-Identifier: EUPL-1.2
// Copyright (C) 2026 Gemeente Utrecht
package com.baseflow.entities

import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.dao.id.java.UUIDTable
import org.jetbrains.exposed.v1.dao.java.UUIDEntity
import org.jetbrains.exposed.v1.dao.java.UUIDEntityClass
import org.jetbrains.exposed.v1.datetime.CurrentDateTime
import org.jetbrains.exposed.v1.datetime.datetime
import java.util.UUID

object OidcSettingsTable : UUIDTable("oidc_settings") {
    val issuer = text("issuer")
    val clientId = text("client_id")
    val clientSecretEncrypted = text("client_secret_encrypted").nullable()
    val updatedAt = datetime("updated_at").defaultExpression(CurrentDateTime)
}

class OidcSettingsEntity(id: EntityID<UUID>) : UUIDEntity(id) {
    companion object : UUIDEntityClass<OidcSettingsEntity>(OidcSettingsTable)

    var issuer by OidcSettingsTable.issuer
    var clientId by OidcSettingsTable.clientId
    var clientSecretEncrypted by OidcSettingsTable.clientSecretEncrypted
    var updatedAt by OidcSettingsTable.updatedAt
}
```

- [ ] **Step 2: Commit**

```bash
git add src/main/kotlin/entities/OidcSettings.kt
git commit -m "feat(oidc): add OidcSettingsEntity (Exposed ORM)"
```

---

## Task 3: AES-256-GCM crypto utility

**Files:**
- Create: `src/main/kotlin/config/OidcCrypto.kt`
- Modify: `.env`

- [ ] **Step 1: Add the encryption key env var to `.env`**

Open `.env` and append:
```
# AES-256 key for encrypting the OIDC client secret at rest.
# Must be at least 8 characters. SHA-256 of this value is used as the actual 32-byte key.
# CHANGE THIS to a long random string in every non-local environment.
OIDC_CLIENT_SECRET_KEY=dev-insecure-oidc-key-change-in-production
```

- [ ] **Step 2: Write the crypto utility**

```kotlin
// SPDX-License-Identifier: EUPL-1.2
// Copyright (C) 2026 Gemeente Utrecht
package com.baseflow.config

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * AES-256-GCM encryption for the OIDC client secret stored at rest.
 *
 * Storage format: Base64( IV[12 bytes] || ciphertext+tag )
 * Key: SHA-256 of OIDC_CLIENT_SECRET_KEY env var → 32 bytes → AES-256 key
 */
internal object OidcCrypto {
    private val secretKey: SecretKeySpec by lazy {
        val raw = Config.envOrSystem("OIDC_CLIENT_SECRET_KEY", "dev-insecure-oidc-key-change-in-production")
        val keyBytes = MessageDigest.getInstance("SHA-256").digest(raw.toByteArray(Charsets.UTF_8))
        SecretKeySpec(keyBytes, "AES")
    }

    fun encrypt(plaintext: String): String {
        val iv = ByteArray(12).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, GCMParameterSpec(128, iv))
        val ciphertext = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        return Base64.getEncoder().encodeToString(iv + ciphertext)
    }

    fun decrypt(encoded: String): String {
        val combined = Base64.getDecoder().decode(encoded)
        val iv = combined.sliceArray(0 until 12)
        val ciphertext = combined.sliceArray(12 until combined.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(128, iv))
        return String(cipher.doFinal(ciphertext), Charsets.UTF_8)
    }
}
```

Save to `src/main/kotlin/config/OidcCrypto.kt`.

- [ ] **Step 3: Commit**

```bash
git add src/main/kotlin/config/OidcCrypto.kt .env
git commit -m "feat(oidc): add AES-256-GCM crypto utility for client secret"
```

---

## Task 4: API models

**Files:**
- Create: `src/main/kotlin/api/models/OidcSettingsModels.kt`

- [ ] **Step 1: Write the models**

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

Save to `src/main/kotlin/api/models/OidcSettingsModels.kt`.

- [ ] **Step 2: Commit**

```bash
git add src/main/kotlin/api/models/OidcSettingsModels.kt
git commit -m "feat(oidc): add OidcSettingsResponse and UpdateOidcSettingsRequest models"
```

---

## Task 5: Route handler

**Files:**
- Create: `src/main/kotlin/api/routes/OidcSettingsRoutes.kt`

The single OIDC settings row always uses the fixed UUID `00000000-0000-0000-0000-000000000001`. GET returns 404 when no row exists yet. PUT upserts.

- [ ] **Step 1: Write the route**

```kotlin
// SPDX-License-Identifier: EUPL-1.2
// Copyright (C) 2026 Gemeente Utrecht
package com.baseflow.api.routes

import com.baseflow.api.models.OidcSettingsResponse
import com.baseflow.api.models.UpdateOidcSettingsRequest
import com.baseflow.api.models.badRequest
import com.baseflow.api.models.notFound
import com.baseflow.api.models.respondProblem
import com.baseflow.config.OidcCrypto
import com.baseflow.entities.OidcSettingsEntity
import com.baseflow.entities.OidcSettingsTable
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.put
import io.ktor.server.routing.route
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.util.UUID

private val SETTINGS_ID = UUID.fromString("00000000-0000-0000-0000-000000000001")

/**
 * Admin routes for managing OIDC provider settings.
 *
 * Mounted at `/admin/oidc-settings`.
 *
 * Endpoints:
 * - `GET  /` — get current OIDC settings (404 if not yet configured)
 * - `PUT  /` — create or update OIDC settings
 */
fun Route.oidcSettingsRoutes() {
    route("/oidc-settings") {
        /**
         * Geeft de huidige OIDC-instellingen.
         *
         * Responses:
         *   - 200 De huidige OIDC-instellingen.
         *   - 404 Nog geen OIDC-instellingen geconfigureerd.
         *
         * @tag Admin
         */
        get {
            val settings = transaction {
                OidcSettingsEntity.findById(SETTINGS_ID)
            } ?: return@get call.respondProblem(
                HttpStatusCode.NotFound,
                notFound("OIDC settings have not been configured yet.", call.request.path()),
            )
            call.respond(settings.toResponse())
        }

        /**
         * Maakt of overschrijft de OIDC-instellingen.
         *
         * Responses:
         *   - 200 De bijgewerkte OIDC-instellingen.
         *   - 400 Ongeldige aanvraag.
         *
         * @tag Admin
         */
        put {
            val body = runCatching { call.receive<UpdateOidcSettingsRequest>() }.getOrNull()
                ?: return@put call.respondProblem(
                    HttpStatusCode.BadRequest,
                    badRequest("Request body must be JSON with 'issuer' and 'clientId' fields.", call.request.path()),
                )

            if (body.issuer.isBlank()) {
                return@put call.respondProblem(
                    HttpStatusCode.BadRequest,
                    badRequest("'issuer' must not be blank.", call.request.path()),
                )
            }
            if (body.clientId.isBlank()) {
                return@put call.respondProblem(
                    HttpStatusCode.BadRequest,
                    badRequest("'clientId' must not be blank.", call.request.path()),
                )
            }

            val updated = transaction {
                val existing = OidcSettingsEntity.findById(SETTINGS_ID)
                if (existing != null) {
                    existing.issuer = body.issuer
                    existing.clientId = body.clientId
                    if (!body.clientSecret.isNullOrBlank()) {
                        existing.clientSecretEncrypted = OidcCrypto.encrypt(body.clientSecret)
                    }
                    existing
                } else {
                    OidcSettingsEntity.new(SETTINGS_ID) {
                        issuer = body.issuer
                        clientId = body.clientId
                        clientSecretEncrypted = body.clientSecret
                            ?.takeIf { it.isNotBlank() }
                            ?.let { OidcCrypto.encrypt(it) }
                    }
                }
            }
            call.respond(HttpStatusCode.OK, updated.toResponse())
        }
    }
}

private fun OidcSettingsEntity.toResponse() = OidcSettingsResponse(
    issuer = issuer,
    clientId = clientId,
    hasSecret = clientSecretEncrypted != null,
    updatedAt = updatedAt.toString(),
)
```

Save to `src/main/kotlin/api/routes/OidcSettingsRoutes.kt`.

- [ ] **Step 2: Commit**

```bash
git add src/main/kotlin/api/routes/OidcSettingsRoutes.kt
git commit -m "feat(oidc): add GET/PUT /admin/oidc-settings route handler"
```

---

## Task 6: Register routes + verify backend starts

**Files:**
- Modify: `src/main/kotlin/api/DocumentenApiRoutes.kt`

- [ ] **Step 1: Add the import and register the new routes**

In `DocumentenApiRoutes.kt`, add the import at the top (alongside the other route imports):
```kotlin
import com.baseflow.api.routes.oidcSettingsRoutes
```

Inside both the `if (useAuthentication)` and `else` `/admin` blocks, add `oidcSettingsRoutes()` after `blobStorageRepositoryRoutes()`:

```kotlin
route("/admin") {
    blobStorageRepositoryRoutes()
    oidcSettingsRoutes()
}
```

Both the authenticated and unauthenticated blocks need this addition (the unauthenticated block is only used in tests).

- [ ] **Step 2: Start the backend and verify**

```bash
./gradlew run
```

Check for:
1. `Successfully applied 1 migration` (V12 runs for the first time)
2. No compilation errors
3. `GET http://localhost:8080/admin/oidc-settings` returns HTTP 404 with a problem JSON body
4. `PUT http://localhost:8080/admin/oidc-settings` with body `{"issuer":"https://example.com","clientId":"test","clientSecret":"s3cr3t"}` returns HTTP 200

Use `Authorization: Bearer bypass` header for local testing (ZGW bypass auth).

- [ ] **Step 3: Commit**

```bash
git add src/main/kotlin/api/DocumentenApiRoutes.kt
git commit -m "feat(oidc): register oidcSettingsRoutes in admin block"
```

---

## Task 7: Frontend — env var + API integration

**Files:**
- Modify: `frontend/admin-portal/.env.local`
- Modify: `frontend/admin-portal/app/instellingen/oidc/page.tsx`

- [ ] **Step 1: Add `NEXT_PUBLIC_API_URL` to `.env.local`**

Append to `frontend/admin-portal/.env.local`:
```
NEXT_PUBLIC_API_URL=http://localhost:8080
```

- [ ] **Step 2: Replace `page.tsx` with the API-connected version**

Replace the entire contents of `frontend/admin-portal/app/instellingen/oidc/page.tsx`:

```tsx
"use client"

import * as React from "react"
import { Check, Eye, EyeOff, Pencil, X } from "lucide-react"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { Skeleton } from "@/components/ui/skeleton"
import { useAuth } from "@/contexts/auth-context"

const API_URL = process.env.NEXT_PUBLIC_API_URL ?? ""

interface OidcSettings {
  issuer: string
  clientId: string
  hasSecret: boolean
  updatedAt: string
}

export default function Page() {
  const { keycloak } = useAuth()

  const [loading, setLoading] = React.useState(true)
  const [error, setError] = React.useState<string | null>(null)
  const [editing, setEditing] = React.useState(false)
  const [saving, setSaving] = React.useState(false)
  const [showSecret, setShowSecret] = React.useState(false)

  const [issuer, setIssuer] = React.useState("")
  const [clientId, setClientId] = React.useState("")
  const [clientSecret, setClientSecret] = React.useState("")
  const [hasSecret, setHasSecret] = React.useState(false)

  const [saved, setSaved] = React.useState({ issuer: "", clientId: "" })

  React.useEffect(() => {
    async function fetchSettings() {
      try {
        const res = await fetch(`${API_URL}/admin/oidc-settings`, {
          headers: { Authorization: `Bearer ${keycloak.token}` },
        })
        if (res.status === 404) {
          setLoading(false)
          return
        }
        if (!res.ok) throw new Error(`HTTP ${res.status}`)
        const data: OidcSettings = await res.json()
        setIssuer(data.issuer)
        setClientId(data.clientId)
        setHasSecret(data.hasSecret)
      } catch {
        setError("Kon de OIDC-instellingen niet laden.")
      } finally {
        setLoading(false)
      }
    }
    fetchSettings()
  }, [keycloak.token])

  function handleEdit() {
    setSaved({ issuer, clientId })
    setEditing(true)
  }

  function handleCancel() {
    setIssuer(saved.issuer)
    setClientId(saved.clientId)
    setClientSecret("")
    setShowSecret(false)
    setEditing(false)
  }

  async function handleSave(e: React.FormEvent<HTMLFormElement>) {
    e.preventDefault()
    setSaving(true)
    setError(null)
    try {
      const body: { issuer: string; clientId: string; clientSecret?: string } =
        { issuer, clientId }
      if (clientSecret) body.clientSecret = clientSecret
      const res = await fetch(`${API_URL}/admin/oidc-settings`, {
        method: "PUT",
        headers: {
          Authorization: `Bearer ${keycloak.token}`,
          "Content-Type": "application/json",
        },
        body: JSON.stringify(body),
      })
      if (!res.ok) throw new Error(`HTTP ${res.status}`)
      const data: OidcSettings = await res.json()
      setIssuer(data.issuer)
      setClientId(data.clientId)
      setHasSecret(data.hasSecret)
      setSaved({ issuer: data.issuer, clientId: data.clientId })
      setClientSecret("")
      setShowSecret(false)
      setEditing(false)
    } catch {
      setError("Opslaan mislukt. Probeer het opnieuw.")
    } finally {
      setSaving(false)
    }
  }

  if (loading) {
    return (
      <div className="flex min-h-svh p-6">
        <div className="flex w-full max-w-sm flex-col gap-6">
          <Skeleton className="h-4 w-3/4" />
          <div className="flex flex-col gap-5">
            <FieldSkeleton />
            <FieldSkeleton />
            <FieldSkeleton />
          </div>
        </div>
      </div>
    )
  }

  return (
    <div className="flex min-h-svh p-6">
      <div className="flex w-full max-w-sm flex-col gap-6">
        <div className="flex items-start justify-between">
          <p className="text-sm text-muted-foreground">
            Configuratie voor OpenID Connect authenticatie.
          </p>
          {!editing && (
            <Button variant="outline" size="sm" onClick={handleEdit}>
              <Pencil />
              Bewerken
            </Button>
          )}
        </div>

        {error && (
          <p className="text-destructive text-sm">{error}</p>
        )}

        <form onSubmit={handleSave} className="flex flex-col gap-5">
          <Field label="Issuer">
            {editing ? (
              <Input
                value={issuer}
                onChange={(e) => setIssuer(e.target.value)}
                placeholder="https://auth.example.com/realms/my-realm"
                required
                disabled={saving}
              />
            ) : (
              <Value>{issuer || <span className="italic">Niet geconfigureerd</span>}</Value>
            )}
          </Field>

          <Field label="Client ID">
            {editing ? (
              <Input
                value={clientId}
                onChange={(e) => setClientId(e.target.value)}
                placeholder="my-client-id"
                required
                disabled={saving}
              />
            ) : (
              <Value>{clientId || <span className="italic">Niet geconfigureerd</span>}</Value>
            )}
          </Field>

          <Field label="Client secret">
            {editing ? (
              <div className="relative">
                <Input
                  type={showSecret ? "text" : "password"}
                  value={clientSecret}
                  onChange={(e) => setClientSecret(e.target.value)}
                  placeholder={
                    hasSecret
                      ? "Laat leeg om huidig secret te bewaren"
                      : "Voer het client secret in"
                  }
                  className="pr-9"
                  disabled={saving}
                />
                <button
                  type="button"
                  onClick={() => setShowSecret((v) => !v)}
                  className="absolute top-1/2 right-2.5 -translate-y-1/2 text-muted-foreground transition-colors hover:text-foreground"
                  aria-label={showSecret ? "Verberg secret" : "Toon secret"}
                >
                  {showSecret ? (
                    <EyeOff className="size-4" />
                  ) : (
                    <Eye className="size-4" />
                  )}
                </button>
              </div>
            ) : (
              <Value className="tracking-widest">
                {hasSecret ? "••••••••••••" : <span className="italic tracking-normal">Niet geconfigureerd</span>}
              </Value>
            )}
          </Field>

          {editing && (
            <div className="flex gap-2">
              <Button type="submit" size="sm" disabled={saving}>
                <Check />
                {saving ? "Opslaan..." : "Opslaan"}
              </Button>
              <Button
                type="button"
                variant="outline"
                size="sm"
                onClick={handleCancel}
                disabled={saving}
              >
                <X />
                Annuleren
              </Button>
            </div>
          )}
        </form>
      </div>
    </div>
  )
}

function Field({
  label,
  children,
}: {
  label: string
  children: React.ReactNode
}) {
  return (
    <div className="flex flex-col gap-1.5">
      <span className="text-xs font-medium">{label}</span>
      {children}
    </div>
  )
}

function Value({
  children,
  className,
}: {
  children: React.ReactNode
  className?: string
}) {
  return (
    <p
      className={`font-mono text-xs leading-6 text-muted-foreground ${className ?? ""}`}
    >
      {children}
    </p>
  )
}

function FieldSkeleton() {
  return (
    <div className="flex flex-col gap-1.5">
      <Skeleton className="h-3 w-16" />
      <Skeleton className="h-4 w-48" />
    </div>
  )
}
```

- [ ] **Step 3: Run typecheck and format**

```bash
cd frontend/admin-portal
npm run typecheck
npm run format
```

Expected: no errors and no type warnings.

- [ ] **Step 4: Start the dev server and verify manually**

```bash
npm run dev
```

Open [http://localhost:3000/instellingen/oidc](http://localhost:3000/instellingen/oidc).

Verify:
1. Page loads with skeleton, then shows "Niet geconfigureerd" for all three fields (backend returns 404)
2. Clicking "Bewerken" switches to edit mode
3. Filling in issuer + client ID + secret and clicking "Opslaan" hits the backend (check network tab or backend logs)
4. After save, fields show the new values; secret shows `••••••••••••`
5. Clicking "Bewerken" again and saving with an empty secret field keeps the existing secret (`hasSecret` stays `true`)

- [ ] **Step 5: Commit**

```bash
git add frontend/admin-portal/.env.local frontend/admin-portal/app/instellingen/oidc/page.tsx
git commit -m "feat(oidc): wire OIDC settings page to GET/PUT /admin/oidc-settings"
```
