import { apiFetch } from "@/lib/backend"
import { type DmfSettingEntry } from "./actions"
import DmfSettingsForm from "./dmf-settings-form"

export default async function Page() {
  const res = await apiFetch("/settings/dmf-settings")
  if (!res.ok)
    throw new Error(
      `Kon de DMF systeeminstellingen niet laden. (HTTP ${res.status})`
    )
  const settings: DmfSettingEntry[] = await res.json()

  return (
    <div className="flex flex-col gap-6 p-6">
      <h1 className="text-2xl font-semibold">DMF systeeminstellingen</h1>
      <DmfSettingsForm settings={settings} />
    </div>
  )
}
