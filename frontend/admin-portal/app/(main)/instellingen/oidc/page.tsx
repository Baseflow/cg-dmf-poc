import { apiFetch } from "@/lib/backend"
import { type OidcProvider } from "./actions"
import { OidcProviderList } from "./oidc-provider-list"

export default async function Page() {
  const res = await apiFetch("/admin/oidc-providers")
  if (!res.ok)
    throw new Error(`Kon de OIDC-providers niet laden. (HTTP ${res.status})`)
  const providers: OidcProvider[] = await res.json()

  return (
    <div className="flex flex-col gap-6 p-6">
      <h1 className="text-2xl font-semibold">OpenID Connect authenticatieproviders</h1>
      <OidcProviderList providers={providers} />
    </div>
  )
}
