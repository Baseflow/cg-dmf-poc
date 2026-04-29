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
import { Field, FieldError, FieldLabel } from "@/components/ui/field"
import { Input } from "@/components/ui/input"
import { SecretInput } from "@/components/ui/secret-input"
import { SettingsTable } from "@/components/settings-table"
import { useIsMobile } from "@/hooks/use-mobile"
import { type ColumnDef } from "@tanstack/react-table"
import { Check, X } from "lucide-react"
import { useCallback, useMemo, useState, useTransition, type FormEvent } from "react"
import {
  createOidcProvider,
  deleteOidcProvider,
  deleteOidcProviders,
  type OidcProvider,
  updateOidcProvider,
} from "./actions"

export function OidcProviderList({
  providers,
}: {
  providers: OidcProvider[]
}) {
  const isMobile = useIsMobile()

  const [drawerOpen, setDrawerOpen] = useState(false)
  const [editingProvider, setEditingProvider] =
    useState<OidcProvider | null>(null)
  const [isSaving, startSave] = useTransition()
  const [drawerError, setDrawerError] = useState<string | null>(null)

  const [deleteTarget, setDeleteTarget] = useState<OidcProvider | null>(
    null
  )
  const [bulkDeleteIds, setBulkDeleteIds] = useState<string[]>([])
  const [bulkDeleteOpen, setBulkDeleteOpen] = useState(false)
  const [isDeleting, startDelete] = useTransition()
  const [deleteError, setDeleteError] = useState<string | null>(null)

  const openAdd = useCallback(() => {
    setEditingProvider(null)
    setDrawerError(null)
    setDrawerOpen(true)
  }, [])

  const openEdit = useCallback((provider: OidcProvider) => {
    setEditingProvider(provider)
    setDrawerError(null)
    setDrawerOpen(true)
  }, [])

  function handleSave(data: {
    name: string
    issuer: string
    clientId: string
    clientSecret: string
  }) {
    setDrawerError(null)
    const body = {
      name: data.name,
      issuer: data.issuer,
      clientId: data.clientId,
      ...(data.clientSecret ? { clientSecret: data.clientSecret } : {}),
    }
    startSave(async () => {
      try {
        if (editingProvider) {
          await updateOidcProvider(editingProvider.id, body)
        } else {
          await createOidcProvider(body)
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

  function handleDelete() {
    if (!deleteTarget) return
    setDeleteError(null)
    startDelete(async () => {
      try {
        await deleteOidcProvider(deleteTarget.id)
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
        await deleteOidcProviders(bulkDeleteIds)
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

  const columns = useMemo<ColumnDef<OidcProvider>[]>(
    () => [
      {
        accessorKey: "name",
        header: "Naam",
        cell: ({ row }) => (
          <span className="font-medium">{row.original.name}</span>
        ),
      },
      {
        accessorKey: "issuer",
        header: "Issuer",
        cell: ({ row }) => (
          <span className="max-w-xs truncate text-muted-foreground">
            {row.original.issuer}
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
                <DropdownMenuItem onClick={() => openEdit(row.original)}>
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
    [openEdit]
  )

  return (
    <>
      <SettingsTable
        data={providers}
        columns={columns}
        description="OpenID Connect authenticatieproviders."
        emptyMessage="Nog geen providers geconfigureerd."
        emptyAddLabel="Provider toevoegen"
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
          <ProviderForm
            key={editingProvider?.id ?? "new"}
            provider={editingProvider}
            saving={isSaving}
            error={drawerError}
            onSave={handleSave}
            onCancel={() => setDrawerOpen(false)}
          />
        </DrawerContent>
      </Drawer>

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
            <AlertDialogTitle>Providers verwijderen</AlertDialogTitle>
            <AlertDialogDescription>
              Weet je zeker dat je{" "}
              <strong>{bulkDeleteIds.length} providers</strong> wilt
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
            <AlertDialogTitle>Provider verwijderen</AlertDialogTitle>
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
              onClick={handleDelete}
              disabled={isDeleting}
            >
              {isDeleting ? "Verwijderen..." : "Verwijderen"}
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </>
  )
}

function ProviderForm({
  provider,
  saving,
  error,
  onSave,
  onCancel,
}: {
  provider: OidcProvider | null
  saving: boolean
  error: string | null
  onSave: (data: {
    name: string
    issuer: string
    clientId: string
    clientSecret: string
  }) => void
  onCancel: () => void
}) {
  const [name, setName] = useState(provider?.name ?? "")
  const [issuer, setIssuer] = useState(provider?.issuer ?? "")
  const [clientId, setClientId] = useState(provider?.clientId ?? "")
  const [clientSecret, setClientSecret] = useState(
    provider?.clientSecret ?? ""
  )
  function handleSubmit(e: FormEvent<HTMLFormElement>) {
    e.preventDefault()
    onSave({ name, issuer, clientId, clientSecret })
  }

  return (
    <>
      <DrawerHeader>
        <DrawerTitle>
          {provider ? provider.name : "Provider toevoegen"}
        </DrawerTitle>
        <DrawerDescription>
          {provider
            ? "Bewerk de OIDC-instellingen."
            : "Configureer een nieuwe OIDC-provider."}
        </DrawerDescription>
      </DrawerHeader>
      <form
        id="provider-form"
        onSubmit={handleSubmit}
        className="flex flex-col gap-4 overflow-y-auto px-4"
      >
        <FieldError>{error}</FieldError>
        <Field>
          <FieldLabel htmlFor="provider-name">Naam</FieldLabel>
          <Input
            id="provider-name"
            value={name}
            onChange={(e) => setName(e.target.value)}
            placeholder="Keycloak productie"
            required
            disabled={saving}
          />
        </Field>
        <Field>
          <FieldLabel htmlFor="provider-issuer">Issuer</FieldLabel>
          <Input
            id="provider-issuer"
            value={issuer}
            onChange={(e) => setIssuer(e.target.value)}
            placeholder="https://auth.example.com/realms/my-realm"
            required
            disabled={saving}
          />
        </Field>
        <Field>
          <FieldLabel htmlFor="provider-client-id">Client ID</FieldLabel>
          <Input
            id="provider-client-id"
            value={clientId}
            onChange={(e) => setClientId(e.target.value)}
            placeholder="my-client-id"
            required
            disabled={saving}
          />
        </Field>
        <Field>
          <FieldLabel htmlFor="provider-client-secret">
            Client secret
          </FieldLabel>
          <SecretInput
            id="provider-client-secret"
            value={clientSecret}
            onChange={(e) => setClientSecret(e.target.value)}
            placeholder={
              provider?.hasSecret
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
          form="provider-form"
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
