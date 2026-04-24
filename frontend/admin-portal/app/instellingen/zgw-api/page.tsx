"use client"

import * as React from "react"
import {
  flexRender,
  getCoreRowModel,
  useReactTable,
  type ColumnDef,
  type RowSelectionState,
} from "@tanstack/react-table"
import {
  Check,
  Eye,
  EyeOff,
  MoreHorizontal,
  Plus,
  Trash2,
  X,
} from "lucide-react"
import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
} from "@/components/ui/alert-dialog"
import { Button } from "@/components/ui/button"
import { Checkbox } from "@/components/ui/checkbox"
import {
  Drawer,
  DrawerClose,
  DrawerContent,
  DrawerDescription,
  DrawerFooter,
  DrawerHeader,
  DrawerTitle,
} from "@/components/ui/drawer"
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu"
import { Input } from "@/components/ui/input"
import { Skeleton } from "@/components/ui/skeleton"
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table"
import { useIsMobile } from "@/hooks/use-mobile"
import { useAuth } from "@/contexts/auth-context"

const API_URL = process.env.NEXT_PUBLIC_API_URL ?? ""

interface ZgwApiSetting {
  id: string
  name: string
  baseUrl: string
  clientId: string
  hasSecret: boolean
  clientSecret: string | null
  updatedAt: string
}

