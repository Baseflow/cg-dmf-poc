import { apiFetch } from "@/lib/backend"
import { type Repository } from "./actions"
import { RepositoryList } from "./repository-list"

export default async function Page() {
  const res = await apiFetch("/admin/storage-repositories")
  if (!res.ok)
    throw new Error(`Kon de repositories niet laden. (HTTP ${res.status})`)
  const repositories: Repository[] = await res.json()

  return (
    <div className="flex min-h-svh p-6">
      <RepositoryList repositories={repositories} />
    </div>
  )
}
