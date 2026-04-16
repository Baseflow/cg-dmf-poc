# Keycloak OIDC Authentication Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add Keycloak OIDC authentication to the admin portal sidebar — showing a "Sign in" row when unauthenticated and the real user info when logged in.

**Architecture:** A `keycloak-js` instance is created once inside a React `AuthProvider` context. On mount it silently checks for an existing Keycloak session (`check-sso`). The `NavUser` component reads from the auth context and renders three states: loading skeleton, unauthenticated login row, or authenticated user row with a logout dropdown.

**Tech Stack:** Next.js 16 (App Router), React 19, TypeScript, `keycloak-js`, Tailwind CSS, shadcn/ui

---

## File Map

| File | Action | Responsibility |
|------|--------|----------------|
| `package.json` | Modify | Add `keycloak-js` dependency |
| `lib/keycloak-config.ts` | Create | Keycloak config placeholders (URL, realm, client ID) |
| `contexts/auth-context.tsx` | Create | `AuthProvider` + `useAuth` hook, owns all auth state |
| `public/silent-check-sso.html` | Create | Static iframe page required by Keycloak silent SSO |
| `app/layout.tsx` | Modify | Wrap app with `AuthProvider` |
| `components/nav-user.tsx` | Modify | Read from `useAuth`; render loading / login / user states |
| `components/app-sidebar.tsx` | Modify | Remove hardcoded `user` data; remove `user` prop from `<NavUser>` |

---

## Task 1: Install keycloak-js

**Files:**
- Modify: `package.json`

- [ ] **Step 1: Install the package**

```bash
cd /Users/janderk/Baseflow/projects/cg-dmf-poc/frontend/admin-portal
npm install keycloak-js
```

Expected output: `added 1 package` (or similar). No errors.

- [ ] **Step 2: Verify it appears in package.json**

Open `package.json` and confirm `"keycloak-js"` is listed under `"dependencies"`.

- [ ] **Step 3: Commit**

```bash
git add package.json package-lock.json
git commit -m "feat(auth): install keycloak-js"
```

---

## Task 2: Create Keycloak config + silent SSO page

**Files:**
- Create: `lib/keycloak-config.ts`
- Create: `public/silent-check-sso.html`

- [ ] **Step 1: Create `lib/keycloak-config.ts`**

```ts
// ─── Fill in these three values to connect to your Keycloak instance ───────
export const KEYCLOAK_URL = "https://YOUR_KEYCLOAK_URL"  // e.g. https://auth.example.com
export const KEYCLOAK_REALM = "YOUR_REALM"               // e.g. "master" or "myrealm"
export const KEYCLOAK_CLIENT_ID = "YOUR_CLIENT_ID"       // e.g. "admin-portal"
// ────────────────────────────────────────────────────────────────────────────
```

- [ ] **Step 2: Create `public/silent-check-sso.html`**

This file is loaded by Keycloak in a hidden iframe to silently check for an existing session. It must exist at the root of your public directory.

```html
<html>
  <body>
    <script>
      parent.postMessage(location.href, location.origin)
    </script>
  </body>
</html>
```

- [ ] **Step 3: Commit**

```bash
git add lib/keycloak-config.ts public/silent-check-sso.html
git commit -m "feat(auth): add keycloak config placeholders and silent SSO page"
```

---

## Task 3: Create the auth context

**Files:**
- Create: `contexts/auth-context.tsx`

