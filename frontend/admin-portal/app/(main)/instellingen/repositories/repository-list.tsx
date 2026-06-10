// SPDX-License-Identifier: EUPL-1.2
// Copyright (C) 2026 Gemeente Utrecht

"use client"

import { DiscardChangesDialog } from "@/components/discard-changes-dialog"
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
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Checkbox } from "@/components/ui/checkbox"
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
  FieldContent,
  FieldDescription,
  FieldError,
  FieldLabel,
} from "@/components/ui/field"
import { Input } from "@/components/ui/input"
import { ResponsiveDrawer } from "@/components/ui/responsive-drawer"
import { SecretInput } from "@/components/ui/secret-input"
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select"
import { useDeleteState } from "@/hooks/use-delete-state"
import { useDrawerState } from "@/hooks/use-drawer-state"
import { ValidationError } from "@/lib/errors"
import { formatNlDate } from "@/lib/format"
import { type ColumnDef } from "@tanstack/react-table"
import { Database, MoreHorizontal } from "lucide-react"
import {
  useCallback,
  useEffect,
  useMemo,
  useState,
  type FormEvent,
} from "react"
import {
  createRepository,
  deleteRepositories,
  deleteRepository,
  type Repository,
  type StorageType,
  updateRepository,
} from "./actions"