export default function Page() {
  const { keycloak } = useAuth()
  const isMobile = useIsMobile()

  const [loading, setLoading] = React.useState(true)
  const [error, setError] = React.useState<string | null>(null)
  const [settings, setSettings] = React.useState<ZgwApiSetting[]>([])
  const [rowSelection, setRowSelection] = React.useState<RowSelectionState>({})

  const [drawerOpen, setDrawerOpen] = React.useState(false)
  const [editingSetting, setEditingSetting] =
    React.useState<ZgwApiSetting | null>(null)
  const [drawerSaving, setDrawerSaving] = React.useState(false)
  const [drawerError, setDrawerError] = React.useState<string | null>(null)

  const [deleteTarget, setDeleteTarget] = React.useState<ZgwApiSetting | null>(
    null
  )
  const [bulkDeleteOpen, setBulkDeleteOpen] = React.useState(false)
  const [deleteInProgress, setDeleteInProgress] = React.useState(false)
  const [deleteError, setDeleteError] = React.useState<string | null>(null)

  const selectedIds = React.useMemo(
    () =>
      Object.entries(rowSelection)
        .filter(([, v]) => v)
        .map(([id]) => id),
    [rowSelection]
  )

  React.useEffect(() => {
    async function fetchSettings() {
      try {
        await keycloak.updateToken(30)
        const res = await fetch(`${API_URL}/admin/zgw-api-settings`, {
          headers: { Authorization: `Bearer ${keycloak.token ?? ""}` },
        })
        if (!res.ok) throw new Error(`HTTP ${res.status}`)
        const data: ZgwApiSetting[] = await res.json()
        setSettings(data)
      } catch {
        setError("Kon de ZGW API-instellingen niet laden.")
      } finally {
        setLoading(false)
      }
    }
    fetchSettings()
  }, [keycloak])

  const openAdd = React.useCallback(() => {
    setEditingSetting(null)
    setDrawerError(null)
    setDrawerOpen(true)
  }, [])

  const openDetails = React.useCallback((setting: ZgwApiSetting) => {
    setEditingSetting(setting)
    setDrawerError(null)
    setDrawerOpen(true)
  }, [])

  async function handleSave(data: {
    name: string
    baseUrl: string
    clientId: string
    clientSecret: string
  }) {
    setDrawerSaving(true)
    setDrawerError(null)
    try {
      await keycloak.updateToken(30)
      const body: Record<string, string> = {
        name: data.name,
        baseUrl: data.baseUrl,
        clientId: data.clientId,
      }
      if (data.clientSecret) body.clientSecret = data.clientSecret

      if (editingSetting) {
        const res = await fetch(
          `${API_URL}/admin/zgw-api-settings/${editingSetting.id}`,
          {
            method: "PUT",
            headers: {
              Authorization: `Bearer ${keycloak.token ?? ""}`,
              "Content-Type": "application/json",
            },
            body: JSON.stringify(body),
          }
        )
        if (!res.ok) throw new Error(`HTTP ${res.status}`)
        const updated: ZgwApiSetting = await res.json()
        setSettings((prev) =>
          prev.map((s) => (s.id === updated.id ? updated : s))
        )
      } else {
        const res = await fetch(`${API_URL}/admin/zgw-api-settings`, {
          method: "POST",
          headers: {
            Authorization: `Bearer ${keycloak.token ?? ""}`,
            "Content-Type": "application/json",
          },
          body: JSON.stringify(body),
        })
        if (!res.ok) throw new Error(`HTTP ${res.status}`)
        const created: ZgwApiSetting = await res.json()
        setSettings((prev) => [...prev, created])
      }
      setDrawerOpen(false)
    } catch {
      setDrawerError("Opslaan mislukt. Probeer het opnieuw.")
    } finally {
      setDrawerSaving(false)
    }
  }

  async function handleDeleteSingle() {
    if (!deleteTarget) return
    setDeleteInProgress(true)
    setDeleteError(null)
    try {
      await keycloak.updateToken(30)
      const res = await fetch(
        `${API_URL}/admin/zgw-api-settings/${deleteTarget.id}`,
        {
          method: "DELETE",
          headers: { Authorization: `Bearer ${keycloak.token ?? ""}` },
        }
      )
      if (!res.ok) throw new Error(`HTTP ${res.status}`)
      setSettings((prev) => prev.filter((s) => s.id !== deleteTarget.id))
      setRowSelection((prev) => {
        const next = { ...prev }
        delete next[deleteTarget.id]
        return next
      })
      setDeleteTarget(null)
    } catch {
      setDeleteError("Verwijderen mislukt. Probeer het opnieuw.")
    } finally {
      setDeleteInProgress(false)
    }
  }

  async function handleDeleteBulk() {
    setDeleteInProgress(true)
    setDeleteError(null)
    try {
      await keycloak.updateToken(30)
      await Promise.all(
        selectedIds.map((id) =>
          fetch(`${API_URL}/admin/zgw-api-settings/${id}`, {
            method: "DELETE",
            headers: { Authorization: `Bearer ${keycloak.token ?? ""}` },
          })
        )
      )
      setSettings((prev) => prev.filter((s) => !selectedIds.includes(s.id)))
      setRowSelection({})
      setBulkDeleteOpen(false)
    } catch {
      setDeleteError("Verwijderen mislukt. Probeer het opnieuw.")
    } finally {
      setDeleteInProgress(false)
    }
  }

  const columns = React.useMemo<ColumnDef<ZgwApiSetting>[]>(
    () => [
      {
        id: "select",
        header: ({ table }) => (
          <Checkbox
            checked={
              table.getIsAllRowsSelected()
                ? true
                : table.getIsSomeRowsSelected()
                  ? "indeterminate"
                  : false
            }
            onCheckedChange={(checked) =>
              table.toggleAllRowsSelected(!!checked)
            }
            aria-label="Selecteer alles"
          />
        ),
        cell: ({ row }) => (
          <Checkbox
            checked={row.getIsSelected()}
            onCheckedChange={(checked) => row.toggleSelected(!!checked)}
            aria-label="Selecteer rij"
          />
        ),
      },
      {
        accessorKey: "name",
        header: "Naam",
        cell: ({ row }) => (
          <span className="font-medium">{row.original.name}</span>
        ),
      },
      {
        accessorKey: "baseUrl",
        header: "Base URL",
        cell: ({ row }) => (
          <span className="max-w-xs truncate text-muted-foreground">
            {row.original.baseUrl}
          </span>
        ),
      },
      {
        accessorKey: "clientId",
        header: "Client ID",
        cell: ({ row }) => (
          <span className="text-muted-foreground">{row.original.clientId}</span>
        ),
      },
      {
        id: "actions",
        cell: ({ row }) => (
          <div className="flex justify-end">
            <DropdownMenu>
              <DropdownMenuTrigger asChild>
                <Button variant="ghost" size="icon" className="size-7">
                  <MoreHorizontal className="size-4" />
                  <span className="sr-only">Acties</span>
                </Button>
              </DropdownMenuTrigger>
              <DropdownMenuContent align="end">
                <DropdownMenuItem onClick={() => openDetails(row.original)}>
                  Details
                </DropdownMenuItem>
                <DropdownMenuItem
                  variant="destructive"
                  onClick={() => setDeleteTarget(row.original)}
                >
                  Verwijderen
                </DropdownMenuItem>
              </DropdownMenuContent>
            </DropdownMenu>
          </div>
        ),
      },
    ],
    [openDetails]
  )

  const table = useReactTable({
    data: settings,
    columns,
    getCoreRowModel: getCoreRowModel(),
    onRowSelectionChange: setRowSelection,
    getRowId: (row) => row.id,
    state: { rowSelection },
  })

  if (loading) {
    return (
      <div className="flex min-h-svh flex-col gap-4 p-6">
        <div className="flex items-center justify-between">
          <Skeleton className="h-4 w-64" />
          <Skeleton className="h-8 w-28" />
        </div>
        <div className="rounded-lg border">
          {[0, 1, 2].map((i) => (
            <TableRowSkeleton key={i} />
          ))}
        </div>
      </div>
    )
  }

  return (
    <>
      <div className="flex min-h-svh flex-col gap-4 p-6">
        <div className="flex items-center justify-between gap-4">
          <p className="text-sm text-muted-foreground">
            ZGW API-koppelingsprofielen voor het DMF-systeem.
          </p>
          <div className="flex items-center gap-2">
            {selectedIds.length > 0 && (
              <Button
                variant="destructive"
                size="sm"
                onClick={() => setBulkDeleteOpen(true)}
              >
                <Trash2 />
                {selectedIds.length} verwijderen
              </Button>
            )}
            <Button variant="outline" size="sm" onClick={openAdd}>
              <Plus />
              Toevoegen
            </Button>
          </div>
        </div>

        {error && <p className="text-sm text-destructive">{error}</p>}

        {settings.length === 0 ? (
          <div className="flex flex-col items-center gap-3 rounded-lg border py-12 text-center">
            <p className="text-sm text-muted-foreground">
              Nog geen ZGW API-instellingen geconfigureerd.
            </p>
            <Button variant="outline" size="sm" onClick={openAdd}>
              <Plus />
              Instelling toevoegen
            </Button>
          </div>
        ) : (
          <div className="rounded-lg border">
            <Table>
              <TableHeader>
                {table.getHeaderGroups().map((headerGroup) => (
                  <TableRow key={headerGroup.id}>
                    {headerGroup.headers.map((header) => (
                      <TableHead key={header.id}>
                        {header.isPlaceholder
                          ? null
                          : flexRender(
                              header.column.columnDef.header,
                              header.getContext()
                            )}
                      </TableHead>
                    ))}
                  </TableRow>
                ))}
              </TableHeader>
              <TableBody>
                {table.getRowModel().rows.map((row) => (
                  <TableRow
                    key={row.id}
                    data-state={row.getIsSelected() ? "selected" : undefined}
                  >
                    {row.getVisibleCells().map((cell) => (
                      <TableCell key={cell.id}>
                        {flexRender(
                          cell.column.columnDef.cell,
                          cell.getContext()
                        )}
                      </TableCell>
                    ))}
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </div>
        )}
      </div>

      <Drawer
        open={drawerOpen}
        onOpenChange={(open) => {
          if (!open && !drawerSaving) setDrawerOpen(false)
        }}
        direction={isMobile ? "bottom" : "right"}
      >
        <DrawerContent>
          <SettingForm
            key={editingSetting?.id ?? "new"}
            setting={editingSetting}
            saving={drawerSaving}
            error={drawerError}
            onSave={handleSave}
            onCancel={() => setDrawerOpen(false)}
          />
        </DrawerContent>
      </Drawer>

      <AlertDialog
        open={deleteTarget !== null}
        onOpenChange={(open) => {
          if (!open && !deleteInProgress) {
            setDeleteTarget(null)
            setDeleteError(null)
          }
        }}
      >
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>Instelling verwijderen</AlertDialogTitle>
            <AlertDialogDescription>
              Weet je zeker dat je <strong>{deleteTarget?.name}</strong> wilt
              verwijderen? Deze actie kan niet ongedaan worden gemaakt.
            </AlertDialogDescription>
          </AlertDialogHeader>
          {deleteError && (
            <p className="text-sm text-destructive">{deleteError}</p>
          )}
          <AlertDialogFooter>
            <AlertDialogCancel disabled={deleteInProgress}>
              Annuleren
            </AlertDialogCancel>
            <AlertDialogAction
              variant="destructive"
              onClick={handleDeleteSingle}
              disabled={deleteInProgress}
            >
              {deleteInProgress ? "Verwijderen..." : "Verwijderen"}
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>

      <AlertDialog
        open={bulkDeleteOpen}
        onOpenChange={(open) => {
          if (!open && !deleteInProgress) {
            setBulkDeleteOpen(false)
            setDeleteError(null)
          }
        }}
      >
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>Instellingen verwijderen</AlertDialogTitle>
            <AlertDialogDescription>
              Weet je zeker dat je{" "}
              <strong>{selectedIds.length} instellingen</strong> wilt
              verwijderen? Deze actie kan niet ongedaan worden gemaakt.
            </AlertDialogDescription>
          </AlertDialogHeader>
          {deleteError && (
            <p className="text-sm text-destructive">{deleteError}</p>
          )}
          <AlertDialogFooter>
            <AlertDialogCancel disabled={deleteInProgress}>
              Annuleren
            </AlertDialogCancel>
            <AlertDialogAction
              variant="destructive"
              onClick={handleDeleteBulk}
              disabled={deleteInProgress}
            >
              {deleteInProgress
                ? "Verwijderen..."
                : `${selectedIds.length} verwijderen`}
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </>
  )
}

function SettingForm({
  setting,
  saving,
  error,
  onSave,
  onCancel,
}: {
  setting: ZgwApiSetting | null
  saving: boolean
  error: string | null
  onSave: (data: {
    name: string
    baseUrl: string
    clientId: string
    clientSecret: string
  }) => void
  onCancel: () => void
}) {
  const [name, setName] = React.useState(setting?.name ?? "")
  const [baseUrl, setBaseUrl] = React.useState(setting?.baseUrl ?? "")
  const [clientId, setClientId] = React.useState(setting?.clientId ?? "")
  const [clientSecret, setClientSecret] = React.useState(
    setting?.clientSecret ?? ""
  )
  const [showSecret, setShowSecret] = React.useState(false)

  function handleSubmit(e: React.FormEvent<HTMLFormElement>) {
    e.preventDefault()
    onSave({ name, baseUrl, clientId, clientSecret })
  }

  return (
    <>
      <DrawerHeader>
        <DrawerTitle>
          {setting ? setting.name : "Instelling toevoegen"}
        </DrawerTitle>
        <DrawerDescription>
          {setting
            ? "Bewerk de ZGW API-koppelingsinstelling."
            : "Configureer een nieuwe ZGW API-koppeling."}
        </DrawerDescription>
      </DrawerHeader>
      <form
        id="setting-form"
        onSubmit={handleSubmit}
        className="flex flex-col gap-4 overflow-y-auto px-4"
      >
        {error && <p className="text-sm text-destructive">{error}</p>}
        <Field label="Naam" htmlFor="setting-name">
          <Input
            id="setting-name"
            value={name}
            onChange={(e) => setName(e.target.value)}
            placeholder="OpenZaak productie"
            required
            disabled={saving}
          />
        </Field>
        <Field label="Base URL" htmlFor="setting-base-url">
          <Input
            id="setting-base-url"
            value={baseUrl}
            onChange={(e) => setBaseUrl(e.target.value)}
            placeholder="https://openzaak.example.com"
            required
            disabled={saving}
          />
        </Field>
        <Field label="Client ID" htmlFor="setting-client-id">
          <Input
            id="setting-client-id"
            value={clientId}
            onChange={(e) => setClientId(e.target.value)}
            placeholder="client-id"
            required
            disabled={saving}
          />
        </Field>
        <Field label="Client secret" htmlFor="setting-client-secret">
          <div className="relative">
            <Input
              id="setting-client-secret"
              type={showSecret ? "text" : "password"}
              value={clientSecret}
              onChange={(e) => setClientSecret(e.target.value)}
              placeholder={
                setting?.hasSecret
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
        </Field>
      </form>
      <DrawerFooter>
        <Button type="submit" form="setting-form" size="sm" disabled={saving}>
          <Check />
          {saving ? "Opslaan..." : "Opslaan"}
        </Button>
        <DrawerClose asChild>
          <Button
            type="button"
            variant="outline"
            size="sm"
            onClick={onCancel}
            disabled={saving}
          >
            <X />
            Annuleren
          </Button>
        </DrawerClose>
      </DrawerFooter>
    </>
  )
}

function Field({
  label,
  htmlFor,
  children,
}: {
  label: string
  htmlFor?: string
  children: React.ReactNode
}) {
  return (
    <div className="flex flex-col gap-1.5">
      <label htmlFor={htmlFor} className="text-xs font-medium">
        {label}
      </label>
      {children}
    </div>
  )
}

function TableRowSkeleton() {
  return (
    <div className="flex items-center gap-4 border-b px-4 py-3 last:border-0">
      <Skeleton className="size-4 rounded" />
      <Skeleton className="h-3.5 w-28" />
      <Skeleton className="h-3.5 w-44" />
      <Skeleton className="h-3.5 w-20" />
    </div>
  )
}
