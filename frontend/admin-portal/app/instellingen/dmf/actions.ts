const API_URL = process.env.NEXT_PUBLIC_API_URL ?? ""

export interface DmfSettings {
  triggerSize: number
  chunkSize: number
  validationEnabled: boolean
}

export async function fetchDmfSettings(
  accessToken: string
): Promise<DmfSettings> {
  const res = await fetch(`${API_URL}/admin/dmf-settings`, {
    headers: { Authorization: `Bearer ${accessToken}` },
  })
  if (!res.ok)
    throw new Error(`Kon de DMF-instellingen niet laden. (HTTP ${res.status})`)
  return res.json() as Promise<DmfSettings>
}

export async function saveDmfSettings(
  accessToken: string,
  data: DmfSettings
): Promise<void> {
  const res = await fetch(`${API_URL}/admin/dmf-settings`, {
    method: "PUT",
    headers: {
      Authorization: `Bearer ${accessToken}`,
      "Content-Type": "application/json",
    },
    body: JSON.stringify(data),
  })
  if (!res.ok) throw new Error(`HTTP ${res.status}`)
}
