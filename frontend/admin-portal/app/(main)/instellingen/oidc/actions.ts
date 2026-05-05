"use server"

import { apiFetch } from "@/lib/backend"
import { revalidatePath } from "next/cache"

export interface OidcProvider {
  id: string
  name: string
  issuer: string
  clientId: string
  hasSecret: boolean
  clientSecret: string | null
  updatedAt: string
}

type OidcProviderInput = {
  name: string
  issuer: string
  clientId: string
  clientSecret?: string
}

export async function createOidcProvider(data: OidcProviderInput) {
  const res = await apiFetch("/settings/oidc-providers", {
    method: "POST",
    body: JSON.stringify(data),
  })
  if (!res.ok) throw new Error(`HTTP ${res.status}`)
  revalidatePath("/instellingen/oidc")
}

export async function updateOidcProvider(id: string, data: OidcProviderInput) {
  const res = await apiFetch(`/settings/oidc-providers/${id}`, {
    method: "PUT",
    body: JSON.stringify(data),
  })
  if (!res.ok) throw new Error(`HTTP ${res.status}`)
  revalidatePath("/instellingen/oidc")
}

export async function deleteOidcProvider(id: string) {
  const res = await apiFetch(`/settings/oidc-providers/${id}`, {
    method: "DELETE",
  })
  if (!res.ok) throw new Error(`HTTP ${res.status}`)
  revalidatePath("/instellingen/oidc")
}

export async function deleteOidcProviders(ids: string[]) {
  await Promise.all(
    ids.map(async (id) => {
      const res = await apiFetch(`/settings/oidc-providers/${id}`, {
        method: "DELETE",
      })
      if (!res.ok) throw new Error(`HTTP ${res.status}`)
    })
  )
  revalidatePath("/instellingen/oidc")
}
