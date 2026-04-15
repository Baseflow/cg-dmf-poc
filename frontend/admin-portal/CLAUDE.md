# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Commands

```bash
npm run dev        # Start dev server with Turbopack
npm run build      # Production build
npm run lint       # ESLint
npm run typecheck  # TypeScript type check (tsc --noEmit)
npm run format     # Prettier (TS/TSX files)
```

## Context

This portal is part of the `cg-dmf-poc` monorepo (`/frontend/admin-portal`).

## MCP Servers

`.mcp.json` configures two MCP servers for this workspace:

- **next-devtools** — Next.js introspection (routes, components, config)
- **shadcn** — component discovery and registry lookup

## Architecture

This is a **Next.js 16 App Router** project using **React 19**, **Tailwind CSS v4**, and **shadcn/ui**.

### Routing

File-based routing via the `/app` directory (App Router). Components are Server Components by default; add `"use client"` only when interactivity or browser APIs are needed.

### Styling

- Tailwind CSS v4 with the new `@import "tailwindcss"` syntax (not `@tailwind` directives).
- Theme tokens are CSS custom properties defined in [app/globals.css](app/globals.css) using oklch color space, with separate `.dark` overrides.
- Use `cn()` from `@/lib/utils` to merge Tailwind classes (combines `clsx` + `tailwind-merge`).
- Prettier auto-sorts Tailwind classes via `prettier-plugin-tailwindcss`; `cn` and `cva` calls are included in the sort.

### shadcn/ui Components

- Add components with `npx shadcn@latest add <component-name>` — they land in `components/ui/`.
- Import pattern: `import { Button } from "@/components/ui/button"`.
- Components use **CVA** (Class Variance Authority) for variant management and **Radix UI** primitives for accessibility.
- Style: `radix-nova`, RSC enabled.

### Code Style

- No semicolons, double quotes, 2-space indent, trailing commas (ES5), 80-char print width — enforced by Prettier.
- Path alias `@/*` maps to the repo root.

### Theme

[components/theme-provider.tsx](components/theme-provider.tsx) wraps the app with `next-themes`. The `d` key toggles dark/light mode (skipped when focus is inside form inputs).

## Testing

Tests use **Vitest** (not yet set up). Run tests with `npm run test` once configured.

## Claude Workflow Rules

- **Never auto-commit**: Do not run `git commit` unless the user explicitly asks. Make code changes and stop — committing is always the developer's decision.
- **No co-author in commits**: When creating a git commit, do **not** add a `Co-Authored-By` trailer. Commit messages must not include any Claude/AI co-authorship attribution.
- **No co-author in pull requests**: When creating a pull request, do **not** add Claude as an author. Pull requests must not include any Claude/AI co-authorship attribution.