export function RepositoryList({
  repositories,
}: {
  repositories: Repository[]
}) {
  const {
    open: drawerOpen,
    item: editingRepo,
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
  } = useDrawerState<Repository>()

  const openEdit = useCallback(
    (repo: Repository) => openEditBase(repo, repo.readonly ?? false),
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
  } = useDeleteState<Repository>()

  function handleSave(data: {
    name: string
    storageType: StorageType
    url: string
    accessKey: string
    secretKey: string
    storageAccountName: string
    bucket: string
    isDefault: boolean
    enabled: boolean
  }) {
    const body: Parameters<typeof createRepository>[0] = {
      name: data.name,
      storageType: data.storageType,
      url: data.url,
      bucket: data.bucket,
      isDefault: data.isDefault,
      enabled: data.enabled,
    }
    if (data.storageType === "S3") {
      if (data.accessKey) body.accessKey = data.accessKey
      if (data.secretKey) body.secretKey = data.secretKey
    } else {
      body.storageAccountName = data.storageAccountName
      if (data.accessKey) body.accessKey = data.accessKey
    }

    save(async () => {
      if (!editingRepo && !data.accessKey) {
        throw new ValidationError("Access Key is verplicht.")
      }
      if (editingRepo) {
        await updateRepository(editingRepo.id, body)
      } else {
        await createRepository(body)
      }
    })
  }

  const columns = useMemo<ColumnDef<Repository>[]>(
    () => [
      {
        accessorKey: "name",
        header: "Naam",
        cell: ({ row }) => (
          <div className="flex items-center gap-2">
            <span className="font-medium">{row.original.name}</span>
            {row.original.isDefault && (
              <Badge variant="outline" className="text-xs">
                Standaard
              </Badge>
            )}
            {!row.original.enabled && (
              <Badge
                variant="secondary"
                className="text-xs text-muted-foreground"
              >
                Uitgeschakeld
              </Badge>
            )}
          </div>
        ),
      },
      {
        accessorKey: "storageType",
        header: "Type",
        cell: ({ row }) => (
          <span className="text-muted-foreground">
            {row.original.storageType}
          </span>
        ),
      },
      {
        id: "locatie",
        header: "Locatie",
        cell: ({ row }) => (
          <span className="max-w-xs truncate text-muted-foreground">
            {row.original.url || "—"}
          </span>
        ),
      },
      {
        accessorKey: "bucket",
        header: "Bucket",
        cell: ({ row }) => {
          const { storageType, bucket, storageAccountName } = row.original
          const label = storageType === "S3" ? bucket : storageAccountName
          return <span className="text-muted-foreground">{label || "—"}</span>
        },
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
    [openEdit, setDeleteTarget]
  )

  return (
    <>
      <SettingsTable
        data={repositories}
        columns={columns}
        description="Object store repositories."
        emptyMessage="Nog geen repositories geconfigureerd."
        emptyAddLabel="Repository toevoegen"
        emptyIcon={<Database />}
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
        <RepositoryForm
          key={editingRepo?.id ?? "new"}
          repo={editingRepo}
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
            <AlertDialogTitle>Repositories verwijderen</AlertDialogTitle>
            <AlertDialogDescription>
              Weet je zeker dat je{" "}
              <strong>{bulkDeleteIds.length} repositories</strong> wilt
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
                deleteBulk(() => deleteRepositories(bulkDeleteIds))
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
            <AlertDialogTitle>Repository verwijderen</AlertDialogTitle>
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
                  deleteOne(() => deleteRepository(deleteTarget.id))
                }
              }}
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

function RepositoryForm({
  repo,
  readOnly = false,
  saving,
  error,
  onSave,
  onCancel,
  onDirtyChange,
}: {
  repo: Repository | null
  readOnly?: boolean
  saving: boolean
  error: string | null
  onSave: (data: {
    name: string
    storageType: StorageType
    url: string
    accessKey: string
    secretKey: string
    storageAccountName: string
    bucket: string
    isDefault: boolean
    enabled: boolean
  }) => void
  onCancel: () => void
  onDirtyChange?: (dirty: boolean) => void
}) {
  const [name, setName] = useState(repo?.name ?? "")
  const [storageType, setStorageType] = useState<StorageType>(
    (repo?.storageType as StorageType) ?? "S3"
  )
  const [url, setUrl] = useState(repo?.url ?? "")
  const [accessKey, setAccessKey] = useState(repo?.accessKey ?? "")
  const [secretKey, setSecretKey] = useState(repo?.secretKey ?? "")
  const [storageAccountName, setStorageAccountName] = useState(
    repo?.storageAccountName ?? ""
  )
  const [bucket, setBucket] = useState(repo?.bucket ?? "")
  const [isDefault, setIsDefault] = useState(repo?.isDefault ?? false)
  const [enabled, setEnabled] = useState(repo?.enabled ?? true)

  const isDirty =
    name !== (repo?.name ?? "") ||
    storageType !== ((repo?.storageType as StorageType) ?? "S3") ||
    url !== (repo?.url ?? "") ||
    accessKey !== (repo?.accessKey ?? "") ||
    secretKey !== "" ||
    storageAccountName !== (repo?.storageAccountName ?? "") ||
    bucket !== (repo?.bucket ?? "") ||
    isDefault !== (repo?.isDefault ?? false) ||
    enabled !== (repo?.enabled ?? true)

  useEffect(() => {
    onDirtyChange?.(isDirty)
  }, [isDirty, onDirtyChange])

  const isS3 = storageType === "S3"
  const isAzure = storageType === "Azure Blob Storage"
  const hasExistingAccessKey = repo !== null && repo.accessKey !== null
  const hasExistingSecretKey = repo !== null && repo.secretKey !== null

  function handleSubmit(e: FormEvent<HTMLFormElement>) {
    e.preventDefault()
    onSave({
      name,
      storageType,
      url,
      accessKey,
      secretKey,
      storageAccountName,
      bucket,
      isDefault,
      enabled,
    })
  }

  return (
    <>
      <DrawerHeader>
        <DrawerTitle>{repo ? repo.name : "Repository toevoegen"}</DrawerTitle>
        <DrawerDescription>
          {readOnly
            ? "Bekijk de repository-instellingen."
            : repo
              ? "Bewerk de repository-instellingen."
              : "Configureer een nieuwe object store repository."}
        </DrawerDescription>
      </DrawerHeader>
      <form
        id="repo-form"
        onSubmit={handleSubmit}
        className="flex flex-col gap-4 overflow-y-auto px-4"
      >
        <FieldError>{error}</FieldError>

        <Field>
          <FieldLabel htmlFor="repo-name">Naam</FieldLabel>
          <Input
            id="repo-name"
            value={name}
            onChange={(e) => setName(e.target.value)}
            placeholder="mijn-repository"
            required
            disabled={saving || readOnly}
          />
          <FieldDescription>
            Herkenbare naam voor deze repository.
          </FieldDescription>
        </Field>

        <Field>
          <FieldLabel htmlFor="repo-type">Type</FieldLabel>
          <Select
            value={storageType}
            onValueChange={(v) => setStorageType(v as StorageType)}
            disabled={saving || repo !== null}
          >
            <SelectTrigger id="repo-type">
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="S3">S3</SelectItem>
              <SelectItem value="Azure Blob Storage">
                Azure Blob Storage
              </SelectItem>
            </SelectContent>
          </Select>
          <FieldDescription>
            Bepaalt welke verbindingsinstellingen nodig zijn.
          </FieldDescription>
        </Field>

        <Field>
          <FieldLabel htmlFor="repo-url">Locatie</FieldLabel>
          <Input
            id="repo-url"
            value={url}
            onChange={(e) => setUrl(e.target.value)}
            placeholder="https://mijnaccount.blob.core.windows.net"
            disabled={saving || readOnly}
            copyable
          />
          <FieldDescription>Basis-URL van de object store.</FieldDescription>
        </Field>

        {isS3 && (
          <Field>
            <FieldLabel htmlFor="repo-bucket">Bucket (optioneel)</FieldLabel>
            <Input
              id="repo-bucket"
              value={bucket}
              onChange={(e) => setBucket(e.target.value)}
              placeholder="mijn-bucket"
              disabled={saving || readOnly}
            />
            <FieldDescription>De naam van de S3-bucket.</FieldDescription>
          </Field>
        )}

        {isAzure && (
          <Field>
            <FieldLabel htmlFor="repo-storage-account-name">
              Storage account name
            </FieldLabel>
            <Input
              id="repo-storage-account-name"
              value={storageAccountName}
              onChange={(e) => setStorageAccountName(e.target.value)}
              placeholder="mijnstorageaccount"
              required={isAzure}
              disabled={saving || readOnly}
            />
            <FieldDescription>
              De naam van het Azure Storage-account.
            </FieldDescription>
          </Field>
        )}

        {(isS3 || isAzure) && (
          <Field>
            <FieldLabel htmlFor="repo-access-key">Access Key</FieldLabel>
            <SecretInput
              id="repo-access-key"
              value={accessKey}
              onChange={(e) => setAccessKey(e.target.value)}
              placeholder={
                hasExistingAccessKey
                  ? "Laat leeg om huidige waarde te bewaren"
                  : "Voer de access key in"
              }
              disabled={saving || readOnly}
              copyable
            />
          </Field>
        )}

        {isS3 && (
          <Field>
            <FieldLabel htmlFor="repo-secret-key">Secret Key</FieldLabel>
            <SecretInput
              id="repo-secret-key"
              value={secretKey}
              onChange={(e) => setSecretKey(e.target.value)}
              placeholder={
                hasExistingSecretKey
                  ? "Laat leeg om huidige waarde te bewaren"
                  : "Voer de secret key in"
              }
              disabled={saving || readOnly}
              copyable
            />
          </Field>
        )}

        <Field orientation="horizontal">
          <Checkbox
            id="repo-default"
            checked={isDefault}
            onCheckedChange={(v) => setIsDefault(v === true)}
            disabled={saving || readOnly}
          />
          <FieldContent>
            <FieldLabel htmlFor="repo-default">Standaard repository</FieldLabel>
            <FieldDescription>
              Gebruik deze repository standaard voor nieuwe uploads.
            </FieldDescription>
          </FieldContent>
        </Field>

        <Field orientation="horizontal">
          <Checkbox
            id="repo-enabled"
            checked={enabled}
            onCheckedChange={(v) => setEnabled(v === true)}
            disabled={saving || readOnly}
          />
          <FieldContent>
            <FieldLabel htmlFor="repo-enabled">Ingeschakeld</FieldLabel>
            <FieldDescription>
              Schakel deze repository in of uit voor gebruik.
            </FieldDescription>
          </FieldContent>
        </Field>
      </form>
      <DrawerFormFooter
        readOnly={readOnly}
        saving={saving}
        formId="repo-form"
        onCancel={onCancel}
      />
    </>
  )
}
