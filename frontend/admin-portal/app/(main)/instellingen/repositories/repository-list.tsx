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
import { SecretInput } from "@/components/ui/secret-input"
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select"
import { SettingsTable } from "@/components/settings-table"
import { useIsMobile } from "@/hooks/use-mobile"
import { type ColumnDef } from "@tanstack/react-table"
import { Check, MoreHorizontal, X } from "lucide-react"
import * as React from "react"
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

  const [drawerOpen, setDrawerOpen] = React.useState(false)
  const [editingRepo, setEditingRepo] = React.useState<Repository | null>(null)
  const [isSaving, startSave] = React.useTransition()
  const [drawerError, setDrawerError] = React.useState<string | null>(null)

  const [deleteTarget, setDeleteTarget] = React.useState<Repository | null>(
    null
  )
  const [bulkDeleteIds, setBulkDeleteIds] = React.useState<string[]>([])
  const [bulkDeleteOpen, setBulkDeleteOpen] = React.useState(false)
  const [isDeleting, startDelete] = React.useTransition()
  const [deleteError, setDeleteError] = React.useState<string | null>(null)

  const openAdd = React.useCallback(() => {
    setEditingRepo(null)
    setDrawerError(null)
    setDrawerOpen(true)
  }, [])

  const openEdit = React.useCallback((repo: Repository) => {
    setEditingRepo(repo)
    setDrawerError(null)
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

  const columns = React.useMemo<ColumnDef<Repository>[]>(
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
        accessorKey: "url",
        header: "URL",
        cell: ({ row }) => (
          <span className="max-w-xs truncate text-muted-foreground">
            {row.original.url}
          </span>
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
          <RepositoryForm
            key={editingRepo?.id ?? "new"}
            repo={editingRepo}
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
}) {
  const [name, setName] = React.useState(repo?.name ?? "")
  const [storageType, setStorageType] = React.useState<StorageType>(
    (repo?.storageType as StorageType) ?? "S3"
  )
  const [url, setUrl] = React.useState(repo?.url ?? "")
  const [accessKey, setAccessKey] = React.useState(repo?.accessKey ?? "")
  const [secretKey, setSecretKey] = React.useState(repo?.secretKey ?? "")
  const [storageAccountName, setStorageAccountName] = React.useState(
    repo?.storageAccountName ?? ""
  )
  const [bucket, setBucket] = React.useState(repo?.bucket ?? "")
  const [isDefault, setIsDefault] = React.useState(repo?.isDefault ?? false)
  const [enabled, setEnabled] = React.useState(repo?.enabled ?? true)

  const isS3 = storageType === "S3"
  const isAzure = storageType === "Azure Blob Storage"
  const hasExistingAccessKey = repo !== null && repo.accessKey === null
  const hasExistingSecretKey = repo !== null && repo.secretKey === null

  function handleSubmit(e: React.FormEvent<HTMLFormElement>) {
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
        </Field>

        <Field>
          <FieldLabel htmlFor="repo-type">Type</FieldLabel>
          <Select
            value={storageType}
            onValueChange={(v) => setStorageType(v as StorageType)}
            disabled={saving}
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
        </Field>

        {isS3 && (
          <>
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
              />
            </Field>

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
              />
            </Field>

            <Field>
              <FieldLabel htmlFor="repo-bucket">Bucket (optioneel)</FieldLabel>
              <Input
                id="repo-bucket"
                value={bucket}
                onChange={(e) => setBucket(e.target.value)}
                placeholder="mijn-bucket"
                disabled={saving}
              />
            </Field>
          </>
        )}

        {isAzure && (
          <>
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
            </Field>

            <Field>
              <FieldLabel htmlFor="repo-access-key-azure">
                Access Key
              </FieldLabel>
              <SecretInput
                id="repo-access-key-azure"
                value={accessKey}
                onChange={(e) => setAccessKey(e.target.value)}
                placeholder={
                  hasExistingAccessKey
                    ? "Laat leeg om huidige waarde te bewaren"
                    : "Voer de access key in"
                }
                disabled={saving}
              />
            </Field>

            <Field>
              <FieldLabel htmlFor="repo-url">Locatie URL</FieldLabel>
              <Input
                id="repo-url"
                value={url}
                onChange={(e) => setUrl(e.target.value)}
                placeholder="https://mijnaccount.blob.core.windows.net"
                required={isAzure}
                disabled={saving}
              />
            </Field>
          </>
        )}

        <div className="flex flex-col gap-3 pt-1">
          <div className="flex cursor-pointer items-center gap-2.5">
            <Checkbox
              id="repo-default"
              checked={isDefault}
              onCheckedChange={(v) => setIsDefault(v === true)}
              disabled={saving}
            />
            <Label htmlFor="repo-default" className="cursor-pointer font-normal">
              Standaard repository
            </Label>
          </div>

          <div className="flex cursor-pointer items-center gap-2.5">
            <Checkbox
              id="repo-enabled"
              checked={enabled}
              onCheckedChange={(v) => setEnabled(v === true)}
              disabled={saving}
            />
            <Label htmlFor="repo-enabled" className="cursor-pointer font-normal">
              Ingeschakeld
            </Label>
          </div>
        </div>
      </form>
      <DrawerFooter>
        <Button type="submit" form="repo-form" size="sm" disabled={saving}>
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
