"use client"

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
import { Checkbox } from "@/components/ui/checkbox"
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
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select"
import { useIsMobile } from "@/hooks/use-mobile"
import { type ColumnDef } from "@tanstack/react-table"
import { Badge } from "@/components/ui/badge"
import { Check, Lock, MoreHorizontal, Plug, X } from "lucide-react"
import {
  useCallback,
  useEffect,
  useMemo,
  useState,
  useTransition,
  type FormEvent,
} from "react"
import {
  createApiKoppeling,
  deleteApiKoppeling,
  deleteApiKoppelingen,
  updateApiKoppeling,
  type ApiKoppeling,
} from "./actions"

const AUTH_TYPE_OPTIONS = [
  { value: "zgw-auth", label: "ZGW authenticatie" },
  { value: "bearer", label: "Bearer token" },
  { value: "none", label: "Geen" },
]

function authTypeLabel(value: string): string {
  return AUTH_TYPE_OPTIONS.find((o) => o.value === value)?.label ?? value
}

const API_TYPE_OPTIONS = [
  { value: "ac", label: "AC — Authorisatie API" },
  { value: "nrc", label: "NRC — Notificaties API" },
  { value: "zrc", label: "ZRC — Zaken API" },
  { value: "ztc", label: "ZTC — Catalogi API" },
  { value: "drc", label: "DRC — Documenten API" },
  { value: "brc", label: "BRC — Besluiten API" },
  { value: "orc", label: "ORC — Overige" },
]

function apiTypeLabel(value: string): string {
  return (
    API_TYPE_OPTIONS.find((o) => o.value === value)
      ?.label.split(" — ")[0] ?? value.toUpperCase()
  )
}

