"use server"

import { apiFetch } from "@/lib/backend"
import { NetworkError, readDetail } from "@/lib/errors"
import { ROUTES } from "@/lib/routes"
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

  let res: Response
  try {
    res = await apiFetch("/settings/dmf-settings", {
      method: "PUT",
      body: JSON.stringify(result.data),
    })
  } catch (e) {
    return {
      error:
        e instanceof NetworkError
          ? e.message
          : "Opslaan mislukt. Probeer het opnieuw.",
    }
  }

  if (!res.ok) {
    const detail = await readDetail(res)
    return { error: detail ?? "Opslaan mislukt. Probeer het opnieuw." }
  }
  revalidatePath(ROUTES.instellingen.dmf)
  return { saved: true }
}
