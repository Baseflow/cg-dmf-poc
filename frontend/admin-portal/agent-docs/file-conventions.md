# File Conventions

How each special file type is used in this Next.js App Router project.

---

## `page.tsx`

The route entry point. Always a **React Server Component** (no `"use client"`).

Responsibilities:
- Fetch data directly using `createClient()` from `@/lib/supabase/server`
- Call `notFound()` / `redirect()` for guard logic
- Pass fetched data down as props to Client Components

```tsx
export default async function OrderDetailPage({
  params,
}: {
  params: Promise<{ id: string }>
}) {
  const [{ id }, supabase] = await Promise.all([params, createClient()])
  const { data: order } = await supabase.from("orders").select("...").single()
  if (!order) notFound()
  return <OrderDetailsForm orderId={order.id} fields={order} />
}
```

Key rules:
- No `useState`, `useEffect`, or browser APIs — those belong in Client Components
- `params` and `searchParams` are `Promise<...>` in Next.js 16 — always `await` them
- Errors thrown here bubble up to the nearest `error.tsx`

---

## `layout.tsx`

Wraps a route segment and all its children. Persists across navigations within the segment (does not remount).

The `(main)` group layout (`app/(main)/layout.tsx`) handles:
- Auth guard — redirects to `/login` if no session
- Renders the sidebar shell (`AppSidebar`, `SidebarInset`, breadcrumb header)
- Passes `{children}` into the content area

```tsx
export default async function MainLayout({ children }: { children: React.ReactNode }) {
  const supabase = await createClient()
  const { data: { user } } = await supabase.auth.getUser()
  if (!user) redirect("/login")

  return (
    <SidebarProvider>
      <AppSidebar user={...} />
      <SidebarInset>{children}</SidebarInset>
    </SidebarProvider>
  )
}
```

---

## `loading.tsx`

Automatically wraps `page.tsx` in a `<Suspense>` boundary. Rendered while the page's async data fetches.

No manual `<Suspense>` needed — Next.js wires this up automatically.

Pattern used here: **skeleton UI** that mirrors the layout of the real page.

```tsx
export default function OrderDetailLoading() {
  return (
    <div className="p-6">
      <Skeleton className="h-8 w-32" />
      {/* ... */}
    </div>
  )
}
```

- No `"use client"` required — it's a Server Component
- Scoped to the segment; does not affect parent or sibling routes

---

## `error.tsx`

Automatically wraps `page.tsx` in a React `ErrorBoundary`. Rendered when the page throws.

Must be a **Client Component** (`"use client"`) — React error boundaries require it.

Receives two props:
- `error: Error & { digest?: string }` — the thrown error
- `reset: () => void` — re-attempts rendering the segment

```tsx
"use client"

export default function OrderDetailError({ reset }: { error: Error; reset: () => void }) {
  return (
    <div>
      <p>Er is iets misgegaan.</p>
      <button onClick={reset}>Opnieuw proberen</button>
    </div>
  )
}
```

- Parent `error.tsx` files catch errors from child segments
- Does **not** catch errors thrown inside `layout.tsx` of the same segment

---

## `not-found.tsx`

Rendered when `notFound()` is called anywhere in the segment. Works like `error.tsx` but specifically for 404s.

---

## `actions.ts`

Server Actions file. Must start with `"use server"`.

Responsibilities:
- Validate input with Zod before touching the database
- Interact with Supabase using `createClient()` (server-side)
- Call `revalidatePath()` to bust the Next.js cache after mutations
- Call `redirect()` after destructive actions (e.g. delete)

```ts
"use server"

export async function deleteOrder(orderId: number) {
  IdSchema.parse(orderId)                          // 1. validate
  const supabase = await createClient()
  const { error } = await supabase                 // 2. mutate
    .from("orders").delete().eq("id", orderId)
  if (error) throw new Error(error.message)
  revalidatePath("/orders")                        // 3. revalidate
  redirect("/orders")                              // 4. navigate
}
```

Actions that return data to the UI (e.g. form state) use the `(prevState, formData)` signature and are bound via `useActionState`.

Actions called directly from event handlers (e.g. `onClick`) are plain `async` functions — no `FormData` involved.

---

## Client Component files (e.g. `order-items-table.tsx`)

Any file with `"use client"` at the top. Used when a component needs:
- `useState`, `useReducer`, `useTransition`
- Browser event handlers (`onClick`, `onChange`)
- `useEffect` / refs

Pattern:
- Receives serializable data as props from the parent RSC (`page.tsx`)
- Imports and calls Server Actions directly — Next.js handles the RPC boundary
- Uses `useTransition` to track pending state without blocking the UI

```tsx
"use client"

import { deleteOrderItems } from "./actions"

export function OrderItemsTable({ items }: { items: OrderItemRow[] }) {
  const [isPending, startTransition] = useTransition()

  function handleDelete(id: string) {
    startTransition(() => deleteOrderItems([id]))
  }
  // ...
}
```

---

## `columns.tsx`

TanStack Table column definitions. Usually a plain module (no `"use client"` unless columns render interactive cells).

Exports a `getColumns(callbacks)` factory when columns need to call back into the parent component (e.g. to open a dialog or trigger a transition):

```tsx
export function getColumns({ onDelete, onUpdateQuantity, isPending }) {
  return [
    columnHelper.accessor("quantity", { ... }),
    columnHelper.display({
      id: "actions",
      cell: ({ row }) => (
        <button onClick={() => onDelete(row.original.id)}>Delete</button>
      ),
    }),
  ]
}
```

---

## Runtime layering

```
layout.tsx          ← persists across navigations, auth guard lives here
  └── error.tsx     ← ErrorBoundary: catches throws from page + children
        └── loading.tsx  ← Suspense: shown while page awaits data
              └── page.tsx     ← async RSC: fetches data, renders shell
                    └── [client components]  ← interactivity, call actions
                          └── actions.ts     ← server-side mutations
```
