import { auth } from "@/auth"
import { redirect } from "next/navigation"
import { type ApplicationSetting } from "./actions"
import { ApplicationList } from "./application-list"

const API_URL = process.env.BACKEND_URL ?? "http://localhost:8080"

export default async function Page() {
  const session = await auth()
  if (!session || session.error) redirect("/api/auth/signin")

  const res = await fetch(`${API_URL}/admin/application-settings`, {
    headers: { Authorization: `Bearer ${session.accessToken}` },
  })
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
