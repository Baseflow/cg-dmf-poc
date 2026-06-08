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
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Checkbox } from "@/components/ui/checkbox"
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
import { Field, FieldContent, FieldDescription, FieldError, FieldLabel } from "@/components/ui/field"
import { Input } from "@/components/ui/input"
import { SecretInput } from "@/components/ui/secret-input"
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select"
import { SecretCell } from "@/components/secret-cell"
import { SettingsTable } from "@/components/settings-table"
import { useIsMobile } from "@/hooks/use-mobile"
import { type ColumnDef } from "@tanstack/react-table"
import { Check, Database, MoreHorizontal, X } from "lucide-react"
import { useCallback, useEffect, useMemo, useState, useTransition, type FormEvent } from "react"
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
  const isMobile = useIsMobile()

  const [drawerOpen, setDrawerOpen] = useState(false)
  const [editingRepo, setEditingRepo] = useState<Repository | null>(null)
  const [isSaving, startSave] = useTransition()
  const [drawerError, setDrawerError] = useState<string | null>(null)
  const [drawerDirty, setDrawerDirty] = useState(false)
  const [closeConfirmOpen, setCloseConfirmOpen] = useState(false)

  const [deleteTarget, setDeleteTarget] = useState<Repository | null>(
    null
  )
  const [bulkDeleteIds, setBulkDeleteIds] = useState<string[]>([])
  const [bulkDeleteOpen, setBulkDeleteOpen] = useState(false)
  const [isDeleting, startDelete] = useTransition()
  const [deleteError, setDeleteError] = useState<string | null>(null)

  const handleDrawerCloseAttempt = useCallback(() => {
    if (isSaving) return
    if (drawerDirty) {
      setCloseConfirmOpen(true)
    } else {
      setDrawerOpen(false)
    }
  }, [isSaving, drawerDirty])

  const openAdd = useCallback(() => {
    setEditingRepo(null)
    setDrawerError(null)
    setDrawerDirty(false)
    setDrawerOpen(true)
  }, [])

  const openEdit = useCallback((repo: Repository) => {
    setEditingRepo(repo)
    setDrawerError(null)
    setDrawerDirty(false)
    setDrawerOpen(true)
  }, [])

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
    setDrawerError(null)
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

    startSave(async () => {
      try {
        if (editingRepo) {
          await updateRepository(editingRepo.id, body)
        } else {
          if (!data.accessKey) {
            setDrawerError("Access Key is verplicht.")
            return
          }
          await createRepository(body)
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
        await deleteRepository(deleteTarget.id)
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
        await deleteRepositories(bulkDeleteIds)
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
              <Badge variant="secondary" className="text-xs text-muted-foreground">
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
          return (
            <span className="text-muted-foreground">{label || "—"}</span>
          )
        },
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

      <Drawer
        key={isMobile ? "bottom" : "right"}
        open={drawerOpen}
        onOpenChange={(open) => {
          if (!open) handleDrawerCloseAttempt()
        }}
        direction={isMobile ? "bottom" : "right"}
      >
        <DrawerContent>
          <RepositoryForm
            key={editingRepo?.id ?? "new"}
            repo={editingRepo}
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

function RepositoryForm({
  repo,
  saving,
  error,
  onSave,
  onCancel,
  onDirtyChange,
}: {
  repo: Repository | null
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
          {repo
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
            disabled={saving}
          />
          <FieldDescription>Herkenbare naam voor deze repository.</FieldDescription>
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
          <FieldDescription>Bepaalt welke verbindingsinstellingen nodig zijn.</FieldDescription>
        </Field>

        <Field>
          <FieldLabel htmlFor="repo-url">Locatie</FieldLabel>
          <Input
            id="repo-url"
            value={url}
            onChange={(e) => setUrl(e.target.value)}
            placeholder="https://mijnaccount.blob.core.windows.net"
            disabled={saving}
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
              disabled={saving}
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
              disabled={saving}
            />
            <FieldDescription>De naam van het Azure Storage-account.</FieldDescription>
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
              disabled={saving}
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
              disabled={saving}
              copyable
            />
          </Field>
        )}

        <Field orientation="horizontal">
          <Checkbox
            id="repo-default"
            checked={isDefault}
            onCheckedChange={(v) => setIsDefault(v === true)}
            disabled={saving}
          />
          <FieldContent>
            <FieldLabel htmlFor="repo-default">Standaard repository</FieldLabel>
            <FieldDescription>Gebruik deze repository standaard voor nieuwe uploads.</FieldDescription>
          </FieldContent>
        </Field>

        <Field orientation="horizontal">
          <Checkbox
            id="repo-enabled"
            checked={enabled}
            onCheckedChange={(v) => setEnabled(v === true)}
            disabled={saving}
          />
          <FieldContent>
            <FieldLabel htmlFor="repo-enabled">Ingeschakeld</FieldLabel>
            <FieldDescription>Schakel deze repository in of uit voor gebruik.</FieldDescription>
          </FieldContent>
        </Field>
      </form>
      <DrawerFooter>
        <Button type="submit" form="repo-form" size="sm" disabled={saving}>
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
      </DrawerFooter>
    </>
  )
}
