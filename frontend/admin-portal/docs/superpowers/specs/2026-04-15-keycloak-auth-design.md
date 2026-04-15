# Keycloak OIDC Authentication — Design Spec

**Date:** 2026-04-15  
**Branch:** CDMF-114-set-up-initial-project

## Overview

Add Keycloak OIDC authentication to the admin portal. The sidebar footer currently shows a hardcoded user. This feature replaces that with a dynamic login state: a "Sign in" row when unauthenticated, and the real user info when logged in.

## Requirements

- Login is triggered explicitly by clicking "Sign in" in the sidebar footer
- On page load, silently detect an existing Keycloak session (`check-sso`) and auto-authenticate if one exists
- While the silent check is running, show a loading skeleton in the sidebar footer
- When authenticated, show the user's name, email, and avatar initials from the Keycloak JWT
- Clicking the user row opens a dropdown with "Account" and "Log out"
- "Log out" calls `keycloak.logout()` and returns the user to the unauthenticated state
- All Keycloak config values are placeholders, ready for the developer to fill in
- No extra auth libraries beyond `keycloak-js`

## Authentication Flow

1. App loads → `AuthProvider` mounts → calls `keycloak.init({ onLoad: 'check-sso', silentCheckSsoRedirectUri: window.origin + '/silent-check-sso.html' })`
2. While waiting: `isLoading: true` → sidebar footer shows skeleton
3. Silent check resolves:
   - Session found → `authenticated: true`, user decoded from `keycloak.tokenParsed`
   - No session → `authenticated: false`, sidebar shows "Sign in" row
4. User clicks "Sign in" → `keycloak.login()` → redirect to Keycloak hosted login page
5. After Keycloak login → redirect back → init resolves authenticated → user row appears
6. User clicks "Log out" → `keycloak.logout()` → redirects to Keycloak, then back unauthenticated

## Files

| File | Action | Purpose |
|------|--------|---------|
| `lib/keycloak.ts` | Create | Keycloak instance + config placeholders |
| `contexts/auth-context.tsx` | Create | React context, `AuthProvider`, auth state |
| `public/silent-check-sso.html` | Create | Static page for Keycloak's silent SSO iframe |
| `app/layout.tsx` | Update | Wrap app with `AuthProvider` |
| `components/nav-user.tsx` | Update | Read from auth context; show login row or user row |
| `components/app-sidebar.tsx` | Update | Remove hardcoded user data; `NavUser` reads from context |

## Config Placeholders (`lib/keycloak.ts`)

```ts
const KEYCLOAK_URL = "https://YOUR_KEYCLOAK_URL"  // e.g. https://auth.example.com
const KEYCLOAK_REALM = "YOUR_REALM"               // e.g. "master" or "myrealm"
const KEYCLOAK_CLIENT_ID = "YOUR_CLIENT_ID"       // e.g. "admin-portal"
```

## Auth Context Shape

```ts
interface AuthContextValue {
  authenticated: boolean
  isLoading: boolean
  user: {
    name: string              // tokenParsed.name
    email: string             // tokenParsed.email
    username: string          // tokenParsed.preferred_username
    initials: string          // first letter of given_name + family_name
  } | null
  keycloak: Keycloak
}
```

## Sidebar Footer States

**Unauthenticated:** A row styled identically to the user row — an icon, "Sign in" label, and a log-in arrow icon. Clicking calls `keycloak.login()`.

**Loading:** A skeleton row (matching the height of the user row) while `keycloak.init` is resolving.

**Authenticated:** The existing `NavUser` layout — avatar with initials, name, email, ellipsis trigger. Dropdown contains "Account" and "Log out" (red). "Log out" calls `keycloak.logout()`.

## Silent SSO Page (`public/silent-check-sso.html`)

A minimal static HTML page that Keycloak loads in a hidden iframe to silently check for an existing session. It must be served at the exact URI passed to `silentCheckSsoRedirectUri`.

## Dependencies

- `keycloak-js` (to be installed via npm)

## Out of Scope

- Token refresh / silent renewal (not needed for POC)
- Role-based access control
- Server-side session management
- Protected routes / middleware
