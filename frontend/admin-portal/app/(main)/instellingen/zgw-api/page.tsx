import { apiFetch } from "@/lib/backend"
import { type ZgwApiSetting } from "./actions"
import { ZgwApiList } from "./zgw-api-list"

export default async function Page() {
  const res = await apiFetch("/admin/zgw-api-settings")
  if (!res.ok)
    throw new Error(
      `Kon de ZGW API-instellingen niet laden. (HTTP ${res.status})`
    )
  const settings: ZgwApiSetting[] = await res.json()

  return (
    <div className="p-6">
      <ZgwApiList settings={settings} />
    </div>
  )
}
