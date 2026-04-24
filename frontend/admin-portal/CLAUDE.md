# CLAUDE.md

## Stack

- **Next.js** (latest) with the App Router — use server components by default, client components only when necessary.
- **React** (latest) — follow current best practices: hooks, composition, minimal state, colocate logic close to where it's used.
- **TypeScript** — strict mode, no `any`.
- **shadcn/ui** — the only UI component library. Always use it for UI primitives. Use the shadcn MCP tools (`shadcn_*`) to look up components, examples, and usage before building UI.
- **ESLint + Prettier** — all code must pass linting and formatting. Do not disable rules without a clear reason.

## Code Standards

- Follow the latest React and Next.js best practices at all times.
- Prefer Server Components and server-side data fetching. Avoid unnecessary `"use client"` boundaries.
- Keep components small, focused, and composable.
- Use file-based routing conventions from the Next.js App Router.
- Co-locate related code (components, hooks, utils) near their feature.

## File Structure

```
app/                          # Next.js App Router — routes and layouts
│
├── layout.tsx                # Root layout (fonts, providers, sidebar)
├── page.tsx                  # Root page (redirects or dashboard)
│
└── <feature>/                # One directory per feature/section
    ├── layout.tsx            # Optional: shared layout for feature sub-pages
    ├── page.tsx              # Server component — data fetching + composition
    ├── loading.tsx           # Suspense skeleton shown while page loads
    ├── error.tsx             # Error boundary for the route
    ├── actions.ts            # Server Actions (mutations, form submissions)
    └── <component>.tsx       # Feature-specific client component(s)

components/                   # Shared, reusable components
├── ui/                       # shadcn/ui primitives (auto-generated, do not edit)
└── *.tsx                     # App-wide shared components (sidebar, breadcrumb, …)

hooks/                        # Shared custom React hooks
lib/                          # Utilities, config, and non-React helpers
types/                        # Global TypeScript type declarations
```

### Feature conventions

- **Route = feature folder** under `app/`. Keep feature-specific components co-located inside the route folder.
- **`page.tsx`** is always a Server Component. Fetch data here; pass it as props to child components.
- **`actions.ts`** holds all Server Actions for that route (form submissions, mutations).
- **Client components** (`"use client"`) are only introduced when interactivity is required and live next to the page that uses them.
- **Shared components** go in `components/` only when used by two or more features.

## Workflow

- **Never auto-commit** — do not run `git commit` unless explicitly asked.
- **No AI attribution** — no `Co-Authored-By` trailers in commits or PRs.
- **Conventional commits** — `type: description` (e.g. `feat: add metrics card`).
- **Atomic commits** — one logical change per commit.
