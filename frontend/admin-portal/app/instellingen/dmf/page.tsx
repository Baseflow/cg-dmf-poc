import { auth } from "@/auth"
import { redirect } from "next/navigation"
import { type DmfSettings } from "./actions"
import DmfSettingsForm from "./dmf-settings-form"

const API_URL = process.env.BACKEND_URL ?? "http://localhost:8080"

export default async function Page() {
  const session = await auth()
  if (!session || session.error) redirect("/api/auth/signin")

  const res = await fetch(`${API_URL}/admin/dmf-settings`, {
    headers: { Authorization: `Bearer ${session.accessToken}` },
  })
  if (!res.ok)
    throw new Error(`Kon de DMF-instellingen niet laden. (HTTP ${res.status})`)
  const settings: DmfSettings = await res.json()

  return (
    <div className="p-6">
      <DmfSettingsForm initialSettings={settings} />
    </div>
  )
}
