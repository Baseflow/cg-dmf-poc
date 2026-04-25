import { auth } from "@/auth"
import { redirect } from "next/navigation"
import { type ZgwApiSetting } from "./actions"
import { ZgwApiList } from "./zgw-api-list"

const API_URL = process.env.BACKEND_URL ?? "http://localhost:8080"

export default async function Page() {
  const session = await auth()
  if (!session || session.error) redirect("/api/auth/signin")

  const res = await fetch(`${API_URL}/admin/zgw-api-settings`, {
    headers: { Authorization: `Bearer ${session.accessToken}` },
  })
  if (!res.ok)
    throw new Error(
      `Kon de ZGW API-instellingen niet laden. (HTTP ${res.status})`
    )
  const settings: ZgwApiSetting[] = await res.json()

  return (
    <div className="flex min-h-svh flex-col gap-4 p-6">
      <ZgwApiList settings={settings} />
    </div>
  )
}
