"use server"

import { apiFetch } from "@/lib/backend"
import { throwOnError } from "@/lib/errors"
import { ROUTES } from "@/lib/routes"
import { revalidatePath } from "next/cache"

export interface ApplicationSetting {
  id: string
  name: string
  clientId: string
  hasSecret: boolean
  clientSecret: string | null
  updatedAt: string
  readonly?: boolean
}

type ApplicationInput = {
  name: string
  clientId: string
  clientSecret?: string
}

const READONLY_MSG =
  "Deze instelling kan niet worden gewijzigd omdat het een omgevingsvariabele betreft."

const on409 = (detail: string | null) =>
  detail?.includes("clientId")
    ? "field:clientId:Dit client ID is al in gebruik."
    : "field:name:Deze naam is al in gebruik."

export async function createApplication(data: ApplicationInput) {
  const res = await apiFetch("/settings/application-settings", {
    method: "POST",
    body: JSON.stringify(data),
  })
  if (!res.ok) await throwOnError(res, { on409, on403: READONLY_MSG })
  revalidatePath(ROUTES.instellingen.applicaties)
}

export async function updateApplication(id: string, data: ApplicationInput) {
  const res = await apiFetch(`/settings/application-settings/${id}`, {
    method: "PUT",
    body: JSON.stringify(data),
  })
  if (!res.ok) await throwOnError(res, { on409, on403: READONLY_MSG })
  revalidatePath(ROUTES.instellingen.applicaties)
}

export async function deleteApplication(id: string) {
  const res = await apiFetch(`/settings/application-settings/${id}`, {
    method: "DELETE",
  })
  if (!res.ok)
    await throwOnError(res, {
      on403:
        "Deze instelling kan niet worden verwijderd omdat het een omgevingsvariabele betreft.",
    })
  revalidatePath(ROUTES.instellingen.applicaties)
}

export async function deleteApplications(ids: string[]) {
  const results = await Promise.allSettled(
    ids.map(async (id) => {
      const res = await apiFetch(`/settings/application-settings/${id}`, {
        method: "DELETE",
      })
      if (!res.ok) throw new Error(`HTTP ${res.status}`)
    })
  )
  revalidatePath(ROUTES.instellingen.applicaties)
  const failed = results.filter(
    (r): r is PromiseRejectedResult => r.status === "rejected"
  )
  if (failed.length > 0) {
    throw new Error(
      `${failed.length} van ${ids.length} applicaties konden niet worden verwijderd.`
    )
  }
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
  if (!res.ok)
    await throwOnError(res, {
      on403:
        "Het secret van deze instelling kan niet worden geroteerd omdat het een omgevingsvariabele betreft.",
    })
  const { secret } = (await res.json()) as { secret: string }
  revalidatePath(ROUTES.instellingen.applicaties)
  return secret
}
