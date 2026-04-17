# OIDC Provider Management — Design Spec

**Date:** 2026-04-16
**Branch:** CGDMF-122-keycloak-configuratie
**Status:** Approved

---

## Overview

Replace the hardcoded Keycloak/OIDC configuration (currently loaded from the `OIDC_ISSUER` environment variable) with a database-backed OIDC provider management system. Administrators can add, edit, delete, and activate OIDC providers through the admin portal. The backend uses the single active provider at startup for JWT validation.

---

## Backend

### Database — `V11__OidcProviders.sql`

New Flyway migration creates the `oidc_providers` table:

```sql
CREATE TABLE oidc_providers (
    id                               UUID         NOT NULL,
    identifier                       VARCHAR(255) NOT NULL,
    discovery_endpoint               VARCHAR(1000),
    jwks_endpoint                    VARCHAR(1000),
    authorization_endpoint           VARCHAR(1000),
    token_endpoint                   VARCHAR(1000),
    user_endpoint                    VARCHAR(1000),
    logout_endpoint                  VARCHAR(1000),
    use_basic_auth_for_token_endpoint BOOLEAN     NOT NULL DEFAULT FALSE,
    use_nonce                        BOOLEAN      NOT NULL DEFAULT TRUE,
    nonce_size                       INTEGER      NOT NULL DEFAULT 32,
    state_size                       INTEGER      NOT NULL DEFAULT 32,
    is_active                        BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at                       TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at                       TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_oidc_providers PRIMARY KEY (id),
    CONSTRAINT uq_oidc_providers_identifier UNIQUE (identifier)
);
```

The migration also seeds one row from the current `OIDC_ISSUER` env var (deriving JWK, auth, token, user and logout endpoints from it), marked `is_active = TRUE`. This ensures existing deployments keep working without manual intervention.

A corresponding undo migration (`U11__OidcProviders.sql`) drops the table.

### Kotlin — Exposed Entity

`OidcProviderEntity` maps to the table using the existing Exposed v1 entity pattern (same as `BlobStorageRepositoryEntity`).

### API — `OidcProviderRoutes.kt`

Mounted at `/admin/oidc-providers`. Follows the same conventions as `BlobStorageRepositoryRoutes.kt`.

| Method | Path                        | Description                                  |
|--------|-----------------------------|----------------------------------------------|
| GET    | `/admin/oidc-providers`     | List all providers                           |
| POST   | `/admin/oidc-providers`     | Create a new provider                        |
| GET    | `/admin/oidc-providers/{id}`| Get a single provider by UUID                |
| PUT    | `/admin/oidc-providers/{id}`| Update a provider                            |
| DELETE | `/admin/oidc-providers/{id}`| Delete a provider (cannot delete active one) |
| PUT    | `/admin/oidc-providers/{id}/activate` | Set as active (deactivates all others) |

**Request/response shape** (`OidcProviderResponse`):
```json
{
  "id": "uuid",
  "identifier": "keycloak",
  "discoveryEndpoint": "https://...",
  "jwksEndpoint": "https://...",
  "authorizationEndpoint": "https://...",
  "tokenEndpoint": "https://...",
  "userEndpoint": "https://...",
  "logoutEndpoint": "https://...",
  "useBasicAuthForTokenEndpoint": false,
  "useNonce": true,
  "nonceSize": 32,
  "stateSize": 32,
  "isActive": true,
  "createdAt": "...",
  "updatedAt": "..."
}
```

POST/PUT body uses the same shape minus `id`, `isActive`, `createdAt`, `updatedAt`.

**Validation:**
- `identifier` must not be blank and must be unique
- At least one of `discoveryEndpoint` or `jwksEndpoint` must be provided (the JWK endpoint is required for token validation)
- `nonceSize` and `stateSize` must be positive integers
- Deleting the active provider returns `400 Bad Request`

### `AuthenticationModule.kt` changes

At startup, query for the provider where `is_active = TRUE`. Use its `jwks_endpoint` to build the `JwkProvider`. Fall back to the existing `OIDC_ISSUER` env var (constructing the JWK URL as `$issuer/protocol/openid-connect/certs`) if no active provider row exists in the DB.

---

## Frontend — Admin Portal

### Routing

| Path                        | Purpose                          |
|-----------------------------|----------------------------------|
| `/keycloak`                 | Provider list page               |
| `/keycloak/new`             | Add provider form                |
| `/keycloak/[id]`            | Edit provider form               |

The nav sidebar entry "Keycloak configuration" already exists and links to `/keycloak` — no sidebar changes needed. The page title on the list view becomes "OIDC Providers".

### List page (`app/keycloak/page.tsx`)

Client component. On mount, fetches `GET /admin/oidc-providers`.

Displays a table with columns:
- Identifier
- Active (badge: active / inactive)
- Discovery endpoint (truncated)
- Actions: **Edit**, **Delete**, **Set as active** (hidden if already active)

"Add provider" button links to `/keycloak/new`.

Delete triggers a confirmation before calling `DELETE /admin/oidc-providers/{id}`.

### Add/Edit form (`app/keycloak/new/page.tsx` and `app/keycloak/[id]/page.tsx`)

Client component. Edit page pre-populates from `GET /admin/oidc-providers/{id}` on mount.

Fields (in order):
1. Identifier — text input
2. Discovery endpoint — URL input
3. JSON Web Key Set endpoint — URL input
4. Authorization endpoint — URL input
5. Token endpoint — URL input
6. User endpoint — URL input
7. Logout endpoint — URL input
8. Use Basic auth for token endpoint — checkbox
9. Use nonce — checkbox
10. Nonce size — number input (default 32)
11. State size — number input (default 32)

Footer: **Save** button (calls POST or PUT) and **Cancel** link back to `/keycloak`.

Inline error display if the API returns a problem response.

### Styling

Uses existing shadcn/ui `Button` component and Tailwind v4 utilities. Follows the layout pattern of the other pages (`flex min-h-svh p-6`). Additional shadcn components to install as needed: `input`, `label`, `checkbox`, `table`.

---

## Out of Scope

- Auto-discovery from the discovery endpoint URL (fetching `.well-known/openid-configuration` to populate the other fields automatically)
- Hot-reload of OIDC config without backend restart
- Multiple simultaneously active providers / multi-IDP token validation
