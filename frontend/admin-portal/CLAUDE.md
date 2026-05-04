# CLAUDE.md

## Stack

- **Next.js** (latest) with the App Router — use server components by default, client components only when necessary.
- **React** (latest) — follow current best practices: hooks, composition, minimal state, colocate logic close to where it's used.
- **TypeScript** — strict mode, no `any`.
- **shadcn/ui** — the only UI component library. Always use it for UI primitives. Use the shadcn MCP tools (`shadcn_*`) to look up components, examples, and usage before building UI.
- **ESLint + Prettier** — all code must pass linting and formatting. Do not disable rules without a clear reason.

## File Conventions

Detailed file and naming conventions are defined in [`agent-docs/file-conventions`](agent-docs/file-conventions). These conventions MUST be followed at all times.

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

## Shell helpers

Use this pattern for silent commands with status output:

```bash
# !/bin/bash
run_silent() {
    local description="$1"
    local command="$2"
    local tmp_file=$(mktemp)

    if eval "$command" > "$tmp_file" 2>&1; then
        printf "  ✓ %s\n" "$description"
        rm -f "$tmp_file"
        return 0
    else
        local exit_code=$?
        printf "  ✗ %s\n" "$description"
        cat "$tmp_file"
        rm -f "$tmp_file"
        return $exit_code
    fi
}
```

## Workflow

- **Never auto-commit** — do not run `git commit` unless explicitly asked.
- **No AI attribution** — no `Co-Authored-By` trailers in commits or PRs.
- **Conventional commits** — `type: description` (e.g. `feat: add metrics card`).
- **Atomic commits** — one logical change per commit.
