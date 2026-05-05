import { apiFetch } from "@/lib/backend"
import { type ApplicationSetting } from "./actions"
import { ApplicationList } from "./application-list"

export default async function Page() {
  const res = await apiFetch("/settings/application-settings")
  if (!res.ok)
    throw new Error(
      `Kon de applicatie-instellingen niet laden. (HTTP ${res.status})`
    )
  const applications: ApplicationSetting[] = await res.json()

  return (
    <div className="p-6">
      <ApplicationList applications={applications} />
    </div>
  )
}
