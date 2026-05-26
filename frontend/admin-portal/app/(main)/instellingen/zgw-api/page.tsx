import { apiFetch } from "@/lib/backend"
import { type ZgwApiSetting } from "./actions"
import { ZgwApiList } from "./zgw-api-list"

export default async function Page() {
  const res = await apiFetch("/settings/zgw-api-settings")
  if (!res.ok)
    throw new Error(
      `Kon de ZGW API-instellingen niet laden. (HTTP ${res.status})`
    )
  const settings: ZgwApiSetting[] = await res.json()

  return (
    <div className="flex flex-col gap-6 p-6">
      <h1 className="text-2xl font-semibold">ZGW API koppelingen</h1>
      <ZgwApiList settings={settings} />
    </div>
  )
}
