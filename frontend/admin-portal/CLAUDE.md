# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

---

## Commands

```bash
npm run dev        # Start dev server with Turbopack (fast HMR, no webpack)
npm run build      # Production build (Next.js standard build)
npm run start      # Serve the production build locally
npm run lint       # Run ESLint across the project
npm run format     # Prettier --write on all TS/TSX files
npm run typecheck  # tsc --noEmit (type-check without emitting output)
```

One-off commands not in scripts:

```bash
npx shadcn@latest add <component>   # Add a shadcn/ui component to components/ui/
npx eslint --fix                    # Auto-fix fixable ESLint violations
npx prettier --check "**/*.{ts,tsx}" # Check formatting without writing
```

---

## Repository Context

This is the **admin portal** frontend, living inside the `cg-dmf-poc` monorepo at:

```
/cg-dmf-poc/
└── frontend/
    └── admin-portal/   ← you are here
```

The monorepo root is two levels above. The admin portal is a standalone Next.js app — it does not share `node_modules` or config with sibling packages. All paths in this file are relative to `frontend/admin-portal/`.

---

## MCP Servers

[.mcp.json](.mcp.json) registers two servers, enabled in [.claude/settings.json](.claude/settings.json):

| Server          | Command                           | Purpose                                                                  |
| --------------- | --------------------------------- | ------------------------------------------------------------------------ |
| `next-devtools` | `npx -y next-devtools-mcp@latest` | Next.js introspection — routes, components, config, upgrade helpers      |
| `shadcn`        | `npx shadcn@latest mcp`           | shadcn/ui component discovery, registry lookup, `add` command generation |

Use the **shadcn** MCP tools when searching for components before running `npx shadcn@latest add`. Use the **next-devtools** MCP tools when inspecting routes, checking Next.js config, or accessing Next.js docs.

---

## Architecture

**Next.js 16.1.7** · **React 19** · **TypeScript 5** (strict) · **Tailwind CSS v4** · **shadcn/ui** (radix-nova)

### App Router

File-based routing under [app/](app/). Every file in `app/` is a **React Server Component** by default.

- Add `"use client"` at the top only when a component needs browser APIs, event handlers, hooks (`useState`, `useEffect`, etc.), or the `useTheme` hook.
- Layouts wrap child pages without re-rendering. The root layout is [app/layout.tsx](app/layout.tsx).
- Do not add `"use client"` to layout files — wrap only the interactive subtree.

**Current app structure:**

```
app/
├── globals.css      # Tailwind imports + CSS custom properties (theme tokens)
├── layout.tsx       # Root layout: fonts, ThemeProvider, html/body
└── page.tsx         # Home page (placeholder; replace when building features)
```

### Fonts

[app/layout.tsx](app/layout.tsx) loads two Google Fonts via `next/font/google`:

| Variable      | Font                              | Role                                          |
| ------------- | --------------------------------- | --------------------------------------------- |
| `--font-sans` | Inter (`subsets: ['latin']`)      | Body / UI text (default font via `font-sans`) |
| `--font-mono` | Geist Mono (`subsets: ['latin']`) | Monospace                                     |

`--font-heading` in the theme resolves to `--font-sans` (same as body). The `html` element receives both CSS variable classes and `font-sans` + `antialiased`.

> Note: Geist (sans) is imported as `Geist` from `next/font/google` in layout.tsx but not currently assigned to a variable — only Geist Mono and Inter are active. If you need Geist sans, wire it up to a CSS variable.

---

## Testing

Tests use **Vitest** — not yet set up. When adding tests:

- Config file will be `vitest.config.ts` at the project root.
- Run with `npm run test` (add the script to `package.json`).
- Co-locate test files with source (`*.test.ts` / `*.test.tsx`) or group under `__tests__/`.

---

## Styling

### Tailwind CSS v4

Tailwind v4 uses a **zero-config, CSS-first** approach:

- **No `tailwind.config.js`** — configuration is embedded in CSS via `@theme` blocks.
- **Import syntax:** `@import "tailwindcss"` (not `@tailwind base/components/utilities`).
- **PostCSS plugin:** `@tailwindcss/postcss` (see [postcss.config.mjs](postcss.config.mjs)).
- Custom animations come from `tw-animate-css` (imported via `@import "tw-animate-css"`).

### Theme Tokens ([app/globals.css](app/globals.css))

All design tokens are CSS custom properties defined in `globals.css`:

- **Color space:** oklch throughout (`oklch(L C H)` — lightness, chroma, hue).
- **Light mode:** defined on `:root`.
- **Dark mode:** overridden in `.dark {}` class (applied by `next-themes` via `attribute="class"`).
- **`@custom-variant dark`:** `(&:is(.dark *))` — Tailwind's `dark:` variant targets `.dark` ancestors.
- **`@theme inline`** block maps CSS variables to Tailwind color and radius utilities.

