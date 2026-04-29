"use client"

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
import { Field, FieldDescription, FieldError, FieldLabel } from "@/components/ui/field"
import { Input } from "@/components/ui/input"
import { SecretInput } from "@/components/ui/secret-input"
import { SettingsTable } from "@/components/settings-table"
import { useIsMobile } from "@/hooks/use-mobile"
import { type ColumnDef } from "@tanstack/react-table"
import { Check, X } from "lucide-react"
import { useCallback, useMemo, useState, useTransition, type FormEvent } from "react"
import {
  createZgwApiSetting,
  deleteZgwApiSetting,
  deleteZgwApiSettings,
  type ZgwApiSetting,
  updateZgwApiSetting,
} from "./actions"

export function ZgwApiList({ settings }: { settings: ZgwApiSetting[] }) {
  const isMobile = useIsMobile()

  const [drawerOpen, setDrawerOpen] = useState(false)
  const [editingSetting, setEditingSetting] =
    useState<ZgwApiSetting | null>(null)
  const [isSaving, startSave] = useTransition()
  const [drawerError, setDrawerError] = useState<string | null>(null)

  const [deleteTarget, setDeleteTarget] =
    useState<ZgwApiSetting | null>(null)
  const [bulkDeleteIds, setBulkDeleteIds] = useState<string[]>([])
  const [bulkDeleteOpen, setBulkDeleteOpen] = useState(false)
  const [isDeleting, startDelete] = useTransition()
  const [deleteError, setDeleteError] = useState<string | null>(null)

  const openAdd = useCallback(() => {
    setEditingSetting(null)
    setDrawerError(null)
    setDrawerOpen(true)
  }, [])

  const openDetails = useCallback((setting: ZgwApiSetting) => {
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
        id: "actions",
        cell: ({ row }) => (
          <div className="flex justify-end">
            <DropdownMenu>
              <DropdownMenuTrigger asChild>
                <Button variant="ghost" size="sm">
                  Acties
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

  return (
    <>
      <SettingsTable
        data={settings}
        columns={columns}
        description="ZGW API-koppelingsprofielen voor het DMF-systeem."
        emptyMessage="Nog geen ZGW API-instellingen geconfigureerd."
        emptyAddLabel="Instelling toevoegen"
        onAdd={openAdd}
        onBulkDelete={(ids) => {
          setBulkDeleteIds(ids)
          setBulkDeleteOpen(true)
        }}
      />

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
  const [name, setName] = useState(setting?.name ?? "")
  const [baseUrl, setBaseUrl] = useState(setting?.baseUrl ?? "")
  const [clientId, setClientId] = useState(setting?.clientId ?? "")
  const [clientSecret, setClientSecret] = useState(
    setting?.clientSecret ?? ""
  )
  function handleSubmit(e: FormEvent<HTMLFormElement>) {
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
        <FieldError>{error}</FieldError>
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
          <FieldDescription>Herkenbare naam voor dit koppelingsprofile.</FieldDescription>
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
          <FieldDescription>Basis-URL van de ZGW API-implementatie.</FieldDescription>
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
          <FieldDescription>Client-ID voor JWT-authenticatie.</FieldDescription>
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
          />
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
