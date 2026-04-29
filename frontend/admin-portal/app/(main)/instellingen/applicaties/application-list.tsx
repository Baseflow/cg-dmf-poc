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
import { Label } from "@/components/ui/label"
import { RadioGroup, RadioGroupItem } from "@/components/ui/radio-group"
import { SecretInput } from "@/components/ui/secret-input"
import { SettingsTable } from "@/components/settings-table"
import { useIsMobile } from "@/hooks/use-mobile"
import { type ColumnDef } from "@tanstack/react-table"
import {
  Check,
  Copy,
  MoreHorizontal,
  RefreshCw,
  X,
} from "lucide-react"
import * as React from "react"
import { z } from "zod"
import {
  createApplication,
  deleteApplication,
  deleteApplications,
  rotateApplicationSecret,
  type ApplicationSetting,
  updateApplication,
} from "./actions"

type RotatePhase = "idle" | "success" | "error"

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
  const [bulkDeleteIds, setBulkDeleteIds] = React.useState<string[]>([])
  const [bulkDeleteOpen, setBulkDeleteOpen] = React.useState(false)
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
  const [isRotating, startRotate] = React.useTransition()

  const openAdd = React.useCallback(() => {
    setEditing(null)
    setDrawerError(null)
    setDrawerOpen(true)
  }, [])

  const openEdit = React.useCallback((app: ApplicationSetting) => {
    setEditing(app)
    setDrawerError(null)
    setDrawerOpen(true)
  }, [])

  const openRotate = React.useCallback((app: ApplicationSetting) => {
    setRotateTarget(app)
    setRotateMode("auto")
    setRotateNewSecret("")
    setRotatePhase("idle")
    setRotatedSecret("")
    setRotateError(null)
    setCopied(false)
  }, [])

  function closeRotate() {
    if (isRotating) return
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

  function handleDeleteBulk() {
    setDeleteError(null)
    startDelete(async () => {
      try {
        await deleteApplications(bulkDeleteIds)
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

  function handleRotate() {
    if (!rotateTarget) return
    setRotateError(null)
    startRotate(async () => {
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
    })
  }

  async function handleCopy() {
    await navigator.clipboard.writeText(rotatedSecret)
    setCopied(true)
    setTimeout(() => setCopied(false), 2000)
  }

  const columns = React.useMemo<ColumnDef<ApplicationSetting>[]>(
    () => [
      {
        accessorKey: "name",
        header: "Naam",
        cell: ({ row }) => (
          <span className="font-medium">{row.original.name}</span>
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
                <DropdownMenuItem onClick={() => openEdit(row.original)}>
                  Bewerken
                </DropdownMenuItem>
                <DropdownMenuItem onClick={() => openRotate(row.original)}>
                  Secret roteren
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
    [openEdit, openRotate]
  )

  return (
    <>
      <SettingsTable
        data={applications}
        columns={columns}
        description="Applicatie-instellingen voor client credentials."
        emptyMessage="Nog geen applicaties geconfigureerd."
        emptyAddLabel="Applicatie toevoegen"
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
            <AlertDialogTitle>Applicaties verwijderen</AlertDialogTitle>
            <AlertDialogDescription>
              Weet je zeker dat je{" "}
              <strong>{bulkDeleteIds.length} applicaties</strong> wilt
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
                  disabled={isRotating}
                  className="gap-2"
                >
                  <div className="flex items-center gap-2">
                    <RadioGroupItem value="auto" id="rotate-auto" />
                    <Label htmlFor="rotate-auto" className="cursor-pointer font-normal">
                      Auto-genereren
                    </Label>
                  </div>
                  <div className="flex items-center gap-2">
                    <RadioGroupItem value="manual" id="rotate-manual" />
                    <Label htmlFor="rotate-manual" className="cursor-pointer font-normal">
                      Handmatig invoeren
                    </Label>
                  </div>
                </RadioGroup>
                {rotateMode === "manual" && (
                  <Input
                    placeholder="Nieuw secret"
                    value={rotateNewSecret}
                    onChange={(e) => setRotateNewSecret(e.target.value)}
                    disabled={isRotating}
                  />
                )}
                <FieldError>{rotateError}</FieldError>
              </div>
              <DialogFooter>
                <Button
                  variant="outline"
                  onClick={closeRotate}
                  disabled={isRotating}
                >
                  Annuleren
                </Button>
                <Button
                  onClick={handleRotate}
                  disabled={
                    isRotating ||
                    (rotateMode === "manual" && !rotateNewSecret.trim())
                  }
                >
                  {isRotating ? (
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
        <FieldError>{error}</FieldError>
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
          <SecretInput
            id="app-client-secret"
            value={clientSecret}
            onChange={(e) => setClientSecret(e.target.value)}
            placeholder={
              app?.hasSecret
                ? "Laat leeg om huidig secret te bewaren"
                : "Voer het client secret in"
            }
            disabled={saving}
          />
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
