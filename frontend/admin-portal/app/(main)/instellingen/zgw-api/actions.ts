"use server"

import { apiFetch } from "@/lib/backend"
import { ROUTES } from "@/lib/routes"
import { revalidatePath } from "next/cache"

export interface ZgwApiSetting {
  id: string
  name: string
  baseUrl: string
  clientId: string
  hasSecret: boolean
  clientSecret: string | null
  updatedAt: string
}

type ZgwApiSettingInput = {
  name: string
  baseUrl: string
  clientId: string
  clientSecret?: string
}

export async function createZgwApiSetting(data: ZgwApiSettingInput) {
  const res = await apiFetch("/settings/zgw-api-settings", {
    method: "POST",
    body: JSON.stringify(data),
  })
  if (!res.ok) throw new Error(`HTTP ${res.status}`)
  revalidatePath(ROUTES.instellingen.zgwApi)
}

export async function updateZgwApiSetting(
  id: string,
  data: ZgwApiSettingInput
) {
  const res = await apiFetch(`/settings/zgw-api-settings/${id}`, {
    method: "PUT",
    body: JSON.stringify(data),
  })
  if (!res.ok) throw new Error(`HTTP ${res.status}`)
  revalidatePath(ROUTES.instellingen.zgwApi)
}

export async function deleteZgwApiSetting(id: string) {
  const res = await apiFetch(`/settings/zgw-api-settings/${id}`, {
    method: "DELETE",
  })
  if (!res.ok) throw new Error(`HTTP ${res.status}`)
  revalidatePath(ROUTES.instellingen.zgwApi)
}

export async function deleteZgwApiSettings(ids: string[]) {
  await Promise.all(
    ids.map(async (id) => {
      const res = await apiFetch(`/settings/zgw-api-settings/${id}`, {
        method: "DELETE",
      })
      if (!res.ok) throw new Error(`HTTP ${res.status}`)
    })
  )
  revalidatePath(ROUTES.instellingen.zgwApi)
}