**Token groups:**

| Group   | Tokens                                                                                                                                                                            |
| ------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Layout  | `--background`, `--foreground`, `--border`, `--input`, `--ring`                                                                                                                   |
| Cards   | `--card`, `--card-foreground`, `--popover`, `--popover-foreground`                                                                                                                |
| Brand   | `--primary`, `--primary-foreground`, `--secondary`, `--secondary-foreground`                                                                                                      |
| Neutral | `--muted`, `--muted-foreground`, `--accent`, `--accent-foreground`                                                                                                                |
| Status  | `--destructive`                                                                                                                                                                   |
| Charts  | `--chart-1` through `--chart-5`                                                                                                                                                   |
| Sidebar | `--sidebar`, `--sidebar-foreground`, `--sidebar-primary`, `--sidebar-primary-foreground`, `--sidebar-accent`, `--sidebar-accent-foreground`, `--sidebar-border`, `--sidebar-ring` |
| Radius  | `--radius` (base: `0.625rem`), `--radius-sm/md/lg/xl/2xl/3xl/4xl` (calc multiples)                                                                                                |

**Global base layer:**

```css
@layer base {
  * {
    @apply border-border outline-ring/50;
  }
  body {
    @apply bg-background text-foreground;
  }
  html {
    @apply font-sans;
  }
}
```

### Class Merging

Use `cn()` from [lib/utils.ts](lib/utils.ts) whenever combining conditional classes:

```typescript
import { cn } from "@/lib/utils"

// cn() = twMerge(clsx(...)) — handles conflicts and conditionals
cn("px-4 py-2", isActive && "bg-primary", className)
```

Never concatenate Tailwind classes with string interpolation — use `cn()`.

---

## shadcn/ui Components

Config: [components.json](components.json)

| Setting       | Value                   |
| ------------- | ----------------------- |
| Style         | `radix-nova`            |
| RSC           | `true`                  |
| Icon library  | `lucide` (lucide-react) |
| Base color    | `neutral`               |
| CSS variables | `true`                  |
| Tailwind CSS  | `app/globals.css`       |
| RTL           | `false`                 |

### Adding Components

```bash
npx shadcn@latest add <component-name>
```

Components land in [components/ui/](components/ui/). Always use the shadcn MCP to look up available components and the correct `add` command before running it.

### Import Pattern

```typescript
import { Button } from "@/components/ui/button"
import { Input }  from "@/components/ui/input"
```

### Currently Installed Components

| Component | File                                                 | Variants / Notes                                                                                                                              |
| --------- | ---------------------------------------------------- | --------------------------------------------------------------------------------------------------------------------------------------------- |
| Button    | [components/ui/button.tsx](components/ui/button.tsx) | `default`, `outline`, `secondary`, `ghost`, `destructive`, `link`; sizes `xs`, `sm`, `default`, `lg`, `icon`, `icon-xs`, `icon-sm`, `icon-lg` |

### Writing New Components

- Use **CVA** (`class-variance-authority`) for variant management — see `button.tsx` as the pattern.
- Use **Radix UI** primitives for accessible interactive elements (Slot, Dialog, DropdownMenu, etc.). Radix packages are bundled under `radix-ui` (the unified package).
- shadcn components are **owned code** — edit them directly when you need to deviate from the default.

---

## TypeScript

Config: [tsconfig.json](tsconfig.json)

| Option             | Value                       | Effect                                                       |
| ------------------ | --------------------------- | ------------------------------------------------------------ |
| `strict`           | `true`                      | All strict checks enabled                                    |
| `noEmit`           | `true`                      | Type-check only; Next.js handles transpilation               |
| `moduleResolution` | `bundler`                   | Resolves like Webpack/Vite (supports `exports` field)        |
| `isolatedModules`  | `true`                      | Each file transpilable in isolation (SWC/esbuild compat)     |
| `jsx`              | `react-jsx`                 | No `React` import needed in JSX files                        |
| `paths`            | `@/* → ./*`                 | Root-relative alias for all imports                          |
| `skipLibCheck`     | `true`                      | Skip `.d.ts` type checking in node_modules                   |
| `target`           | `ES2017`                    | Output target (async/await preserved, broad browser support) |
| `lib`              | `dom, dom.iterable, esnext` | Browser + modern JS types                                    |
| `plugins`          | `[{ "name": "next" }]`      | Next.js TypeScript plugin for IDE suggestions                |

