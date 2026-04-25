"use server"

import { auth } from "@/auth"
import { revalidatePath } from "next/cache"

const API_URL = process.env.BACKEND_URL ?? "http://localhost:8080"

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
  const session = await auth()
  const res = await fetch(`${API_URL}/admin/storage-repositories`, {
    method: "POST",
    headers: {
      Authorization: `Bearer ${session?.accessToken}`,
      "Content-Type": "application/json",
    },
    body: JSON.stringify(data),
  })
  if (!res.ok) throw new Error(`HTTP ${res.status}`)
  revalidatePath("/instellingen/repositories")
}

export async function updateRepository(id: string, data: RepositoryInput) {
  const session = await auth()
  const res = await fetch(`${API_URL}/admin/storage-repositories/${id}`, {
    method: "PUT",
    headers: {
      Authorization: `Bearer ${session?.accessToken}`,
      "Content-Type": "application/json",
    },
    body: JSON.stringify(data),
  })
  if (!res.ok) throw new Error(`HTTP ${res.status}`)
  revalidatePath("/instellingen/repositories")
}

export async function deleteRepository(id: string) {
  const session = await auth()
  const res = await fetch(`${API_URL}/admin/storage-repositories/${id}`, {
    method: "DELETE",
    headers: { Authorization: `Bearer ${session?.accessToken}` },
  })
  if (!res.ok) throw new Error(`HTTP ${res.status}`)
  revalidatePath("/instellingen/repositories")
}
