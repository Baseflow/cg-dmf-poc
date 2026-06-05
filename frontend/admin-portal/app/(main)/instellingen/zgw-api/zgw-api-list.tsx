"use client"

import { SecretCell } from "@/components/secret-cell"
import { SettingsTable } from "@/components/settings-table"
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
import {
  Drawer,
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
import {
  Field,
  FieldDescription,
  FieldError,
  FieldLabel,
} from "@/components/ui/field"
import { Input } from "@/components/ui/input"
import { SecretInput } from "@/components/ui/secret-input"
import { useIsMobile } from "@/hooks/use-mobile"
import { type ColumnDef } from "@tanstack/react-table"
import { Check, MoreHorizontal, Plug, X } from "lucide-react"
import {
  useCallback,
  useEffect,
  useMemo,
  useState,
  useTransition,
  type FormEvent,
} from "react"
import {
  createZgwApiSetting,
  deleteZgwApiSetting,
  deleteZgwApiSettings,
  updateZgwApiSetting,
  type ZgwApiSetting,
} from "./actions"

export function ZgwApiList({ settings }: { settings: ZgwApiSetting[] }) {
  const isMobile = useIsMobile()

  const [drawerOpen, setDrawerOpen] = useState(false)
  const [editingSetting, setEditingSetting] = useState<ZgwApiSetting | null>(
    null
  )
  const [isSaving, startSave] = useTransition()
  const [drawerError, setDrawerError] = useState<string | null>(null)
  const [drawerDirty, setDrawerDirty] = useState(false)
  const [closeConfirmOpen, setCloseConfirmOpen] = useState(false)

  const [deleteTarget, setDeleteTarget] = useState<ZgwApiSetting | null>(null)
  const [bulkDeleteIds, setBulkDeleteIds] = useState<string[]>([])
  const [bulkDeleteOpen, setBulkDeleteOpen] = useState(false)
  const [isDeleting, startDelete] = useTransition()
  const [deleteError, setDeleteError] = useState<string | null>(null)

  const handleDrawerCloseAttempt = useCallback(() => {
    if (isSaving) return
    if (drawerDirty) {
      setCloseConfirmOpen(true)
    } else {
      setDrawerOpen(false)
    }
  }, [isSaving, drawerDirty])

  const openAdd = useCallback(() => {
    setEditingSetting(null)
    setDrawerError(null)
    setDrawerDirty(false)
    setDrawerOpen(true)
  }, [])

  const openDetails = useCallback((setting: ZgwApiSetting) => {
    setEditingSetting(setting)
    setDrawerError(null)
    setDrawerDirty(false)
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
        await deleteZgwApiSettings(bulkDeleteIds)
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

  const columns = useMemo<ColumnDef<ZgwApiSetting>[]>(
    () => [
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
        accessorKey: "clientSecret",
        header: "Client secret",
        cell: ({ row }) => (
          <SecretCell
            value={row.original.clientSecret}
            hasSecret={row.original.hasSecret}
          />
        ),
      },
      {
        accessorKey: "updatedAt",
        header: "Bijgewerkt",
        cell: ({ row }) => (
          <span className="text-muted-foreground">
            {new Intl.DateTimeFormat("nl-NL", {
              day: "numeric",
              month: "short",
              year: "numeric",
            }).format(new Date(row.original.updatedAt))}
          </span>
        ),
      },
      {
        id: "actions",
        cell: ({ row }) => (
          <div className="flex justify-end">
            <DropdownMenu>
              <DropdownMenuTrigger asChild>
                <Button variant="ghost" size="icon" className="size-8">
                  <MoreHorizontal className="size-4" />
                  <span className="sr-only">Acties</span>
                </Button>
              </DropdownMenuTrigger>
              <DropdownMenuContent align="end">
                <DropdownMenuItem onClick={() => openDetails(row.original)}>
                  Bewerken
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

  return (
    <>
      <SettingsTable
        data={settings}
        columns={columns}
        description="ZGW API-koppelingsprofielen voor het DMF-systeem."
        emptyMessage="Nog geen ZGW API-instellingen geconfigureerd."
        emptyAddLabel="Instelling toevoegen"
        emptyIcon={<Plug />}
        onAdd={openAdd}
        onBulkDelete={(ids) => {
          setBulkDeleteIds(ids)
          setBulkDeleteOpen(true)
        }}
      />

      <Drawer
        key={isMobile ? "bottom" : "right"}
        open={drawerOpen}
        onOpenChange={(open) => {
          if (!open) handleDrawerCloseAttempt()
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
            onCancel={handleDrawerCloseAttempt}
            onDirtyChange={setDrawerDirty}
          />
        </DrawerContent>
      </Drawer>

      <AlertDialog
        open={closeConfirmOpen}
        onOpenChange={(open) => !open && setCloseConfirmOpen(false)}
      >
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>Wijzigingen verlaten?</AlertDialogTitle>
            <AlertDialogDescription>
              Je hebt niet-opgeslagen wijzigingen. Weet je zeker dat je wilt
              sluiten?
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel>Terug</AlertDialogCancel>
            <AlertDialogAction
              variant="destructive"
              onClick={() => {
                setCloseConfirmOpen(false)
                setDrawerOpen(false)
                setDrawerDirty(false)
              }}
            >
              Sluiten
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>

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
          <FieldError>{deleteError}</FieldError>
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
              <strong>{bulkDeleteIds.length} instellingen</strong> wilt
              verwijderen? Deze actie kan niet ongedaan worden gemaakt.
            </AlertDialogDescription>
          </AlertDialogHeader>
          <FieldError>{deleteError}</FieldError>
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
                : `${bulkDeleteIds.length} verwijderen`}
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
  onDirtyChange,
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
  onDirtyChange?: (dirty: boolean) => void
}) {
  const [name, setName] = useState(setting?.name ?? "")
  const [baseUrl, setBaseUrl] = useState(setting?.baseUrl ?? "")
  const [clientId, setClientId] = useState(setting?.clientId ?? "")
  const [clientSecret, setClientSecret] = useState("")
  const [fieldErrors, setFieldErrors] = useState<{
    name?: string
    baseUrl?: string
    clientId?: string
  }>({})

  const isDirty =
    name !== (setting?.name ?? "") ||
    baseUrl !== (setting?.baseUrl ?? "") ||
    clientId !== (setting?.clientId ?? "") ||
    clientSecret !== ""

  useEffect(() => {
    onDirtyChange?.(isDirty)
  }, [isDirty, onDirtyChange])

  function handleSubmit(e: FormEvent<HTMLFormElement>) {
    e.preventDefault()

    const errors: typeof fieldErrors = {}
    if (!name.trim()) errors.name = "Naam is verplicht."
    if (!baseUrl.trim()) {
      errors.baseUrl = "Base URL is verplicht."
    } else {
      try {
        new URL(baseUrl)
      } catch {
        errors.baseUrl = "Voer een geldige URL in (bijv. https://openzaak.example.com)."
      }
    }
    if (!clientId.trim()) errors.clientId = "Client ID is verplicht."

    if (Object.keys(errors).length > 0) {
      setFieldErrors(errors)
      return
    }

    setFieldErrors({})
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
        noValidate
        className="flex flex-col gap-4 overflow-y-auto px-4"
      >
        <FieldError>{error}</FieldError>
        <Field>
          <FieldLabel htmlFor="setting-name">Naam</FieldLabel>
          <Input
            id="setting-name"
            value={name}
            onChange={(e) => setName(e.target.value)}
            placeholder="OpenZaak productie"
            disabled={saving}
          />
          <FieldDescription>
            Herkenbare naam voor dit koppelingsprofile.
          </FieldDescription>
          <FieldError>{fieldErrors.name}</FieldError>
        </Field>
        <Field>
          <FieldLabel htmlFor="setting-base-url">Base URL</FieldLabel>
          <Input
            id="setting-base-url"
            value={baseUrl}
            onChange={(e) => setBaseUrl(e.target.value)}
            placeholder="https://openzaak.example.com"
            disabled={saving}
            copyable
          />
          <FieldDescription>
            Basis-URL van de ZGW API-implementatie.
          </FieldDescription>
          <FieldError>{fieldErrors.baseUrl}</FieldError>
        </Field>
        <Field>
          <FieldLabel htmlFor="setting-client-id">Client ID</FieldLabel>
          <Input
            id="setting-client-id"
            value={clientId}
            onChange={(e) => setClientId(e.target.value)}
            placeholder="client-id"
            disabled={saving}
            copyable
          />
          <FieldDescription>Client-ID voor JWT-authenticatie.</FieldDescription>
          <FieldError>{fieldErrors.clientId}</FieldError>
        </Field>
        <Field>
          <FieldLabel htmlFor="setting-client-secret">Client secret</FieldLabel>
          <SecretInput
            id="setting-client-secret"
            value={clientSecret}
            onChange={(e) => setClientSecret(e.target.value)}
            placeholder={
              setting?.hasSecret
                ? "Laat leeg om huidig secret te bewaren"
                : "Voer het client secret in"
            }
            disabled={saving}
            copyable
          />
        </Field>
      </form>
      <DrawerFooter>
        <Button type="submit" form="setting-form" size="sm" disabled={saving}>
          <Check />
          {saving ? "Opslaan..." : "Opslaan"}
        </Button>
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
      </DrawerFooter>
    </>
  )
}