**Type-check before committing:**

```bash
npm run typecheck
```

**Rules:**

- Never use `any` — prefer `unknown` and narrow, or use a proper type.
- Avoid type assertions (`as Foo`) except at verified system boundaries.
- Do not use `// @ts-ignore` or `// @ts-expect-error` without a comment explaining why.

---

## ESLint

Config: [eslint.config.mjs](eslint.config.mjs) — ESLint v9 **flat config** format.

### Rule Sets

| Layer             | Package                              | Covers                                                                                                      |
| ----------------- | ------------------------------------ | ----------------------------------------------------------------------------------------------------------- |
| `core-web-vitals` | `eslint-config-next/core-web-vitals` | Next.js best practices + Core Web Vitals (image optimization, font optimization, no sync scripts in Head)   |
| `typescript`      | `eslint-config-next/typescript`      | `@typescript-eslint` parser + plugin rules (no-explicit-any, no-unused-vars, consistent-type-imports, etc.) |

Rules are applied in order; `typescript` extends `core-web-vitals`.

### Ignored Paths

The config uses `globalIgnores` to restrict what's excluded (overriding eslint-config-next's broader defaults):

```
.next/**    out/**    build/**    next-env.d.ts
```

`node_modules/` is excluded automatically by ESLint.

### Running

```bash
npm run lint          # Check all files
npx eslint --fix      # Auto-fix fixable violations
npx eslint <file>     # Check a single file
```

### Guidelines

- Fix lint violations at the source — do not suppress with `// eslint-disable-*`.
- If suppression is truly necessary, add `// eslint-disable-next-line <rule> -- <reason>` on the same line.
- The TypeScript rules will catch `any`, unused variables, and missing return types on exported functions.

---

## Prettier

Config: [.prettierrc](.prettierrc) · Ignore: [.prettierignore](.prettierignore)

### Settings

| Option               | Value                         | Effect                                                    |
| -------------------- | ----------------------------- | --------------------------------------------------------- |
| `semi`               | `false`                       | No semicolons                                             |
| `singleQuote`        | `false`                       | Double quotes                                             |
| `tabWidth`           | `2`                           | 2-space indent                                            |
| `trailingComma`      | `"es5"`                       | Trailing commas in objects/arrays; not in function params |
| `printWidth`         | `80`                          | Soft line wrap at 80 chars                                |
| `endOfLine`          | `"lf"`                        | Unix line endings                                         |
| `plugins`            | `prettier-plugin-tailwindcss` | Auto-sort Tailwind utility classes                        |
| `tailwindStylesheet` | `app/globals.css`             | Resolves custom token sort order                          |
| `tailwindFunctions`  | `["cn", "cva"]`               | Sorts classes inside `cn()` and `cva()` calls             |

### Ignored Paths

`dist/`, `node_modules/`, `.next/`, `.turbo/`, `coverage/`, `pnpm-lock.yaml`, `.pnpm-store/`

### Running

```bash
npm run format                           # Format all TS/TSX files
npx prettier --check "**/*.{ts,tsx}"     # Check without writing
npx prettier --write <file>              # Format a single file
```

### Guidelines

- Always run `npm run format` after generating or editing TS/TSX files.
- Never hand-sort Tailwind class lists — `prettier-plugin-tailwindcss` owns that order.
- ESLint and Prettier are **separate** — no `eslint-plugin-prettier` bridge. Run both independently.
- The class sort order is derived from `app/globals.css`, so custom utilities defined there sort correctly.

---

## Theme System

[components/theme-provider.tsx](components/theme-provider.tsx) wraps the app with `next-themes`.

### Configuration

| Option                      | Value                                            |
| --------------------------- | ------------------------------------------------ |
| `attribute`                 | `"class"` — adds/removes `.dark` on `<html>`     |
| `defaultTheme`              | `"system"` — follows OS preference on first load |
| `enableSystem`              | `true`                                           |
| `disableTransitionOnChange` | `true` — prevents flash during switch            |

### Dark Mode Hotkey

`ThemeHotkey` (a client component inside `ThemeProvider`) listens for keydown events:

- **Key:** `d` toggles dark ↔ light.
- **Guards:** ignored when `event.defaultPrevented`, `event.repeat`, any modifier key (`Meta`, `Ctrl`, `Alt`) is held, or focus is inside a typing target (`INPUT`, `TEXTAREA`, `SELECT`, `contentEditable`).
- The `<html>` element has `suppressHydrationWarning` to prevent React's SSR/CSR mismatch warning caused by `next-themes` injecting the `.dark` class.

### Using the Theme in Components

Read the current theme in client components:

