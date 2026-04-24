"use client"

import * as React from "react"
import {
  Check,
  ChevronRight,
  Eye,
  EyeOff,
  MoreVertical,
  Plus,
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
import { useIsMobile } from "@/hooks/use-mobile"
import { useAuth } from "@/contexts/auth-context"

const API_URL = process.env.NEXT_PUBLIC_API_URL ?? ""

interface OidcProvider {
  id: string
  name: string
  issuer: string
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
  const [providers, setProviders] = React.useState<OidcProvider[]>([])

  const [drawerOpen, setDrawerOpen] = React.useState(false)
  const [editingProvider, setEditingProvider] =
    React.useState<OidcProvider | null>(null)
  const [drawerSaving, setDrawerSaving] = React.useState(false)
  const [drawerError, setDrawerError] = React.useState<string | null>(null)

  const [deleteTarget, setDeleteTarget] = React.useState<OidcProvider | null>(
    null
  )
  const [deleteInProgress, setDeleteInProgress] = React.useState(false)
  const [deleteError, setDeleteError] = React.useState<string | null>(null)

  React.useEffect(() => {
    async function fetchProviders() {
      try {
        await keycloak.updateToken(30)
        const res = await fetch(`${API_URL}/admin/oidc-providers`, {
          headers: { Authorization: `Bearer ${keycloak.token ?? ""}` },
        })
        if (!res.ok) throw new Error(`HTTP ${res.status}`)
        const data: OidcProvider[] = await res.json()
        setProviders(data)
      } catch {
        setError("Kon de OIDC-providers niet laden.")
      } finally {
        setLoading(false)
      }
    }
    fetchProviders()
  }, [keycloak])

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

  async function handleSave(data: {
    name: string
    issuer: string
    clientId: string
    clientSecret: string
  }) {
    setDrawerSaving(true)
    setDrawerError(null)
    try {
      await keycloak.updateToken(30)
      const body: Record<string, string> = {
        name: data.name,
        issuer: data.issuer,
        clientId: data.clientId,
      }
      if (data.clientSecret) body.clientSecret = data.clientSecret

      if (editingProvider) {
        const res = await fetch(
          `${API_URL}/admin/oidc-providers/${editingProvider.id}`,
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
        const updated: OidcProvider = await res.json()
        setProviders((prev) =>
          prev.map((p) => (p.id === updated.id ? updated : p))
        )
      } else {
        const res = await fetch(`${API_URL}/admin/oidc-providers`, {
          method: "POST",
          headers: {
            Authorization: `Bearer ${keycloak.token ?? ""}`,
            "Content-Type": "application/json",
          },
          body: JSON.stringify(body),
        })
        if (!res.ok) throw new Error(`HTTP ${res.status}`)
        const created: OidcProvider = await res.json()
        setProviders((prev) => [...prev, created])
      }
      setDrawerOpen(false)
    } catch {
      setDrawerError("Opslaan mislukt. Probeer het opnieuw.")
    } finally {
      setDrawerSaving(false)
    }
  }

  async function handleDelete() {
    if (!deleteTarget) return
    setDeleteInProgress(true)
    setDeleteError(null)
    try {
      await keycloak.updateToken(30)
      const res = await fetch(
        `${API_URL}/admin/oidc-providers/${deleteTarget.id}`,
        {
          method: "DELETE",
          headers: { Authorization: `Bearer ${keycloak.token ?? ""}` },
        }
      )
      if (!res.ok) throw new Error(`HTTP ${res.status}`)
      setProviders((prev) => prev.filter((p) => p.id !== deleteTarget.id))
      setDeleteTarget(null)
    } catch {
      setDeleteError("Verwijderen mislukt. Probeer het opnieuw.")
    } finally {
      setDeleteInProgress(false)
    }
  }

  if (loading) {
    return (
      <div className="flex min-h-svh p-6">
        <div className="flex w-full max-w-sm flex-col gap-4">
          <div className="flex items-center justify-between">
            <Skeleton className="h-4 w-48" />
            <Skeleton className="h-8 w-28" />
          </div>
          {[0, 1, 2].map((i) => (
            <RowSkeleton key={i} />
          ))}
        </div>
      </div>
    )
  }

  return (
    <>
      <div className="flex min-h-svh p-6">
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

          {error && <p className="text-sm text-destructive">{error}</p>}

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
      </div>

      <Drawer
        open={drawerOpen}
        onOpenChange={(open) => {
          if (!open && !drawerSaving) setDrawerOpen(false)
        }}
        direction={isMobile ? "bottom" : "right"}
      >
        <DrawerContent>
          <ProviderForm
            key={editingProvider?.id ?? "new"}
            provider={editingProvider}
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
            <AlertDialogCancel disabled={deleteInProgress}>
              Annuleren
            </AlertDialogCancel>
            <AlertDialogAction
              onClick={handleDelete}
              disabled={deleteInProgress}
              className="bg-destructive text-destructive-foreground hover:bg-destructive/90"
            >
              {deleteInProgress ? "Verwijderen..." : "Verwijderen"}
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
        <Field label="Naam" htmlFor="provider-name">
          <Input
            id="provider-name"
            value={name}
            onChange={(e) => setName(e.target.value)}
            placeholder="Keycloak productie"
            required
            disabled={saving}
          />
        </Field>
        <Field label="Issuer" htmlFor="provider-issuer">
          <Input
            id="provider-issuer"
            value={issuer}
            onChange={(e) => setIssuer(e.target.value)}
            placeholder="https://auth.example.com/realms/my-realm"
            required
            disabled={saving}
          />
        </Field>
        <Field label="Client ID" htmlFor="provider-client-id">
          <Input
            id="provider-client-id"
            value={clientId}
            onChange={(e) => setClientId(e.target.value)}
            placeholder="my-client-id"
            required
            disabled={saving}
          />
        </Field>
        <Field label="Client secret" htmlFor="provider-client-secret">
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
        <Button type="submit" form="provider-form" size="sm" disabled={saving}>
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

function RowSkeleton() {
  return (
    <div className="flex items-center gap-3 rounded-lg border px-4 py-3">
      <div className="flex-1 space-y-1.5">
        <Skeleton className="h-3.5 w-32" />
        <Skeleton className="h-3 w-48" />
      </div>
    </div>
  )
}
