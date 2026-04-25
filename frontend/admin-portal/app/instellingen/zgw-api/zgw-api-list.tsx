"use client"

import {
  flexRender,
  getCoreRowModel,
  useReactTable,
  type ColumnDef,
  type RowSelectionState,
} from "@tanstack/react-table"
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
import { Field, FieldLabel } from "@/components/ui/field"
import { Input } from "@/components/ui/input"
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table"
import { useIsMobile } from "@/hooks/use-mobile"
import {
  Check,
  Eye,
  EyeOff,
  MoreHorizontal,
  Plus,
  Trash2,
  X,
} from "lucide-react"
import * as React from "react"
import {
  createZgwApiSetting,
  deleteZgwApiSetting,
  deleteZgwApiSettings,
  type ZgwApiSetting,
  updateZgwApiSetting,
} from "./actions"

export function ZgwApiList({ settings }: { settings: ZgwApiSetting[] }) {
  const isMobile = useIsMobile()

  const [rowSelection, setRowSelection] = React.useState<RowSelectionState>({})

  const [drawerOpen, setDrawerOpen] = React.useState(false)
  const [editingSetting, setEditingSetting] =
    React.useState<ZgwApiSetting | null>(null)
  const [isSaving, startSave] = React.useTransition()
  const [drawerError, setDrawerError] = React.useState<string | null>(null)

  const [deleteTarget, setDeleteTarget] =
    React.useState<ZgwApiSetting | null>(null)
  const [bulkDeleteOpen, setBulkDeleteOpen] = React.useState(false)
  const [isDeleting, startDelete] = React.useTransition()
  const [deleteError, setDeleteError] = React.useState<string | null>(null)

  const selectedIds = React.useMemo(
    () =>
      Object.entries(rowSelection)
        .filter(([, v]) => v)
        .map(([id]) => id),
    [rowSelection]
  )

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

  function handleSave(data: {
    name: string
    baseUrl: string
    clientId: string
    clientSecret: string
  }) {
    setDrawerError(null)
    const body = {
      name: data.name,
      baseUrl: data.baseUrl,
      clientId: data.clientId,
      ...(data.clientSecret ? { clientSecret: data.clientSecret } : {}),
    }
    startSave(async () => {
      try {
        if (editingSetting) {
          await updateZgwApiSetting(editingSetting.id, body)
        } else {
          await createZgwApiSetting(body)
        }
        setDrawerOpen(false)
      } catch (e) {
        setDrawerError(
          e instanceof Error
            ? e.message
            : "Opslaan mislukt. Probeer het opnieuw."
        )
      }
    })
  }

  function handleDeleteSingle() {
    if (!deleteTarget) return
    setDeleteError(null)
    startDelete(async () => {
      try {
        await deleteZgwApiSetting(deleteTarget.id)
        setRowSelection((prev) => {
          const next = { ...prev }
          delete next[deleteTarget.id]
          return next
        })
        setDeleteTarget(null)
      } catch (e) {
        setDeleteError(
          e instanceof Error
            ? e.message
            : "Verwijderen mislukt. Probeer het opnieuw."
        )
      }
    })
  }

  function handleDeleteBulk() {
    setDeleteError(null)
    startDelete(async () => {
      try {
        await deleteZgwApiSettings(selectedIds)
        setRowSelection({})
        setBulkDeleteOpen(false)
      } catch (e) {
        setDeleteError(
          e instanceof Error
            ? e.message
            : "Verwijderen mislukt. Probeer het opnieuw."
        )
      }
    })
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

  return (
    <>
      <div className="flex min-h-svh flex-col gap-4">
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
          if (!open && !isSaving) setDrawerOpen(false)
        }}
        direction={isMobile ? "bottom" : "right"}
      >
        <DrawerContent>
          <SettingForm
            key={editingSetting?.id ?? "new"}
            setting={editingSetting}
            saving={isSaving}
            error={drawerError}
            onSave={handleSave}
            onCancel={() => setDrawerOpen(false)}
          />
        </DrawerContent>
      </Drawer>

      <AlertDialog
        open={deleteTarget !== null}
        onOpenChange={(open) => {
          if (!open && !isDeleting) {
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
            <AlertDialogCancel disabled={isDeleting}>
              Annuleren
            </AlertDialogCancel>
            <AlertDialogAction
              variant="destructive"
              onClick={handleDeleteSingle}
              disabled={isDeleting}
            >
              {isDeleting ? "Verwijderen..." : "Verwijderen"}
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>

      <AlertDialog
        open={bulkDeleteOpen}
        onOpenChange={(open) => {
          if (!open && !isDeleting) {
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
            <AlertDialogCancel disabled={isDeleting}>
              Annuleren
            </AlertDialogCancel>
            <AlertDialogAction
              variant="destructive"
              onClick={handleDeleteBulk}
              disabled={isDeleting}
            >
              {isDeleting
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
        <Field>
          <FieldLabel htmlFor="setting-name">Naam</FieldLabel>
          <Input
            id="setting-name"
            value={name}
            onChange={(e) => setName(e.target.value)}
            placeholder="OpenZaak productie"
            required
            disabled={saving}
          />
        </Field>
        <Field>
          <FieldLabel htmlFor="setting-base-url">Base URL</FieldLabel>
          <Input
            id="setting-base-url"
            value={baseUrl}
            onChange={(e) => setBaseUrl(e.target.value)}
            placeholder="https://openzaak.example.com"
            required
            disabled={saving}
          />
        </Field>
        <Field>
          <FieldLabel htmlFor="setting-client-id">Client ID</FieldLabel>
          <Input
            id="setting-client-id"
            value={clientId}
            onChange={(e) => setClientId(e.target.value)}
            placeholder="client-id"
            required
            disabled={saving}
          />
        </Field>
        <Field>
          <FieldLabel htmlFor="setting-client-secret">Client secret</FieldLabel>
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
        <Button
          type="submit"
          form="setting-form"
          size="sm"
          disabled={saving}
        >
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