export function ApiKoppelingenList({ settings }: { settings: ApiKoppeling[] }) {
  const isMobile = useIsMobile()

  const [drawerOpen, setDrawerOpen] = useState(false)
  const [editingSetting, setEditingSetting] = useState<ApiKoppeling | null>(
    null
  )
  const [isSaving, startSave] = useTransition()
  const [drawerError, setDrawerError] = useState<string | null>(null)
  const [drawerDirty, setDrawerDirty] = useState(false)
  const [closeConfirmOpen, setCloseConfirmOpen] = useState(false)

  const [deleteTarget, setDeleteTarget] = useState<ApiKoppeling | null>(null)
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

  const openDetails = useCallback((setting: ApiKoppeling) => {
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
    apiType: string
    authType: string
    validationEnabled: boolean
    enabled: boolean
  }) {
    setDrawerError(null)
    const body = {
      name: data.name,
      baseUrl: data.baseUrl,
      clientId: data.clientId,
      apiType: data.apiType,
      authType: data.authType,
      validationEnabled: data.validationEnabled,
      enabled: data.enabled,
      ...(data.clientSecret ? { clientSecret: data.clientSecret } : {}),
    }
    startSave(async () => {
      try {
        if (editingSetting) {
          await updateApiKoppeling(editingSetting.id, body)
        } else {
          await createApiKoppeling(body)
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
        await deleteApiKoppeling(deleteTarget.id)
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
        await deleteApiKoppelingen(bulkDeleteIds)
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

  const columns = useMemo<ColumnDef<ApiKoppeling>[]>(
    () => [
      {
        accessorKey: "name",
        header: "Naam",
        cell: ({ row }) => (
          <div className="flex items-center gap-2">
            <span className="font-medium">{row.original.name}</span>
            {!row.original.enabled && (
              <Badge variant="secondary" className="text-xs text-muted-foreground">
                Uitgeschakeld
              </Badge>
            )}
            {row.original.readonly && (
              <Badge variant="secondary" className="gap-1 text-xs">
                <Lock className="size-3" />
                Omgeving
              </Badge>
            )}
          </div>
        ),
      },
      {
        accessorKey: "apiType",
        header: "Type",
        cell: ({ row }) => (
          <span className="text-muted-foreground">
            {apiTypeLabel(row.original.apiType)}
          </span>
        ),
      },
      {
        accessorKey: "authType",
        header: "Authenticatie",
        cell: ({ row }) => (
          <span className="text-muted-foreground">
            {authTypeLabel(row.original.authType)}
          </span>
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
        accessorKey: "validationEnabled",
        header: "Validatie",
        cell: ({ row }) =>
          row.original.validationEnabled ? (
            <Check className="size-4 text-green-600" />
          ) : (
            <X className="size-4 text-muted-foreground" />
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
                <DropdownMenuItem
                  onClick={() => openDetails(row.original)}
                  disabled={row.original.readonly}
                >
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
        description="API koppelingsprofielen voor het DMF-systeem."
        emptyMessage="Nog geen API koppelingen geconfigureerd."
        emptyAddLabel="Koppeling toevoegen"
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
            <AlertDialogTitle>Koppeling verwijderen</AlertDialogTitle>
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
            <AlertDialogTitle>Koppelingen verwijderen</AlertDialogTitle>
            <AlertDialogDescription>
              Weet je zeker dat je{" "}
              <strong>{bulkDeleteIds.length} koppelingen</strong> wilt
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
  setting: ApiKoppeling | null
  saving: boolean
  error: string | null
  onSave: (data: {
    name: string
    baseUrl: string
    clientId: string
    clientSecret: string
    apiType: string
    authType: string
    validationEnabled: boolean
    enabled: boolean
  }) => void
  onCancel: () => void
  onDirtyChange?: (dirty: boolean) => void
}) {
  const [name, setName] = useState(setting?.name ?? "")
  const [baseUrl, setBaseUrl] = useState(setting?.baseUrl ?? "")
  const [clientId, setClientId] = useState(setting?.clientId ?? "")
  const [clientSecret, setClientSecret] = useState("")
  const [apiType, setApiType] = useState(setting?.apiType ?? API_TYPE_OPTIONS[0].value)
  const [authType, setAuthType] = useState(setting?.authType ?? "zgw-auth")
  const [validationEnabled, setValidationEnabled] = useState(
    setting?.validationEnabled ?? true
  )
  const [enabled, setEnabled] = useState(setting?.enabled ?? true)
  const [fieldErrors, setFieldErrors] = useState<{
    name?: string
    baseUrl?: string
    clientId?: string
    apiType?: string
  }>({})

  const isDirty =
    name !== (setting?.name ?? "") ||
    baseUrl !== (setting?.baseUrl ?? "") ||
    clientId !== (setting?.clientId ?? "") ||
    clientSecret !== "" ||
    apiType !== (setting?.apiType ?? API_TYPE_OPTIONS[0].value) ||
    authType !== (setting?.authType ?? "zgw-auth") ||
    validationEnabled !== (setting?.validationEnabled ?? true) ||
    enabled !== (setting?.enabled ?? true)

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
        errors.baseUrl =
          "Voer een geldige URL in (bijv. https://openzaak.example.com)."
      }
    }
    if (authType !== "none" && authType !== "bearer" && !clientId.trim()) errors.clientId = "Client ID is verplicht."
    if (!apiType) errors.apiType = "Type is verplicht."

    if (Object.keys(errors).length > 0) {
      setFieldErrors(errors)
      return
    }

    setFieldErrors({})
    onSave({ name, baseUrl, clientId, clientSecret, apiType, authType, validationEnabled, enabled })
  }

  return (
    <>
      <DrawerHeader>
        <DrawerTitle>
          {setting ? setting.name : "Koppeling toevoegen"}
        </DrawerTitle>
        <DrawerDescription>
          {setting
            ? "Bewerk de API koppelingsinstelling."
            : "Configureer een nieuwe API koppeling."}
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
          <FieldLabel htmlFor="setting-api-type">Type</FieldLabel>
          <Select
            value={apiType}
            onValueChange={setApiType}
            disabled={saving}
          >
            <SelectTrigger id="setting-api-type">
              <SelectValue placeholder="Selecteer een type" />
            </SelectTrigger>
            <SelectContent>
              {API_TYPE_OPTIONS.map((option) => (
                <SelectItem key={option.value} value={option.value}>
                  {option.label}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
          <FieldDescription>Het type API koppeling.</FieldDescription>
          <FieldError>{fieldErrors.apiType}</FieldError>
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
          <FieldDescription>Basis-URL van de API-implementatie.</FieldDescription>
          <FieldError>{fieldErrors.baseUrl}</FieldError>
        </Field>
        <Field>
          <FieldLabel htmlFor="setting-auth-type">Authenticatie</FieldLabel>
          <Select
            value={authType}
            onValueChange={setAuthType}
            disabled={saving}
          >
            <SelectTrigger id="setting-auth-type">
              <SelectValue placeholder="Selecteer authenticatietype" />
            </SelectTrigger>
            <SelectContent>
              {AUTH_TYPE_OPTIONS.map((option) => (
                <SelectItem key={option.value} value={option.value}>
                  {option.label}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
          <FieldDescription>Authenticatiemethode voor deze koppeling.</FieldDescription>
        </Field>
        {authType !== "none" && authType !== "bearer" && (
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
        )}
        {authType !== "none" && (
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
        )}
        <Field>
          <div className="flex items-center gap-2">
            <Checkbox
              id="setting-validation-enabled"
              checked={validationEnabled}
              onCheckedChange={(checked) =>
                setValidationEnabled(checked === true)
              }
              disabled={saving}
            />
            <FieldLabel htmlFor="setting-validation-enabled">
              Validatie ingeschakeld
            </FieldLabel>
          </div>
          <FieldDescription>
            Valideer URLs tegen deze koppeling wanneer ze voorkomen in het
            proces.
          </FieldDescription>
        </Field>
        <Field>
          <div className="flex items-center gap-2">
            <Checkbox
              id="setting-enabled"
              checked={enabled}
              onCheckedChange={(checked) => setEnabled(checked === true)}
              disabled={saving}
            />
            <FieldLabel htmlFor="setting-enabled">Actief</FieldLabel>
          </div>
          <FieldDescription>
            Schakel deze koppeling in of uit zonder hem te verwijderen.
          </FieldDescription>
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
