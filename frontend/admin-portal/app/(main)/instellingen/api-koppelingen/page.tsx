import { apiFetch } from "@/lib/backend"
import { type ApiKoppeling } from "./actions"
import { ApiKoppelingenList } from "./api-koppelingen-list"

export default async function Page() {
  const res = await apiFetch("/settings/api-connection-settings")
  if (!res.ok)
    throw new Error(`Kon de API koppelingen niet laden. (HTTP ${res.status})`)
  const settings: ApiKoppeling[] = await res.json()

  return (
    <div className="flex flex-col gap-6 p-6">
      <h1 className="text-2xl font-semibold">API koppelingen</h1>
      <ApiKoppelingenList settings={settings} />
    </div>
  )
}
