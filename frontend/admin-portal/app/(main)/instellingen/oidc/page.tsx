import { apiFetch } from "@/lib/backend"
import { type OidcProvider } from "./actions"
import { OidcProviderList } from "./oidc-provider-list"

export default async function Page() {
  const res = await apiFetch("/admin/oidc-providers")
  if (!res.ok)
    throw new Error(`Kon de OIDC-providers niet laden. (HTTP ${res.status})`)
  const providers: OidcProvider[] = await res.json()

  return (
    <div className="p-6">
      <OidcProviderList providers={providers} />
    </div>
  )
}
