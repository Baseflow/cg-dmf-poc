import { apiFetch } from "@/lib/backend"
import { type Repository } from "./actions"
import { RepositoryList } from "./repository-list"

export default async function Page() {
  const res = await apiFetch("/settings/storage-repositories")
  if (!res.ok)
    throw new Error(`Kon de repositories niet laden. (HTTP ${res.status})`)
  const repositories: Repository[] = await res.json()

  return (
    <div className="flex flex-col gap-6 p-6">
      <h1 className="text-2xl font-semibold">Opslag repositories</h1>
      <RepositoryList repositories={repositories} />
    </div>
  )
}
