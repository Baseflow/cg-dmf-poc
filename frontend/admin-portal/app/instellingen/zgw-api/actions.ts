"use server"

import { auth } from "@/auth"
import { revalidatePath } from "next/cache"

const API_URL = process.env.BACKEND_URL ?? "http://localhost:8080"

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
  const session = await auth()
  const res = await fetch(`${API_URL}/admin/zgw-api-settings`, {
    method: "POST",
    headers: {
      Authorization: `Bearer ${session?.accessToken}`,
      "Content-Type": "application/json",
    },
    body: JSON.stringify(data),
  })
  if (!res.ok) throw new Error(`HTTP ${res.status}`)
  revalidatePath("/instellingen/zgw-api")
}

export async function updateZgwApiSetting(
  id: string,
  data: ZgwApiSettingInput
) {
  const session = await auth()
  const res = await fetch(`${API_URL}/admin/zgw-api-settings/${id}`, {
    method: "PUT",
    headers: {
      Authorization: `Bearer ${session?.accessToken}`,
      "Content-Type": "application/json",
    },
    body: JSON.stringify(data),
  })
  if (!res.ok) throw new Error(`HTTP ${res.status}`)
  revalidatePath("/instellingen/zgw-api")
}

export async function deleteZgwApiSetting(id: string) {
  const session = await auth()
  const res = await fetch(`${API_URL}/admin/zgw-api-settings/${id}`, {
    method: "DELETE",
    headers: { Authorization: `Bearer ${session?.accessToken}` },
  })
  if (!res.ok) throw new Error(`HTTP ${res.status}`)
  revalidatePath("/instellingen/zgw-api")
}

export async function deleteZgwApiSettings(ids: string[]) {
  const session = await auth()
  await Promise.all(
    ids.map((id) =>
      fetch(`${API_URL}/admin/zgw-api-settings/${id}`, {
        method: "DELETE",
        headers: { Authorization: `Bearer ${session?.accessToken}` },
      }).then((res) => {
        if (!res.ok) throw new Error(`HTTP ${res.status}`)
      })
    )
  )
  revalidatePath("/instellingen/zgw-api")
}
