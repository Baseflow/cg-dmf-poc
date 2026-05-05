"use server"

import { apiFetch } from "@/lib/backend"
import { revalidatePath } from "next/cache"

export interface ApplicationSetting {
  id: string
  name: string
  clientId: string
  hasSecret: boolean
  clientSecret: string | null
  updatedAt: string
}

type ApplicationInput = {
  name: string
  clientId: string
  clientSecret?: string
}

export async function createApplication(data: ApplicationInput) {
  const res = await apiFetch("/settings/application-settings", {
    method: "POST",
    body: JSON.stringify(data),
  })
  if (!res.ok) throw new Error(`HTTP ${res.status}`)
  revalidatePath("/instellingen/applicaties")
}

export async function updateApplication(id: string, data: ApplicationInput) {
  const res = await apiFetch(`/settings/application-settings/${id}`, {
    method: "PUT",
    body: JSON.stringify(data),
  })
  if (!res.ok) throw new Error(`HTTP ${res.status}`)
  revalidatePath("/instellingen/applicaties")
}

export async function deleteApplication(id: string) {
  const res = await apiFetch(`/settings/application-settings/${id}`, {
    method: "DELETE",
  })
  if (!res.ok) throw new Error(`HTTP ${res.status}`)
  revalidatePath("/instellingen/applicaties")
}

export async function deleteApplications(ids: string[]) {
  await Promise.all(
    ids.map(async (id) => {
      const res = await apiFetch(`/settings/application-settings/${id}`, {
        method: "DELETE",
      })
      if (!res.ok) throw new Error(`HTTP ${res.status}`)
    })
  )
  revalidatePath("/instellingen/applicaties")
}

export async function rotateApplicationSecret(
  id: string,
  newSecret?: string
): Promise<string> {
  const res = await apiFetch(
    `/settings/application-settings/${id}/rotate-secret`,
    {
      method: "POST",
      body: JSON.stringify(newSecret ? { newSecret } : {}),
    }
  )
  if (!res.ok) throw new Error(`HTTP ${res.status}`)
  const { secret } = (await res.json()) as { secret: string }
  revalidatePath("/instellingen/applicaties")
  return secret
}
