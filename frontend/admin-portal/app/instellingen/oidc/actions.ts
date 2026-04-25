"use server"

import { auth } from "@/auth"
import { revalidatePath } from "next/cache"

const API_URL = process.env.BACKEND_URL ?? "http://localhost:8080"

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
  const session = await auth()
  const res = await fetch(`${API_URL}/admin/oidc-providers`, {
    method: "POST",
    headers: {
      Authorization: `Bearer ${session?.accessToken}`,
      "Content-Type": "application/json",
    },
    body: JSON.stringify(data),
  })
  if (!res.ok) throw new Error(`HTTP ${res.status}`)
  revalidatePath("/instellingen/oidc")
}

export async function updateOidcProvider(id: string, data: OidcProviderInput) {
  const session = await auth()
  const res = await fetch(`${API_URL}/admin/oidc-providers/${id}`, {
    method: "PUT",
    headers: {
      Authorization: `Bearer ${session?.accessToken}`,
      "Content-Type": "application/json",
    },
    body: JSON.stringify(data),
  })
  if (!res.ok) throw new Error(`HTTP ${res.status}`)
  revalidatePath("/instellingen/oidc")
}

export async function deleteOidcProvider(id: string) {
  const session = await auth()
  const res = await fetch(`${API_URL}/admin/oidc-providers/${id}`, {
    method: "DELETE",
    headers: { Authorization: `Bearer ${session?.accessToken}` },
  })
  if (!res.ok) throw new Error(`HTTP ${res.status}`)
  revalidatePath("/instellingen/oidc")
}
