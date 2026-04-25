import { auth } from "@/auth"
import { redirect } from "next/navigation"
import { type OidcProvider } from "./actions"
import { OidcProviderList } from "./oidc-provider-list"

const API_URL = process.env.BACKEND_URL ?? "http://localhost:8080"

export default async function Page() {
  const session = await auth()
  if (!session || session.error) redirect("/api/auth/signin")

  const res = await fetch(`${API_URL}/admin/oidc-providers`, {
    headers: { Authorization: `Bearer ${session.accessToken}` },
  })
  if (!res.ok)
    throw new Error(
      `Kon de OIDC-providers niet laden. (HTTP ${res.status})`
    )
  const providers: OidcProvider[] = await res.json()

  return (
    <div className="flex min-h-svh p-6">
      <OidcProviderList providers={providers} />
    </div>
  )
}
