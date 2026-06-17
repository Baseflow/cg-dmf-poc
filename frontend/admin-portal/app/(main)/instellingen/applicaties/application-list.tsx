// SPDX-License-Identifier: EUPL-1.2
// Copyright (C) 2026 Gemeente Utrecht

"use client"

import { CopyableCell } from "@/components/copyable-cell"
import { DiscardChangesDialog } from "@/components/discard-changes-dialog"
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
  DrawerDescription,
  DrawerHeader,
  DrawerTitle,
} from "@/components/ui/drawer"
import { DrawerFormFooter } from "@/components/ui/drawer-form-footer"
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
import { ResponsiveDrawer } from "@/components/ui/responsive-drawer"
import { SecretInput } from "@/components/ui/secret-input"
import { useDeleteState } from "@/hooks/use-delete-state"
import { useDrawerState } from "@/hooks/use-drawer-state"
import { parseActionError } from "@/lib/errors"
import { formatNlDate } from "@/lib/format"
import { type ColumnDef } from "@tanstack/react-table"
import {
  AlertTriangle,
  AppWindow,
  MoreHorizontal,
  RefreshCw,
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
  const {
    open: drawerOpen,
    item: editing,
    readOnly: drawerReadOnly,
    saving: isSaving,
    error: drawerError,
    setDirty: setDrawerDirty,
    closeConfirmOpen,
    dismissCloseConfirm,
    confirmClose,
    handleCloseAttempt: handleDrawerCloseAttempt,
    openAdd,
    openEdit: openEditBase,
    save,
  } = useDrawerState<ApplicationSetting>()

  const openEdit = useCallback(
    (app: ApplicationSetting) => openEditBase(app, app.readonly ?? false),
    [openEditBase]
  )

  const {
    deleteTarget,
    setDeleteTarget,
    bulkDeleteIds,
    setBulkDeleteIds,
    bulkDeleteOpen,
    setBulkDeleteOpen,
    isDeleting,
    deleteError,
    setDeleteError,
    deleteOne,
    deleteBulk,
  } = useDeleteState<ApplicationSetting>()

  const [rotateTarget, setRotateTarget] = useState<ApplicationSetting | null>(
    null
  )
  const [rotateMode, setRotateMode] = useState<"auto" | "manual">("auto")
  const [rotateNewSecret, setRotateNewSecret] = useState("")
  const [rotatePhase, setRotatePhase] = useState<RotatePhase>("idle")
  const [rotatedSecret, setRotatedSecret] = useState("")
  const [rotateError, setRotateError] = useState<string | null>(null)
  const [isRotating, startRotate] = useTransition()

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
    const body = {
      name: data.name,
      clientId: data.clientId,
      ...(data.clientSecret ? { clientSecret: data.clientSecret } : {}),
    }
    save(async () => {
      if (editing) {
        await updateApplication(editing.id, body)
      } else {
        await createApplication(body)
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
            {formatNlDate(row.original.updatedAt)}
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
                <DropdownMenuItem
                  onClick={() => openRotate(row.original)}
                  disabled={row.original.readonly}
                >
                  Secret roteren
                </DropdownMenuItem>
                <DropdownMenuItem
                  variant="destructive"
                  onClick={() => setDeleteTarget(row.original)}
                  disabled={row.original.readonly}
                >
                  Verwijderen
                </DropdownMenuItem>
              </DropdownMenuContent>
            </DropdownMenu>
          </div>
        ),
      },
    ],
    [openEdit, openRotate, setDeleteTarget]
  )

  return (
    <>
      <SettingsTable
        data={applications}
        columns={columns}
        description="Beheer de applicaties die toegang nodig hebben tot dit systeem, zoals OpenZaak en GZAC om de DRC te gebruiken."
        emptyMessage="Nog geen applicaties geconfigureerd."
        emptyAddLabel="Applicatie toevoegen"
        emptyIcon={<AppWindow />}
        onAdd={openAdd}
        onBulkDelete={(ids) => {
          setBulkDeleteIds(ids)
          setBulkDeleteOpen(true)
        }}
      />

      <ResponsiveDrawer
        open={drawerOpen}
        onOpenChange={(open) => {
          if (!open) handleDrawerCloseAttempt()
        }}
      >
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
      </ResponsiveDrawer>

      <DiscardChangesDialog
        open={closeConfirmOpen}
        onDismiss={dismissCloseConfirm}
        onConfirm={confirmClose}
      />

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
              onClick={() => {
                if (deleteTarget) {
                  deleteOne(() => deleteApplication(deleteTarget.id))
                }
              }}
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
              onClick={() =>
                deleteBulk(() => deleteApplications(bulkDeleteIds))
              }
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

  const serverError = parseActionError(error)
  const generalError = serverError.field ? null : serverError.message || null
  const allFieldErrors: AppFormErrors = {
    ...fieldErrors,
    ...(serverError.field
      ? { [serverError.field as keyof AppFormErrors]: serverError.message }
      : {}),
  }

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
        <FieldError>{generalError}</FieldError>
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
          <FieldError>{allFieldErrors.name}</FieldError>
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
          <FieldError>{allFieldErrors.clientId}</FieldError>
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
      <DrawerFormFooter
        readOnly={readOnly}
        saving={saving}
        formId="app-form"
        onCancel={onCancel}
      />
    </>
  )
}
