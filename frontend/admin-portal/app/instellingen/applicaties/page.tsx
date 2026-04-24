"use client"

import * as React from "react"
import {
  Check,
  ChevronRight,
  Copy,
  Eye,
  EyeOff,
  MoreVertical,
  Plus,
  RefreshCw,
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
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog"
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
import { useAuth } from "@/contexts/auth-context"
import { useIsMobile } from "@/hooks/use-mobile"

const API_URL = process.env.NEXT_PUBLIC_API_URL ?? ""

interface ApplicationSetting {
  id: string
  name: string
  clientId: string
  hasSecret: boolean
  clientSecret: string | null
  updatedAt: string
}

type RotatePhase = "idle" | "loading" | "success" | "error"

export default function Page() {
  const { keycloak } = useAuth()
  const isMobile = useIsMobile()

  const [loading, setLoading] = React.useState(true)
  const [error, setError] = React.useState<string | null>(null)
  const [applications, setApplications] = React.useState<ApplicationSetting[]>(
    []
  )

  const [drawerOpen, setDrawerOpen] = React.useState(false)
  const [editing, setEditing] = React.useState<ApplicationSetting | null>(null)
  const [drawerSaving, setDrawerSaving] = React.useState(false)
  const [drawerError, setDrawerError] = React.useState<string | null>(null)

  const [deleteTarget, setDeleteTarget] =
    React.useState<ApplicationSetting | null>(null)
  const [deleteInProgress, setDeleteInProgress] = React.useState(false)

  const [rotateTarget, setRotateTarget] =
    React.useState<ApplicationSetting | null>(null)
  const [rotateMode, setRotateMode] = React.useState<"auto" | "manual">("auto")
  const [rotateNewSecret, setRotateNewSecret] = React.useState("")
  const [rotatePhase, setRotatePhase] = React.useState<RotatePhase>("idle")
  const [rotatedSecret, setRotatedSecret] = React.useState("")
  const [rotateError, setRotateError] = React.useState<string | null>(null)
  const [copied, setCopied] = React.useState(false)

  React.useEffect(() => {
    async function fetchApplications() {
      try {
        await keycloak.updateToken(30)
        const res = await fetch(`${API_URL}/admin/application-settings`, {
          headers: { Authorization: `Bearer ${keycloak.token ?? ""}` },
        })
        if (!res.ok) throw new Error(`HTTP ${res.status}`)
        const data: ApplicationSetting[] = await res.json()
        setApplications(data)
      } catch {
        setError("Kon de applicatie-instellingen niet laden.")
      } finally {
        setLoading(false)
      }
    }
    fetchApplications()
  }, [keycloak, keycloak.token])

  function openAdd() {
    setEditing(null)
    setDrawerError(null)
    setDrawerOpen(true)
  }

  function openEdit(app: ApplicationSetting) {
    setEditing(app)
    setDrawerError(null)
    setDrawerOpen(true)
  }

  function openRotate(app: ApplicationSetting) {
    setRotateTarget(app)
    setRotateMode("auto")
    setRotateNewSecret("")
    setRotatePhase("idle")
    setRotatedSecret("")
    setRotateError(null)
    setCopied(false)
  }

  function closeRotate() {
    if (rotatePhase === "loading") return
    setRotateTarget(null)
  }

  async function handleSave(data: {
    name: string
    clientId: string
    clientSecret: string
  }) {
    setDrawerSaving(true)
    setDrawerError(null)
    try {
      await keycloak.updateToken(30)
      const body: Record<string, string> = {
        name: data.name,
        clientId: data.clientId,
      }
      if (data.clientSecret) body.clientSecret = data.clientSecret

      if (editing) {
        const res = await fetch(
          `${API_URL}/admin/application-settings/${editing.id}`,
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
        const updated: ApplicationSetting = await res.json()
        setApplications((prev) =>
          prev.map((a) => (a.id === updated.id ? updated : a))
        )
      } else {
        const res = await fetch(`${API_URL}/admin/application-settings`, {
          method: "POST",
          headers: {
            Authorization: `Bearer ${keycloak.token ?? ""}`,
            "Content-Type": "application/json",
          },
          body: JSON.stringify(body),
        })
        if (!res.ok) throw new Error(`HTTP ${res.status}`)
        const created: ApplicationSetting = await res.json()
        setApplications((prev) => [...prev, created])
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
    try {
      await keycloak.updateToken(30)
      const res = await fetch(
        `${API_URL}/admin/application-settings/${deleteTarget.id}`,
        {
          method: "DELETE",
          headers: { Authorization: `Bearer ${keycloak.token ?? ""}` },
        }
      )
      if (!res.ok) throw new Error(`HTTP ${res.status}`)
      setApplications((prev) => prev.filter((a) => a.id !== deleteTarget.id))
      setDeleteTarget(null)
    } catch {
      setError("Verwijderen mislukt. Probeer het opnieuw.")
      setDeleteTarget(null)
    } finally {
      setDeleteInProgress(false)
    }
  }

  async function handleRotate() {
    if (!rotateTarget) return
    setRotatePhase("loading")
    setRotateError(null)
    try {
      await keycloak.updateToken(30)
      const bodyObj =
        rotateMode === "manual" && rotateNewSecret.trim()
          ? { newSecret: rotateNewSecret.trim() }
          : {}
      const res = await fetch(
        `${API_URL}/admin/application-settings/${rotateTarget.id}/rotate-secret`,
        {
          method: "POST",
          headers: {
            Authorization: `Bearer ${keycloak.token ?? ""}`,
            "Content-Type": "application/json",
          },
          body: JSON.stringify(bodyObj),
        }
      )
      if (!res.ok) throw new Error(`HTTP ${res.status}`)
      const data: { secret: string } = await res.json()
      setRotatedSecret(data.secret)
      setApplications((prev) =>
        prev.map((a) =>
          a.id === rotateTarget.id ? { ...a, hasSecret: true, clientSecret: data.secret } : a
        )
      )
      setRotatePhase("success")
    } catch {
      setRotateError("Roteren mislukt. Probeer het opnieuw.")
      setRotatePhase("error")
    }
  }

  async function handleCopy() {
    await navigator.clipboard.writeText(rotatedSecret)
    setCopied(true)
    setTimeout(() => setCopied(false), 2000)
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
              Applicatie-instellingen voor client credentials.
            </p>
            <Button variant="outline" size="sm" onClick={openAdd}>
              <Plus />
              Toevoegen
            </Button>
          </div>

          {error && <p className="text-sm text-destructive">{error}</p>}

          {applications.length === 0 ? (
            <div className="flex flex-col items-center gap-3 py-12 text-center">
              <p className="text-sm text-muted-foreground">
                Nog geen applicaties geconfigureerd.
              </p>
              <Button variant="outline" size="sm" onClick={openAdd}>
                <Plus />
                Applicatie toevoegen
              </Button>
            </div>
          ) : (
            <div className="flex flex-col divide-y rounded-lg border">
              {applications.map((app) => (
                <div
                  key={app.id}
                  className="flex items-center gap-3 px-4 py-3 hover:bg-muted/50"
                >
                  <button
                    className="flex min-w-0 flex-1 cursor-pointer items-center gap-3 text-left"
                    onClick={() => openEdit(app)}
                  >
                    <div className="min-w-0 flex-1">
                      <p className="text-sm font-medium">{app.name}</p>
                      <p className="truncate text-xs text-muted-foreground">
                        {app.clientId}
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
                      <DropdownMenuItem onClick={() => openEdit(app)}>
                        Bewerken
                      </DropdownMenuItem>
                      <DropdownMenuItem onClick={() => openRotate(app)}>
                        Secret roteren
                      </DropdownMenuItem>
                      <DropdownMenuItem
                        variant="destructive"
                        onClick={() => setDeleteTarget(app)}
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

      {/* Add / Edit Drawer */}
      <Drawer
        open={drawerOpen}
        onOpenChange={(open) => {
          if (!open && !drawerSaving) setDrawerOpen(false)
        }}
        direction={isMobile ? "bottom" : "right"}
      >
        <DrawerContent>
          <AppForm
            key={editing?.id ?? "new"}
            app={editing}
            saving={drawerSaving}
            error={drawerError}
            onSave={handleSave}
            onCancel={() => setDrawerOpen(false)}
          />
        </DrawerContent>
      </Drawer>

      {/* Delete confirmation */}
      <AlertDialog
        open={deleteTarget !== null}
        onOpenChange={(open) => {
          if (!open && !deleteInProgress) setDeleteTarget(null)
        }}
      >
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>Applicatie verwijderen</AlertDialogTitle>
            <AlertDialogDescription>
              Weet je zeker dat je <strong>{deleteTarget?.name}</strong> wilt
              verwijderen? Deze actie kan niet ongedaan worden gemaakt.
            </AlertDialogDescription>
          </AlertDialogHeader>
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

      {/* Rotate secret dialog */}
      <Dialog
        open={rotateTarget !== null}
        onOpenChange={(open) => {
          if (!open) closeRotate()
        }}
      >
        <DialogContent>
          {rotatePhase === "success" ? (
            <>
              <DialogHeader>
                <DialogTitle>Nieuw secret</DialogTitle>
                <DialogDescription>
                  Dit secret wordt maar één keer getoond. Kopieer het nu.
                </DialogDescription>
              </DialogHeader>
              <div className="flex gap-2">
                <Input
                  readOnly
                  value={rotatedSecret}
                  className="font-mono text-xs"
                />
                <Button
                  type="button"
                  variant="outline"
                  size="icon"
                  onClick={handleCopy}
                  aria-label="Kopieer secret"
                >
                  {copied ? (
                    <Check className="size-4" />
                  ) : (
                    <Copy className="size-4" />
                  )}
                </Button>
              </div>
              <DialogFooter>
                <Button onClick={closeRotate}>Sluiten</Button>
              </DialogFooter>
            </>
          ) : (
            <>
              <DialogHeader>
                <DialogTitle>Secret roteren — {rotateTarget?.name}</DialogTitle>
                <DialogDescription>
                  Kies hoe het nieuwe secret wordt aangemaakt.
                </DialogDescription>
              </DialogHeader>
              <div className="flex flex-col gap-4">
                <div className="flex flex-col gap-2">
                  <label className="flex items-center gap-2 text-sm">
                    <input
                      type="radio"
                      name="rotate-mode"
                      value="auto"
                      checked={rotateMode === "auto"}
                      onChange={() => setRotateMode("auto")}
                      disabled={rotatePhase === "loading"}
                    />
                    Auto-genereren
                  </label>
                  <label className="flex items-center gap-2 text-sm">
                    <input
                      type="radio"
                      name="rotate-mode"
                      value="manual"
                      checked={rotateMode === "manual"}
                      onChange={() => setRotateMode("manual")}
                      disabled={rotatePhase === "loading"}
                    />
                    Handmatig invoeren
                  </label>
                </div>
                {rotateMode === "manual" && (
                  <Input
                    placeholder="Nieuw secret"
                    value={rotateNewSecret}
                    onChange={(e) => setRotateNewSecret(e.target.value)}
                    disabled={rotatePhase === "loading"}
                  />
                )}
                {rotateError && (
                  <p className="text-sm text-destructive">{rotateError}</p>
                )}
              </div>
              <DialogFooter>
                <Button
                  variant="outline"
                  onClick={closeRotate}
                  disabled={rotatePhase === "loading"}
                >
                  Annuleren
                </Button>
                <Button
                  onClick={handleRotate}
                  disabled={
                    rotatePhase === "loading" ||
                    (rotateMode === "manual" && !rotateNewSecret.trim())
                  }
                >
                  {rotatePhase === "loading" ? (
                    <>
                      <RefreshCw className="size-4 animate-spin" />
                      Roteren...
                    </>
                  ) : (
                    <>
                      <RefreshCw className="size-4" />
                      Secret roteren
                    </>
                  )}
                </Button>
              </DialogFooter>
            </>
          )}
        </DialogContent>
      </Dialog>
    </>
  )
}

function AppForm({
  app,
  saving,
  error,
  onSave,
  onCancel,
}: {
  app: ApplicationSetting | null
  saving: boolean
  error: string | null
  onSave: (data: {
    name: string
    clientId: string
    clientSecret: string
  }) => void
  onCancel: () => void
}) {
  const [name, setName] = React.useState(app?.name ?? "")
  const [clientId, setClientId] = React.useState(app?.clientId ?? "")
  const [clientSecret, setClientSecret] = React.useState(
    app?.clientSecret ?? ""
  )
  const [showSecret, setShowSecret] = React.useState(false)

  function handleSubmit(e: React.FormEvent<HTMLFormElement>) {
    e.preventDefault()
    onSave({ name, clientId, clientSecret })
  }

  return (
    <>
      <DrawerHeader>
        <DrawerTitle>{app ? app.name : "Applicatie toevoegen"}</DrawerTitle>
        <DrawerDescription>
          {app
            ? "Bewerk de applicatie-instellingen."
            : "Voeg een nieuwe applicatie toe."}
        </DrawerDescription>
      </DrawerHeader>
      <form
        id="app-form"
        onSubmit={handleSubmit}
        className="flex flex-col gap-4 overflow-y-auto px-4"
      >
        {error && <p className="text-sm text-destructive">{error}</p>}
        <Field label="Naam" htmlFor="app-name">
          <Input
            id="app-name"
            value={name}
            onChange={(e) => setName(e.target.value)}
            placeholder="Mijn applicatie"
            required
            disabled={saving}
          />
        </Field>
        <Field label="Client ID" htmlFor="app-client-id">
          <Input
            id="app-client-id"
            value={clientId}
            onChange={(e) => setClientId(e.target.value)}
            placeholder="my-client-id"
            required
            disabled={saving}
          />
        </Field>
        <Field label="Client secret" htmlFor="app-client-secret">
          <div className="relative">
            <Input
              id="app-client-secret"
              type={showSecret ? "text" : "password"}
              value={clientSecret}
              onChange={(e) => setClientSecret(e.target.value)}
              placeholder={
                app?.hasSecret
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
        <Button type="submit" form="app-form" size="sm" disabled={saving}>
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
