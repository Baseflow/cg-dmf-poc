"use server"

import { apiFetch } from "@/lib/backend"
import { ROUTES } from "@/lib/routes"
import { revalidatePath } from "next/cache"

export interface ApiKoppeling {
  id: string
  name: string
  baseUrl: string
  clientId: string
  hasSecret: boolean
  clientSecret: string | null
  apiType: string
  authType: string
  validationEnabled: boolean
  enabled: boolean
  readonly: boolean
  createdAt: string
  updatedAt: string
}

type ApiKoppelingInput = {
  name: string
  baseUrl: string
  clientId: string
  clientSecret?: string
  apiType: string
  authType: string
  validationEnabled: boolean
  enabled: boolean
}

export async function createApiKoppeling(data: ApiKoppelingInput) {
  const res = await apiFetch("/settings/api-connection-settings", {
    method: "POST",
    body: JSON.stringify(data),
  })
  if (!res.ok) throw new Error(`HTTP ${res.status}`)
  revalidatePath(ROUTES.instellingen.apiKoppelingen)
}

export async function updateApiKoppeling(id: string, data: ApiKoppelingInput) {
  const res = await apiFetch(`/settings/api-connection-settings/${id}`, {
    method: "PUT",
    body: JSON.stringify(data),
  })
  if (!res.ok) throw new Error(`HTTP ${res.status}`)
  revalidatePath(ROUTES.instellingen.apiKoppelingen)
}

export async function deleteApiKoppeling(id: string) {
  const res = await apiFetch(`/settings/api-connection-settings/${id}`, {
    method: "DELETE",
  })
  if (!res.ok) throw new Error(`HTTP ${res.status}`)
  revalidatePath(ROUTES.instellingen.apiKoppelingen)
}

export async function deleteApiKoppelingen(ids: string[]) {
  const results = await Promise.allSettled(
    ids.map(async (id) => {
      const res = await apiFetch(`/settings/api-connection-settings/${id}`, {
        method: "DELETE",
      })
      if (!res.ok) throw new Error(`HTTP ${res.status}`)
    })
  )
  revalidatePath(ROUTES.instellingen.apiKoppelingen)
  const failed = results.filter(
    (r): r is PromiseRejectedResult => r.status === "rejected"
  )
  if (failed.length > 0) {
    throw new Error(
      `${failed.length} van ${ids.length} koppelingen konden niet worden verwijderd.`
    )
  }
}
