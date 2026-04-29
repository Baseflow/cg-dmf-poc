import { apiFetch } from "@/lib/backend"
import { type ApplicationSetting } from "./actions"
import { ApplicationList } from "./application-list"

export default async function Page() {
  const res = await apiFetch("/admin/application-settings")
  if (!res.ok)
    throw new Error(
      `Kon de applicatie-instellingen niet laden. (HTTP ${res.status})`
    )
  const applications: ApplicationSetting[] = await res.json()

  return (
    <div className="flex min-h-svh p-6">
      <ApplicationList applications={applications} />
    </div>
  )
}
