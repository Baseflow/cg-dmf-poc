"use server"

import { apiFetch } from "@/lib/backend"
import { revalidatePath } from "next/cache"

export type StorageType = "S3" | "Azure Blob Storage"

export interface Repository {
  id: string
  name: string
  storageType: string
  url: string
  bucket: string
  isDefault: boolean
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

export async function createRepository(data: RepositoryInput) {
  const res = await apiFetch("/admin/storage-repositories", {
    method: "POST",
    body: JSON.stringify(data),
  })
  if (!res.ok) throw new Error(`HTTP ${res.status}`)
  revalidatePath("/instellingen/repositories")
}

export async function updateRepository(id: string, data: RepositoryInput) {
  const res = await apiFetch(`/admin/storage-repositories/${id}`, {
    method: "PUT",
    body: JSON.stringify(data),
  })
  if (!res.ok) throw new Error(`HTTP ${res.status}`)
  revalidatePath("/instellingen/repositories")
}

export async function deleteRepository(id: string) {
  const res = await apiFetch(`/admin/storage-repositories/${id}`, {
    method: "DELETE",
  })
  if (!res.ok) throw new Error(`HTTP ${res.status}`)
  revalidatePath("/instellingen/repositories")
}

export async function deleteRepositories(ids: string[]) {
  await Promise.all(
    ids.map(async (id) => {
      const res = await apiFetch(`/admin/storage-repositories/${id}`, {
        method: "DELETE",
      })
      if (!res.ok) throw new Error(`HTTP ${res.status}`)
    })
  )
  revalidatePath("/instellingen/repositories")
}
