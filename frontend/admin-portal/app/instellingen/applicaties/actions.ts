"use server"

import { auth } from "@/auth"
import { revalidatePath } from "next/cache"

const API_URL = process.env.BACKEND_URL ?? "http://localhost:8080"

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
  const session = await auth()
  const res = await fetch(`${API_URL}/admin/application-settings`, {
    method: "POST",
    headers: {
      Authorization: `Bearer ${session?.accessToken}`,
      "Content-Type": "application/json",
    },
    body: JSON.stringify(data),
  })
  if (!res.ok) throw new Error(`HTTP ${res.status}`)
  revalidatePath("/instellingen/applicaties")
}

export async function updateApplication(id: string, data: ApplicationInput) {
  const session = await auth()
  const res = await fetch(`${API_URL}/admin/application-settings/${id}`, {
    method: "PUT",
    headers: {
      Authorization: `Bearer ${session?.accessToken}`,
      "Content-Type": "application/json",
    },
    body: JSON.stringify(data),
  })
  if (!res.ok) throw new Error(`HTTP ${res.status}`)
  revalidatePath("/instellingen/applicaties")
}

export async function deleteApplication(id: string) {
  const session = await auth()
  const res = await fetch(`${API_URL}/admin/application-settings/${id}`, {
    method: "DELETE",
    headers: { Authorization: `Bearer ${session?.accessToken}` },
  })
  if (!res.ok) throw new Error(`HTTP ${res.status}`)
  revalidatePath("/instellingen/applicaties")
}

export async function rotateApplicationSecret(
  id: string,
  newSecret?: string
): Promise<string> {
  const session = await auth()
  const res = await fetch(
    `${API_URL}/admin/application-settings/${id}/rotate-secret`,
    {
      method: "POST",
      headers: {
        Authorization: `Bearer ${session?.accessToken}`,
        "Content-Type": "application/json",
      },
      body: JSON.stringify(newSecret ? { newSecret } : {}),
    }
  )
  if (!res.ok) throw new Error(`HTTP ${res.status}`)
  const { secret } = (await res.json()) as { secret: string }
  revalidatePath("/instellingen/applicaties")
  return secret
}
