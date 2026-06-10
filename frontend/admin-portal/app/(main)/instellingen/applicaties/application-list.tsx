"use client"

import { CopyableCell } from "@/components/copyable-cell"
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
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog"
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
import { Label } from "@/components/ui/label"
import { RadioGroup, RadioGroupItem } from "@/components/ui/radio-group"
import { SecretInput } from "@/components/ui/secret-input"
import { useIsMobile } from "@/hooks/use-mobile"
import { type ColumnDef } from "@tanstack/react-table"
import {
  AlertTriangle,
  AppWindow,
  Check,
  MoreHorizontal,
  RefreshCw,
  X,
} from "lucide-react"
import {
  useCallback,
  useEffect,
  useMemo,
  useState,
  useTransition,
  type FormEvent,
} from "react"
import { z } from "zod"
import {
  createApplication,
  deleteApplication,
  deleteApplications,
  rotateApplicationSecret,
  updateApplication,
  type ApplicationSetting,
} from "./actions"

type RotatePhase = "idle" | "success" | "error"

export function ApplicationList({
  applications,
}: {
  applications: ApplicationSetting[]
}) {
  const isMobile = useIsMobile()

  const [drawerOpen, setDrawerOpen] = useState(false)
  const [editing, setEditing] = useState<ApplicationSetting | null>(null)
  const [drawerReadOnly, setDrawerReadOnly] = useState(false)
  const [isSaving, startSave] = useTransition()
  const [drawerError, setDrawerError] = useState<string | null>(null)

  const [deleteTarget, setDeleteTarget] = useState<ApplicationSetting | null>(
    null
  )
  const [bulkDeleteIds, setBulkDeleteIds] = useState<string[]>([])
  const [bulkDeleteOpen, setBulkDeleteOpen] = useState(false)
  const [isDeleting, startDelete] = useTransition()
  const [deleteError, setDeleteError] = useState<string | null>(null)

  const [drawerDirty, setDrawerDirty] = useState(false)
  const [closeConfirmOpen, setCloseConfirmOpen] = useState(false)

  const [rotateTarget, setRotateTarget] = useState<ApplicationSetting | null>(
    null
  )
  const [rotateMode, setRotateMode] = useState<"auto" | "manual">("auto")
  const [rotateNewSecret, setRotateNewSecret] = useState("")
  const [rotatePhase, setRotatePhase] = useState<RotatePhase>("idle")
  const [rotatedSecret, setRotatedSecret] = useState("")
  const [rotateError, setRotateError] = useState<string | null>(null)
  const [isRotating, startRotate] = useTransition()

  const handleDrawerCloseAttempt = useCallback(() => {
    if (isSaving) return
    if (drawerDirty) {
      setCloseConfirmOpen(true)
    } else {
      setDrawerOpen(false)
    }
  }, [isSaving, drawerDirty])

  const openAdd = useCallback(() => {
    setEditing(null)
    setDrawerReadOnly(false)
    setDrawerError(null)
    setDrawerDirty(false)
    setDrawerOpen(true)
  }, [])

  const openEdit = useCallback((app: ApplicationSetting) => {
    setEditing(app)
    setDrawerReadOnly(app.readonly ?? false)
    setDrawerError(null)
    setDrawerDirty(false)
    setDrawerOpen(true)
  }, [])

  const openRotate = useCallback((app: ApplicationSetting) => {
    setRotateTarget(app)
    setRotateMode("auto")
    setRotateNewSecret("")
    setRotatePhase("idle")
    setRotatedSecret("")
    setRotateError(null)
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

  const columns = useMemo<ColumnDef<ApplicationSetting>[]>(
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
        cell: ({ row }) => <CopyableCell value={row.original.clientId} />,
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
                <DropdownMenuItem onClick={() => openEdit(row.original)}>
                  {row.original.readonly ? "Bekijken" : "Bewerken"}
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
        emptyIcon={<AppWindow />}
        onAdd={openAdd}
        onBulkDelete={(ids) => {
          setBulkDeleteIds(ids)
          setBulkDeleteOpen(true)
        }}
      />

      <Drawer
        open={drawerOpen}
        onOpenChange={(open) => {
          if (!open) handleDrawerCloseAttempt()
        }}
        direction={isMobile ? "bottom" : "right"}
      >
        <DrawerContent>
          <AppForm
            key={editing?.id ?? "new"}
            app={editing}
            readOnly={drawerReadOnly}
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
                <DialogTitle>Nieuw secret gegenereerd</DialogTitle>
              </DialogHeader>
              <div className="flex items-start gap-2 rounded-md border border-amber-200 bg-amber-50 p-3 text-amber-800 dark:border-amber-800/30 dark:bg-amber-950/20 dark:text-amber-400">
                <AlertTriangle className="mt-0.5 size-4 shrink-0" />
                <p className="text-sm">
                  Dit secret wordt maar één keer getoond. Kopieer het nu voordat
                  je dit venster sluit.
                </p>
              </div>
              <Input
                readOnly
                value={rotatedSecret}
                className="font-mono text-xs"
                copyable
              />
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
                    <Label
                      htmlFor="rotate-auto"
                      className="cursor-pointer font-normal"
                    >
                      Auto-genereren
                    </Label>
                  </div>
                  <div className="flex items-center gap-2">
                    <RadioGroupItem value="manual" id="rotate-manual" />
                    <Label
                      htmlFor="rotate-manual"
                      className="cursor-pointer font-normal"
                    >
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
  readOnly = false,
  saving,
  error,
  onSave,
  onCancel,
  onDirtyChange,
}: {
  app: ApplicationSetting | null
  readOnly?: boolean
  saving: boolean
  error: string | null
  onSave: (data: {
    name: string
    clientId: string
    clientSecret: string
  }) => void
  onCancel: () => void
  onDirtyChange?: (dirty: boolean) => void
}) {
  const [name, setName] = useState(app?.name ?? "")
  const [clientId, setClientId] = useState(app?.clientId ?? "")
  const [clientSecret, setClientSecret] = useState(app?.clientSecret ?? "")
  const [fieldErrors, setFieldErrors] = useState<AppFormErrors>({})

  function generateSecret() {
    const alphabet = "0123456789abcdefghijklmnopqrstuvwxyz"
    // 256 % 36 (nr of chars) = 4, so bytes 252–255 are rejected (~1.5% of draws).
    const limit = 256 - (256 % alphabet.length)
    const result: string[] = []
    while (result.length < 32) {
      const bytes = new Uint8Array(64)
      crypto.getRandomValues(bytes)
      for (const b of bytes) {
        if (result.length === 32) break
        if (b < limit) result.push(alphabet[b % alphabet.length])
      }
    }
    setClientSecret(result.join(""))
  }

  const isDirty =
    name !== (app?.name ?? "") ||
    clientId !== (app?.clientId ?? "") ||
    clientSecret !== ""

  useEffect(() => {
    onDirtyChange?.(isDirty)
  }, [isDirty, onDirtyChange])

  function handleSubmit(e: FormEvent<HTMLFormElement>) {
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
          {readOnly
            ? "Bekijk de applicatie-instellingen."
            : app
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
            disabled={saving || readOnly}
          />
          <FieldDescription>
            Herkenbare naam voor deze applicatie.
          </FieldDescription>
          <FieldError>{fieldErrors.name}</FieldError>
        </Field>
        <Field>
          <FieldLabel htmlFor="app-client-id">Client ID</FieldLabel>
          <Input
            id="app-client-id"
            value={clientId}
            onChange={(e) => setClientId(e.target.value)}
            placeholder="my-client-id"
            disabled={saving || readOnly}
            copyable
          />
          <FieldDescription>
            De unieke identifier van de applicatie.
          </FieldDescription>
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
            disabled={saving || readOnly}
            onGenerate={generateSecret}
            copyable
          />
        </Field>
      </form>
      <DrawerFooter>
        {readOnly ? (
          <Button type="button" variant="outline" size="sm" onClick={onCancel}>
            Sluiten
          </Button>
        ) : (
          <>
            <Button type="submit" form="app-form" size="sm" disabled={saving}>
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
          </>
        )}
      </DrawerFooter>
    </>
  )
}
