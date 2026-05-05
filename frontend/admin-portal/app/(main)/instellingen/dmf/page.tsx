import { apiFetch } from "@/lib/backend"
import { type DmfSettings } from "./actions"
import DmfSettingsForm from "./dmf-settings-form"

export default async function Page() {
  const res = await apiFetch("/settings/dmf-settings")
  if (!res.ok)
    throw new Error(`Kon de DMF-instellingen niet laden. (HTTP ${res.status})`)
  const settings: DmfSettings = await res.json()

  return (
    <div className="p-6">
      <DmfSettingsForm initialSettings={settings} />
    </div>
  )
}
