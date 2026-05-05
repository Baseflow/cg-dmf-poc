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
    <div className="flex flex-col gap-6 p-6">
      <h1 className="text-2xl font-semibold">Applicaties</h1>
      <ApplicationList applications={applications} />
    </div>
  )
}
