"use server"

import { apiFetch } from "@/lib/backend"
import { throwOnError } from "@/lib/errors"
import { ROUTES } from "@/lib/routes"
import { revalidatePath } from "next/cache"

export type StorageType = "S3" | "Azure Blob Storage"

export interface Repository {
  id: string
  name: string
  storageType: string
  url: string
  bucket: string
  isDefault: boolean
  readonly?: boolean
  enabled: boolean
  accessKey: string | null
  secretKey: string | null
  storageAccountName: string | null
  updatedAt: string
}

type RepositoryInput = {
  name: string
  storageType: StorageType
  url: string
  bucket: string
  isDefault: boolean
  enabled: boolean
  accessKey?: string
  secretKey?: string
  storageAccountName?: string
}

const READONLY_MSG =
  "Deze repository kan niet worden gewijzigd omdat het een omgevingsvariabele betreft."

const on409 = () => "field:name:Deze naam is al in gebruik."

export async function createRepository(data: RepositoryInput) {
  const res = await apiFetch("/settings/storage-repositories", {
    method: "POST",
    body: JSON.stringify(data),
  })
  if (!res.ok) await throwOnError(res, { on409, on403: READONLY_MSG })
  revalidatePath(ROUTES.instellingen.repositories)
}

export async function updateRepository(id: string, data: RepositoryInput) {
  const res = await apiFetch(`/settings/storage-repositories/${id}`, {
    method: "PUT",
    body: JSON.stringify(data),
  })
  if (!res.ok) await throwOnError(res, { on409, on403: READONLY_MSG })
  revalidatePath(ROUTES.instellingen.repositories)
}

export async function deleteRepository(id: string) {
  const res = await apiFetch(`/settings/storage-repositories/${id}`, {
    method: "DELETE",
  })
  if (!res.ok)
    await throwOnError(res, {
      on403:
        "Deze repository kan niet worden verwijderd omdat het een omgevingsvariabele betreft.",
    })
  revalidatePath(ROUTES.instellingen.repositories)
}

export async function setDefaultRepository(name: string) {
  const res = await apiFetch("/settings/storage-repositories/default", {
    method: "PUT",
    body: JSON.stringify({ name }),
  })
  if (!res.ok)
    await throwOnError(res, {
      on404: "Repository niet gevonden. Ververs de pagina en probeer opnieuw.",
    })
  revalidatePath(ROUTES.instellingen.repositories)
}

export async function deleteRepositories(ids: string[]) {
  const results = await Promise.allSettled(
    ids.map(async (id) => {
      const res = await apiFetch(`/settings/storage-repositories/${id}`, {
        method: "DELETE",
      })
      if (!res.ok) throw new Error(`HTTP ${res.status}`)
    })
  )
  revalidatePath(ROUTES.instellingen.repositories)
  const failed = results.filter(
    (r): r is PromiseRejectedResult => r.status === "rejected"
  )
  if (failed.length > 0) {
    throw new Error(
      `${failed.length} van ${ids.length} repositories konden niet worden verwijderd.`
    )
  }
}
