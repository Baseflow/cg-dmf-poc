"use server"

import { apiFetch } from "@/lib/backend"
import { throwOnError } from "@/lib/errors"
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

const READONLY_MSG =
  "Deze koppeling kan niet worden gewijzigd omdat het een omgevingsvariabele betreft."

const on409 = () => "field:name:Deze naam is al in gebruik."

export async function createApiKoppeling(data: ApiKoppelingInput) {
  const res = await apiFetch("/settings/api-connection-settings", {
    method: "POST",
    body: JSON.stringify(data),
  })
  if (!res.ok) await throwOnError(res, { on409, on403: READONLY_MSG })
  revalidatePath(ROUTES.instellingen.apiKoppelingen)
}

export async function updateApiKoppeling(id: string, data: ApiKoppelingInput) {
  const res = await apiFetch(`/settings/api-connection-settings/${id}`, {
    method: "PUT",
    body: JSON.stringify(data),
  })
  if (!res.ok) await throwOnError(res, { on409, on403: READONLY_MSG })
  revalidatePath(ROUTES.instellingen.apiKoppelingen)
}

export async function deleteApiKoppeling(id: string) {
  const res = await apiFetch(`/settings/api-connection-settings/${id}`, {
    method: "DELETE",
  })
  if (!res.ok)
    await throwOnError(res, {
      on403:
        "Deze koppeling kan niet worden verwijderd omdat het een omgevingsvariabele betreft.",
    })
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
