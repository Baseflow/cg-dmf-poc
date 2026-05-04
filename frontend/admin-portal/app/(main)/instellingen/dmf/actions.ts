"use server"

import { apiFetch } from "@/lib/backend"
import { revalidatePath } from "next/cache"
import { z } from "zod"

export interface DmfSettings {
  triggerSize: number
  chunkSize: number
  validationEnabled: boolean
}

export type FormState = {
  errors?: { triggerSize?: string; chunkSize?: string }
  error?: string
  saved?: boolean
}

const settingsSchema = z.object({
  triggerSize: z.coerce
    .number()
    .int("Moet een geheel getal zijn.")
    .min(1, "Moet minimaal 1 byte zijn."),
  chunkSize: z.coerce
    .number()
    .int("Moet een geheel getal zijn.")
    .min(1, "Moet minimaal 1 byte zijn."),
  validationEnabled: z.boolean(),
})

export async function saveDmfSettings(
  _prev: FormState,
  formData: FormData
): Promise<FormState> {
  const result = settingsSchema.safeParse({
    triggerSize: formData.get("triggerSize"),
    chunkSize: formData.get("chunkSize"),
    validationEnabled: formData.get("validationEnabled") === "on",
  })

  if (!result.success) {
    const errors: FormState["errors"] = {}
    for (const issue of result.error.issues) {
      const key = issue.path[0] as keyof NonNullable<FormState["errors"]>
      if (!errors[key]) errors[key] = issue.message
    }
    return { errors }
  }

  const res = await apiFetch("/admin/dmf-settings", {
    method: "PUT",
    body: JSON.stringify(result.data),
  })

  if (!res.ok) return { error: "Opslaan mislukt. Probeer het opnieuw." }
  revalidatePath("/instellingen/dmf")
  return { saved: true }
}
