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
import { Field, FieldLabel } from "@/components/ui/field"
import { Input } from "@/components/ui/input"
import { useIsMobile } from "@/hooks/use-mobile"
import {
  Check,
  ChevronRight,
  Eye,
  EyeOff,
  MoreVertical,
  Plus,
  X,
} from "lucide-react"
import * as React from "react"
import {
  createOidcProvider,
  deleteOidcProvider,
  type OidcProvider,
  updateOidcProvider,
} from "./actions"

export function OidcProviderList({
  providers,
}: {
  providers: OidcProvider[]
}) {
  const isMobile = useIsMobile()

  const [drawerOpen, setDrawerOpen] = React.useState(false)
  const [editingProvider, setEditingProvider] =
    React.useState<OidcProvider | null>(null)
  const [isSaving, startSave] = React.useTransition()
  const [drawerError, setDrawerError] = React.useState<string | null>(null)

  const [deleteTarget, setDeleteTarget] = React.useState<OidcProvider | null>(
    null
  )
  const [isDeleting, startDelete] = React.useTransition()
  const [deleteError, setDeleteError] = React.useState<string | null>(null)

  function openAdd() {
    setEditingProvider(null)
    setDrawerError(null)
    setDrawerOpen(true)
  }

  function openEdit(provider: OidcProvider) {
    setEditingProvider(provider)
    setDrawerError(null)
    setDrawerOpen(true)
  }

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

  return (
    <>
      <div className="flex w-full max-w-sm flex-col gap-4">
        <div className="flex items-center justify-between">
          <p className="text-sm text-muted-foreground">
            OpenID Connect authenticatieproviders.
          </p>
          <Button variant="outline" size="sm" onClick={openAdd}>
            <Plus />
            Toevoegen
          </Button>
        </div>

        {providers.length === 0 ? (
          <div className="flex flex-col items-center gap-3 py-12 text-center">
            <p className="text-sm text-muted-foreground">
              Nog geen providers geconfigureerd.
            </p>
            <Button variant="outline" size="sm" onClick={openAdd}>
              <Plus />
              Provider toevoegen
            </Button>
          </div>
        ) : (
          <div className="flex flex-col divide-y rounded-lg border">
            {providers.map((provider) => (
              <div
                key={provider.id}
                className="flex items-center gap-3 px-4 py-3 hover:bg-muted/50"
              >
                <button
                  className="flex min-w-0 flex-1 cursor-pointer items-center gap-3 text-left"
                  onClick={() => openEdit(provider)}
                >
                  <div className="min-w-0 flex-1">
                    <p className="text-sm font-medium">{provider.name}</p>
                    <p className="truncate text-xs text-muted-foreground">
                      {provider.issuer}
                    </p>
                  </div>
                  <ChevronRight className="size-4 shrink-0 text-muted-foreground" />
                </button>
                <DropdownMenu>
                  <DropdownMenuTrigger asChild>
                    <Button
                      variant="ghost"
                      size="icon"
                      className="size-7 shrink-0"
                    >
                      <MoreVertical className="size-4" />
                      <span className="sr-only">Acties</span>
                    </Button>
                  </DropdownMenuTrigger>
                  <DropdownMenuContent align="end">
                    <DropdownMenuItem
                      variant="destructive"
                      onClick={() => setDeleteTarget(provider)}
                    >
                      Verwijderen
                    </DropdownMenuItem>
                  </DropdownMenuContent>
                </DropdownMenu>
              </div>
            ))}
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
          {deleteError && (
            <p className="text-sm text-destructive">{deleteError}</p>
          )}
          <AlertDialogFooter>
            <AlertDialogCancel disabled={isDeleting}>
              Annuleren
            </AlertDialogCancel>
            <AlertDialogAction
              onClick={handleDelete}
              disabled={isDeleting}
              className="bg-destructive text-destructive-foreground hover:bg-destructive/90"
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
  const [name, setName] = React.useState(provider?.name ?? "")
  const [issuer, setIssuer] = React.useState(provider?.issuer ?? "")
  const [clientId, setClientId] = React.useState(provider?.clientId ?? "")
  const [clientSecret, setClientSecret] = React.useState(
    provider?.clientSecret ?? ""
  )
  const [showSecret, setShowSecret] = React.useState(false)

  function handleSubmit(e: React.FormEvent<HTMLFormElement>) {
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
        {error && <p className="text-sm text-destructive">{error}</p>}
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
          <div className="relative">
            <Input
              id="provider-client-secret"
              type={showSecret ? "text" : "password"}
              value={clientSecret}
              onChange={(e) => setClientSecret(e.target.value)}
              placeholder={
                provider?.hasSecret
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
