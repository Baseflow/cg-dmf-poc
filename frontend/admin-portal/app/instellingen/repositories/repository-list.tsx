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
import { Field, FieldLabel } from "@/components/ui/field"
import { Input } from "@/components/ui/input"
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select"
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
  createRepository,
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
  const [isDeleting, startDelete] = React.useTransition()
  const [deleteError, setDeleteError] = React.useState<string | null>(null)

  function openAdd() {
    setEditingRepo(null)
    setDrawerError(null)
    setDrawerOpen(true)
  }

  function openEdit(repo: Repository) {
    setEditingRepo(repo)
    setDrawerError(null)
    setDrawerOpen(true)
  }

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

  return (
    <>
      <div className="flex w-full max-w-sm flex-col gap-4">
        <div className="flex items-center justify-between">
          <p className="text-sm text-muted-foreground">
            Object store repositories.
          </p>
          <Button variant="outline" size="sm" onClick={openAdd}>
            <Plus />
            Toevoegen
          </Button>
        </div>

        {repositories.length === 0 ? (
          <div className="flex flex-col items-center gap-3 py-12 text-center">
            <p className="text-sm text-muted-foreground">
              Nog geen repositories geconfigureerd.
            </p>
            <Button variant="outline" size="sm" onClick={openAdd}>
              <Plus />
              Repository toevoegen
            </Button>
          </div>
        ) : (
          <div className="flex flex-col divide-y rounded-lg border">
            {repositories.map((repo) => (
              <div
                key={repo.id}
                className="flex items-center gap-3 px-4 py-3 hover:bg-muted/50"
              >
                <button
                  className="flex min-w-0 flex-1 cursor-pointer items-center gap-3 text-left"
                  onClick={() => openEdit(repo)}
                >
                  <div className="min-w-0 flex-1">
                    <div className="flex items-center gap-2">
                      <p className="text-sm font-medium">{repo.name}</p>
                      {repo.isDefault && (
                        <Badge variant="outline" className="text-xs">
                          Standaard
                        </Badge>
                      )}
                      {!repo.enabled && (
                        <Badge
                          variant="secondary"
                          className="text-xs text-muted-foreground"
                        >
                          Uitgeschakeld
                        </Badge>
                      )}
                    </div>
                    <p className="truncate text-xs text-muted-foreground">
                      {repo.storageType}
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
                      onClick={() => setDeleteTarget(repo)}
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
  const [showAccessKey, setShowAccessKey] = React.useState(false)
  const [showSecretKey, setShowSecretKey] = React.useState(false)

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
        {error && <p className="text-sm text-destructive">{error}</p>}

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
                onChange={setAccessKey}
                show={showAccessKey}
                onToggleShow={() => setShowAccessKey((v) => !v)}
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
                onChange={setSecretKey}
                show={showSecretKey}
                onToggleShow={() => setShowSecretKey((v) => !v)}
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
                onChange={setAccessKey}
                show={showAccessKey}
                onToggleShow={() => setShowAccessKey((v) => !v)}
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
          <label className="flex cursor-pointer items-center gap-2.5">
            <Checkbox
              id="repo-default"
              checked={isDefault}
              onCheckedChange={(v) => setIsDefault(v === true)}
              disabled={saving}
            />
            <span className="text-sm">Standaard repository</span>
          </label>

          <label className="flex cursor-pointer items-center gap-2.5">
            <Checkbox
              id="repo-enabled"
              checked={enabled}
              onCheckedChange={(v) => setEnabled(v === true)}
              disabled={saving}
            />
            <span className="text-sm">Ingeschakeld</span>
          </label>
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

function SecretInput({
  id,
  value,
  onChange,
  show,
  onToggleShow,
  placeholder,
  disabled,
}: {
  id: string
  value: string
  onChange: (v: string) => void
  show: boolean
  onToggleShow: () => void
  placeholder: string
  disabled?: boolean
}) {
  return (
    <div className="relative">
      <Input
        id={id}
        type={show ? "text" : "password"}
        value={value}
        onChange={(e) => onChange(e.target.value)}
        placeholder={placeholder}
        className="pr-9"
        disabled={disabled}
      />
      <button
        type="button"
        onClick={onToggleShow}
        className="absolute top-1/2 right-2.5 -translate-y-1/2 text-muted-foreground transition-colors hover:text-foreground"
        aria-label={show ? "Verberg waarde" : "Toon waarde"}
      >
        {show ? <EyeOff className="size-4" /> : <Eye className="size-4" />}
      </button>
    </div>
  )
}