```typescript
"use client"
import { useTheme } from "next-themes"

const { resolvedTheme, setTheme } = useTheme()
```

In Tailwind, use `dark:` prefix for dark-mode variants:

```typescript
<div className="bg-background dark:bg-card text-foreground" />
```

---

## Path Aliases

The `@/*` alias maps to the **project root** (`./`):

```typescript
import { cn }            from "@/lib/utils"
import { Button }        from "@/components/ui/button"
import { ThemeProvider } from "@/components/theme-provider"
```

Never use relative `../../` imports — always use `@/`.

---

## Project Structure

```
admin-portal/
├── app/                     # Next.js App Router (pages, layouts, styles)
│   ├── globals.css          # Tailwind import + all CSS custom properties
│   ├── layout.tsx           # Root layout (fonts, ThemeProvider, html/body)
│   └── page.tsx             # Home route (/)
├── components/
│   ├── theme-provider.tsx   # next-themes wrapper + dark mode hotkey
│   └── ui/                  # shadcn/ui components (owned, editable)
│       └── button.tsx
├── hooks/                   # Custom React hooks (empty — add hooks here)
├── lib/
│   └── utils.ts             # cn() utility (clsx + tailwind-merge)
├── public/                  # Static assets served at /
├── .claude/
│   └── settings.json        # Claude Code: enabled MCP servers + plugins
├── .mcp.json                # MCP server definitions (next-devtools, shadcn)
├── .prettierrc              # Prettier config
├── .prettierignore          # Prettier ignore list
├── components.json          # shadcn/ui config (style, aliases, tailwind)
├── eslint.config.mjs        # ESLint flat config (v9)
├── next.config.mjs          # Next.js config (currently empty baseline)
├── package.json             # Scripts + dependencies
├── postcss.config.mjs       # PostCSS with @tailwindcss/postcss
├── skills-lock.json         # Claude Code skills lock (shadcn version pin)
└── tsconfig.json            # TypeScript config (strict, bundler resolution)
```

---

## Key Dependencies

### Runtime

| Package                    | Version | Role                                          |
| -------------------------- | ------- | --------------------------------------------- |
| `next`                     | 16.1.7  | Framework                                     |
| `react` / `react-dom`      | ^19.2.4 | UI library                                    |
| `next-themes`              | ^0.4.6  | Dark mode                                     |
| `radix-ui`                 | ^1.4.3  | Accessible primitives (unified Radix package) |
| `class-variance-authority` | ^0.7.1  | Component variant management (CVA)            |
| `clsx`                     | ^2.1.1  | Conditional class names                       |
| `tailwind-merge`           | ^3.5.0  | Merge Tailwind classes without conflicts      |
| `lucide-react`             | ^1.8.0  | Icon library                                  |
| `tw-animate-css`           | ^1.4.0  | Tailwind animation utilities                  |
| `shadcn`                   | ^4.2.0  | shadcn CLI (used for `add` commands)          |

### Dev

| Package                       | Version  | Role                                |
| ----------------------------- | -------- | ----------------------------------- |
| `eslint`                      | ^9.39.4  | Linter                              |
| `eslint-config-next`          | 16.1.7   | Next.js + TS lint rules             |
| `prettier`                    | ^3.8.1   | Formatter                           |
| `prettier-plugin-tailwindcss` | ^0.7.2   | Tailwind class sorting              |
| `tailwindcss`                 | ^4.2.1   | CSS framework                       |
| `@tailwindcss/postcss`        | ^4.2.1   | PostCSS integration for Tailwind v4 |
| `typescript`                  | ^5.9.3   | Type checker                        |
| `@types/react`                | ^19.2.14 | React type definitions              |
| `@types/node`                 | ^25.5.0  | Node.js type definitions            |

---

## Claude Workflow Rules

- **Never auto-commit:** Do not run `git commit` unless the user explicitly asks. Make code changes and stop — committing is always the developer's decision.
- **No co-author in commits:** When creating a git commit, do **not** add a `Co-Authored-By` trailer. Commit messages must not include any Claude/AI attribution.
- **No co-author in pull requests:** When creating a pull request, do **not** add Claude as an author or reviewer.
- **Conventional commits:** Use the `type(scope): description` format (e.g. `feat(button): add destructive variant`).
- **Atomic commits:** Split unrelated changes into separate commits.
- **Format before flagging done:** Run `npm run format` and `npm run typecheck` after code changes. Fix any issues before reporting completion.
- **Prefer editing over creating:** Edit existing files rather than creating new ones. Do not add files that don't serve an immediate need.
- **No speculative abstractions:** Only extract helpers or utilities when they are used in at least two places. Three similar lines beats a premature abstraction.
