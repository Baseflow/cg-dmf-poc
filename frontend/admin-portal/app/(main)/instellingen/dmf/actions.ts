"use server"

import { apiFetch } from "@/lib/backend"
import { ROUTES } from "@/lib/routes"
import { revalidatePath } from "next/cache"

export type DmfSettingType = "string" | "int" | "boolean"

export interface DmfSettingEntry {
  key: string
  type: DmfSettingType
  value: string
  updatedAt: string
}

export async function upsertDmfSetting(
  key: string,
  value: string
): Promise<void> {
  const res = await apiFetch(
    `/settings/dmf-settings/${encodeURIComponent(key)}`,
    { method: "PUT", body: JSON.stringify({ value }) }
  )
  if (!res.ok) throw new Error(`HTTP ${res.status}`)
  revalidatePath(ROUTES.instellingen.dmf)
}
