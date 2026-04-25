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
import { Field, FieldError, FieldLabel } from "@/components/ui/field"
import { Input } from "@/components/ui/input"
import { RadioGroup, RadioGroupItem } from "@/components/ui/radio-group"
import { useIsMobile } from "@/hooks/use-mobile"
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
import * as React from "react"
import { z } from "zod"
import {
  createApplication,
  deleteApplication,
  rotateApplicationSecret,
  type ApplicationSetting,
  updateApplication,
} from "./actions"

type RotatePhase = "idle" | "loading" | "success" | "error"

export function ApplicationList({
  applications,
}: {
  applications: ApplicationSetting[]
}) {
  const isMobile = useIsMobile()

  const [drawerOpen, setDrawerOpen] = React.useState(false)
  const [editing, setEditing] = React.useState<ApplicationSetting | null>(null)
  const [isSaving, startSave] = React.useTransition()
  const [drawerError, setDrawerError] = React.useState<string | null>(null)

  const [deleteTarget, setDeleteTarget] =
    React.useState<ApplicationSetting | null>(null)
  const [isDeleting, startDelete] = React.useTransition()
  const [deleteError, setDeleteError] = React.useState<string | null>(null)

  const [rotateTarget, setRotateTarget] =
    React.useState<ApplicationSetting | null>(null)
  const [rotateMode, setRotateMode] = React.useState<"auto" | "manual">("auto")
  const [rotateNewSecret, setRotateNewSecret] = React.useState("")
  const [rotatePhase, setRotatePhase] = React.useState<RotatePhase>("idle")
  const [rotatedSecret, setRotatedSecret] = React.useState("")
  const [rotateError, setRotateError] = React.useState<string | null>(null)
  const [copied, setCopied] = React.useState(false)

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

  function handleSave(data: {
    name: string
    clientId: string
    clientSecret: string
  }) {
    setDrawerError(null)
    const body = {
      name: data.name,
      clientId: data.clientId,
      ...(data.clientSecret ? { clientSecret: data.clientSecret } : {}),
    }
    startSave(async () => {
      try {
        if (editing) {
          await updateApplication(editing.id, body)
        } else {
          await createApplication(body)
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
        await deleteApplication(deleteTarget.id)
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

  async function handleRotate() {
    if (!rotateTarget) return
    setRotatePhase("loading")
    setRotateError(null)
    try {
      const secret = await rotateApplicationSecret(
        rotateTarget.id,
        rotateMode === "manual" && rotateNewSecret.trim()
          ? rotateNewSecret.trim()
          : undefined
      )
      setRotatedSecret(secret)
      setRotatePhase("success")
    } catch (e) {
      setRotateError(
        e instanceof Error
          ? e.message
          : "Roteren mislukt. Probeer het opnieuw."
      )
      setRotatePhase("error")
    }
  }

  async function handleCopy() {
    await navigator.clipboard.writeText(rotatedSecret)
    setCopied(true)
    setTimeout(() => setCopied(false), 2000)
  }

  return (
    <>
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

      <Drawer
        open={drawerOpen}
        onOpenChange={(open) => {
          if (!open && !isSaving) setDrawerOpen(false)
        }}
        direction={isMobile ? "bottom" : "right"}
      >
        <DrawerContent>
          <AppForm
            key={editing?.id ?? "new"}
            app={editing}
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
            <AlertDialogTitle>Applicatie verwijderen</AlertDialogTitle>
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
                <DialogTitle>
                  Secret roteren — {rotateTarget?.name}
                </DialogTitle>
                <DialogDescription>
                  Kies hoe het nieuwe secret wordt aangemaakt.
                </DialogDescription>
              </DialogHeader>
              <div className="flex flex-col gap-4">
                <RadioGroup
                  value={rotateMode}
                  onValueChange={(v: string) =>
                    setRotateMode(v as "auto" | "manual")
                  }
                  disabled={rotatePhase === "loading"}
                  className="gap-2"
                >
                  <label className="flex cursor-pointer items-center gap-2 text-sm">
                    <RadioGroupItem value="auto" id="rotate-auto" />
                    Auto-genereren
                  </label>
                  <label className="flex cursor-pointer items-center gap-2 text-sm">
                    <RadioGroupItem value="manual" id="rotate-manual" />
                    Handmatig invoeren
                  </label>
                </RadioGroup>
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

const appFormSchema = z.object({
  name: z.string().min(1, "Naam is verplicht."),
  clientId: z.string().min(1, "Client ID is verplicht."),
  clientSecret: z.string(),
})

type AppFormFields = z.infer<typeof appFormSchema>
type AppFormErrors = Partial<Record<keyof AppFormFields, string>>

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
  onSave: (data: { name: string; clientId: string; clientSecret: string }) => void
  onCancel: () => void
}) {
  const [name, setName] = React.useState(app?.name ?? "")
  const [clientId, setClientId] = React.useState(app?.clientId ?? "")
  const [clientSecret, setClientSecret] = React.useState(
    app?.clientSecret ?? ""
  )
  const [showSecret, setShowSecret] = React.useState(false)
  const [fieldErrors, setFieldErrors] = React.useState<AppFormErrors>({})

  function handleSubmit(e: React.FormEvent<HTMLFormElement>) {
    e.preventDefault()
    setFieldErrors({})
    const result = appFormSchema.safeParse({ name, clientId, clientSecret })
    if (!result.success) {
      const errors: AppFormErrors = {}
      for (const issue of result.error.issues) {
        const key = issue.path[0] as keyof AppFormFields
        if (!errors[key]) errors[key] = issue.message
      }
      setFieldErrors(errors)
      return
    }
    onSave(result.data)
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
        <Field>
          <FieldLabel htmlFor="app-name">Naam</FieldLabel>
          <Input
            id="app-name"
            value={name}
            onChange={(e) => setName(e.target.value)}
            placeholder="Mijn applicatie"
            disabled={saving}
          />
          <FieldError>{fieldErrors.name}</FieldError>
        </Field>
        <Field>
          <FieldLabel htmlFor="app-client-id">Client ID</FieldLabel>
          <Input
            id="app-client-id"
            value={clientId}
            onChange={(e) => setClientId(e.target.value)}
            placeholder="my-client-id"
            disabled={saving}
          />
          <FieldError>{fieldErrors.clientId}</FieldError>
        </Field>
        <Field>
          <FieldLabel htmlFor="app-client-secret">Client secret</FieldLabel>
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