This is the core of the auth system. It creates a single `Keycloak` instance per session (using `useState` initializer so it's client-only), runs `keycloak.init` once on mount (guarded against React strict mode double-run), and exposes `{ authenticated, isLoading, user, keycloak }` via context.

- [ ] **Step 1: Create `contexts/auth-context.tsx`**

```tsx
"use client"

import * as React from "react"
import Keycloak from "keycloak-js"
import {
  KEYCLOAK_URL,
  KEYCLOAK_REALM,
  KEYCLOAK_CLIENT_ID,
} from "@/lib/keycloak-config"

interface User {
  name: string
  email: string
  username: string
  initials: string
}

interface AuthContextValue {
  authenticated: boolean
  isLoading: boolean
  user: User | null
  keycloak: Keycloak
}

const AuthContext = React.createContext<AuthContextValue | null>(null)

export function AuthProvider({ children }: { children: React.ReactNode }) {
  // useState initializer only runs client-side (this is a "use client" component)
  const [keycloak] = React.useState(
    () =>
      new Keycloak({
        url: KEYCLOAK_URL,
        realm: KEYCLOAK_REALM,
        clientId: KEYCLOAK_CLIENT_ID,
      })
  )
  const [authenticated, setAuthenticated] = React.useState(false)
  const [isLoading, setIsLoading] = React.useState(true)
  const [user, setUser] = React.useState<User | null>(null)

  // Guard against React strict mode double-invocation
  const initialized = React.useRef(false)

  React.useEffect(() => {
    if (initialized.current) return
    initialized.current = true

    keycloak
      .init({
        onLoad: "check-sso",
        silentCheckSsoRedirectUri:
          window.location.origin + "/silent-check-sso.html",
      })
      .then((auth) => {
        setAuthenticated(auth)
        if (auth && keycloak.tokenParsed) {
          const parsed = keycloak.tokenParsed as Record<string, string>
          const givenName = parsed["given_name"] ?? ""
          const familyName = parsed["family_name"] ?? ""
          setUser({
            name: parsed["name"] ?? parsed["preferred_username"] ?? "",
            email: parsed["email"] ?? "",
            username: parsed["preferred_username"] ?? "",
            initials:
              (givenName[0] ?? "").toUpperCase() +
              (familyName[0] ?? "").toUpperCase(),
          })
        }
        setIsLoading(false)
      })
      .catch(() => {
        setIsLoading(false)
      })
  }, [keycloak])

  return (
    <AuthContext.Provider value={{ authenticated, isLoading, user, keycloak }}>
      {children}
    </AuthContext.Provider>
  )
}

export function useAuth(): AuthContextValue {
  const context = React.useContext(AuthContext)
  if (!context) {
    throw new Error("useAuth must be used within an AuthProvider")
  }
  return context
}
```

- [ ] **Step 2: Verify TypeScript compiles**

```bash
npm run typecheck
```

Expected: no errors. If `keycloak.tokenParsed` type errors appear, the cast to `Record<string, string>` on line above handles it.

- [ ] **Step 3: Commit**

```bash
git add contexts/auth-context.tsx
git commit -m "feat(auth): add AuthProvider context with keycloak-js check-sso"
```

---

## Task 4: Wrap the app with AuthProvider

**Files:**
- Modify: `app/layout.tsx`

- [ ] **Step 1: Update `app/layout.tsx`**

Replace the entire file content:

```tsx
import { Geist_Mono, Inter } from "next/font/google"

import "./globals.css"
import { ThemeProvider } from "@/components/theme-provider"
import { TooltipProvider } from "@/components/ui/tooltip"
import { AuthProvider } from "@/contexts/auth-context"
import { cn } from "@/lib/utils"

const inter = Inter({ subsets: ["latin"], variable: "--font-sans" })

const fontMono = Geist_Mono({
  subsets: ["latin"],
  variable: "--font-mono",
})

export default function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode
}>) {
  return (
    <html
      lang="en"
      suppressHydrationWarning
      className={cn(
        "antialiased",
        fontMono.variable,
        "font-sans",
        inter.variable
      )}
    >
      <body>
        <ThemeProvider>
          <TooltipProvider>
            <AuthProvider>{children}</AuthProvider>
          </TooltipProvider>
        </ThemeProvider>
      </body>
    </html>
  )
}
```

- [ ] **Step 2: Verify TypeScript compiles**

```bash
npm run typecheck
```

Expected: no errors.

- [ ] **Step 3: Commit**

```bash
git add app/layout.tsx
git commit -m "feat(auth): wrap app with AuthProvider"
```

---

## Task 5: Update NavUser to use auth context

**Files:**
- Modify: `components/nav-user.tsx`

Remove the `user` prop entirely. The component now reads from `useAuth()` and renders three states.

- [ ] **Step 1: Replace `components/nav-user.tsx`**

```tsx
"use client"

import * as React from "react"
import {
  Avatar,
  AvatarFallback,
} from "@/components/ui/avatar"
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuGroup,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu"
import { Skeleton } from "@/components/ui/skeleton"
import {
  SidebarMenu,
  SidebarMenuButton,
  SidebarMenuItem,
  useSidebar,
} from "@/components/ui/sidebar"
import {
  CircleUserRoundIcon,
  EllipsisVerticalIcon,
  LogInIcon,
  LogOutIcon,
} from "lucide-react"
import { useAuth } from "@/contexts/auth-context"

export function NavUser() {
  const { isMobile } = useSidebar()
  const { authenticated, isLoading, user, keycloak } = useAuth()

  // ── Loading state ────────────────────────────────────────────────────────
  if (isLoading) {
    return (
      <SidebarMenu>
        <SidebarMenuItem>
          <SidebarMenuButton size="lg" disabled>
            <Skeleton className="h-8 w-8 rounded-lg" />
            <div className="grid flex-1 gap-1">
              <Skeleton className="h-3 w-24 rounded" />
              <Skeleton className="h-3 w-32 rounded" />
            </div>
          </SidebarMenuButton>
        </SidebarMenuItem>
      </SidebarMenu>
    )
  }

  // ── Unauthenticated state ─────────────────────────────────────────────────
  if (!authenticated || !user) {
    return (
      <SidebarMenu>
        <SidebarMenuItem>
          <SidebarMenuButton
            size="lg"
            onClick={() => keycloak.login()}
            tooltip="Sign in"
          >
            <Avatar className="h-8 w-8 rounded-lg">
              <AvatarFallback className="rounded-lg bg-muted">
                <CircleUserRoundIcon className="size-4 text-muted-foreground" />
              </AvatarFallback>
            </Avatar>
            <div className="grid flex-1 text-left text-sm leading-tight">
              <span className="truncate font-medium">Sign in</span>
              <span className="truncate text-xs text-muted-foreground">
                Click to log in
              </span>
            </div>
            <LogInIcon className="ml-auto size-4 text-muted-foreground" />
          </SidebarMenuButton>
        </SidebarMenuItem>
      </SidebarMenu>
    )
  }

  // ── Authenticated state ───────────────────────────────────────────────────
  return (
    <SidebarMenu>
      <SidebarMenuItem>
        <DropdownMenu>
          <DropdownMenuTrigger asChild>
            <SidebarMenuButton
              size="lg"
              className="data-[state=open]:bg-sidebar-accent data-[state=open]:text-sidebar-accent-foreground"
            >
              <Avatar className="h-8 w-8 rounded-lg">
                <AvatarFallback className="rounded-lg">
                  {user.initials || user.name.slice(0, 2).toUpperCase()}
                </AvatarFallback>
              </Avatar>
              <div className="grid flex-1 text-left text-sm leading-tight">
                <span className="truncate font-medium">{user.name}</span>
                <span className="truncate text-xs text-muted-foreground">
                  {user.email}
                </span>
              </div>
              <EllipsisVerticalIcon className="ml-auto size-4" />
            </SidebarMenuButton>
          </DropdownMenuTrigger>
          <DropdownMenuContent
            className="w-(--radix-dropdown-menu-trigger-width) min-w-56 rounded-lg"
            side={isMobile ? "bottom" : "right"}
            align="end"
            sideOffset={4}
          >
            <DropdownMenuLabel className="p-0 font-normal">
              <div className="flex items-center gap-2 px-1 py-1.5 text-left text-sm">
                <Avatar className="h-8 w-8 rounded-lg">
                  <AvatarFallback className="rounded-lg">
                    {user.initials || user.name.slice(0, 2).toUpperCase()}
                  </AvatarFallback>
                </Avatar>
                <div className="grid flex-1 text-left text-sm leading-tight">
                  <span className="truncate font-medium">{user.name}</span>
                  <span className="truncate text-xs text-muted-foreground">
                    {user.email}
                  </span>
                </div>
              </div>
            </DropdownMenuLabel>
            <DropdownMenuSeparator />
            <DropdownMenuGroup>
              <DropdownMenuItem>
                <CircleUserRoundIcon />
                Account
              </DropdownMenuItem>
            </DropdownMenuGroup>
            <DropdownMenuSeparator />
            <DropdownMenuItem
              className="text-destructive focus:text-destructive"
              onClick={() => keycloak.logout()}
            >
              <LogOutIcon />
              Log out
            </DropdownMenuItem>
          </DropdownMenuContent>
        </DropdownMenu>
      </SidebarMenuItem>
    </SidebarMenu>
  )
}
```

- [ ] **Step 2: Verify TypeScript compiles**

```bash
npm run typecheck
```

Expected: no errors.

- [ ] **Step 3: Commit**

```bash
git add components/nav-user.tsx
git commit -m "feat(auth): update NavUser to read from auth context with 3 states"
```

---

## Task 6: Remove hardcoded user from AppSidebar

**Files:**
- Modify: `components/app-sidebar.tsx`

`NavUser` no longer accepts a `user` prop, so remove it from `AppSidebar`.

- [ ] **Step 1: Remove the hardcoded `user` from the `data` object and update `<NavUser>`**

In `components/app-sidebar.tsx`, find the `data` object at the top and remove the entire `user` key:

```ts
// Remove this block from the `data` object:
user: {
  name: "Baseflow",
  email: "hello@baseflow.com",
  avatar: <BaseflowAvatar />,
},
```

Then in the `AppSidebar` JSX, change:

```tsx
// Before:
<NavUser user={data.user} />

// After:
<NavUser />
```

- [ ] **Step 2: Verify TypeScript compiles**

```bash
npm run typecheck
```

Expected: no errors. TypeScript will flag the old prop usage so this confirms the removal worked.

- [ ] **Step 3: Commit**

```bash
git add components/app-sidebar.tsx
git commit -m "feat(auth): remove hardcoded user from AppSidebar"
```

---

## Task 7: Smoke test in the browser

There is no test framework in this project. Verify manually.

- [ ] **Step 1: Start the dev server**

```bash
npm run dev
```

Expected: server starts on `http://localhost:3000` (or next available port), no compile errors in terminal.

- [ ] **Step 2: Check the unauthenticated state**

Open `http://localhost:3000/dashboard` in a browser where you are **not** logged into Keycloak.

Expected:
- Sidebar footer shows a "Sign in" row with a user icon and login arrow icon
- After ~1 second (silent SSO check), the skeleton disappears and the sign-in row appears
- No console errors (other than expected Keycloak network errors pointing to `YOUR_KEYCLOAK_URL`)

- [ ] **Step 3: Verify the loading skeleton**

The skeleton appears during the ~1s `keycloak.init` call. It shows two stacked grey bars inside a button-sized row.

- [ ] **Step 4: Fill in real Keycloak values (your step)**

In `lib/keycloak-config.ts`, replace the three placeholder strings with your actual Keycloak URL, realm, and client ID.

- [ ] **Step 5: Test the full login flow**

Click "Sign in" in the sidebar → you are redirected to Keycloak → log in → you are redirected back → the sidebar footer shows your name and email.

- [ ] **Step 6: Test logout**

Click the user row → dropdown opens → click "Log out" → you are redirected to Keycloak → redirected back → sidebar shows "Sign in" again.
