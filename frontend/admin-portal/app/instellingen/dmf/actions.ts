import type Keycloak from "keycloak-js"

const API_URL = process.env.NEXT_PUBLIC_API_URL ?? ""

export interface DmfSettings {
  triggerSize: number
  chunkSize: number
  validationEnabled: boolean
}

export async function fetchDmfSettings(
  keycloak: Keycloak
): Promise<DmfSettings> {
  await keycloak.updateToken(30)
  const res = await fetch(`${API_URL}/admin/dmf-settings`, {
    headers: { Authorization: `Bearer ${keycloak.token ?? ""}` },
  })
  if (!res.ok)
    throw new Error(`Kon de DMF-instellingen niet laden. (HTTP ${res.status})`)
  return res.json() as Promise<DmfSettings>
}

export async function saveDmfSettings(
  keycloak: Keycloak,
  data: DmfSettings
): Promise<void> {
  await keycloak.updateToken(30)
  const res = await fetch(`${API_URL}/admin/dmf-settings`, {
    method: "PUT",
    headers: {
      Authorization: `Bearer ${keycloak.token ?? ""}`,
      "Content-Type": "application/json",
    },
    body: JSON.stringify(data),
  })
  if (!res.ok) throw new Error(`HTTP ${res.status}`)
}
