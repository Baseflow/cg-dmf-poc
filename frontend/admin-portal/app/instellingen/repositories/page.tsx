import { auth } from "@/auth"
import { redirect } from "next/navigation"
import { type Repository } from "./actions"
import { RepositoryList } from "./repository-list"

const API_URL = process.env.BACKEND_URL ?? "http://localhost:8080"

export default async function Page() {
  const session = await auth()
  if (!session || session.error) redirect("/api/auth/signin")

  const res = await fetch(`${API_URL}/admin/storage-repositories`, {
    headers: { Authorization: `Bearer ${session.accessToken}` },
  })
  if (!res.ok)
    throw new Error(
      `Kon de repositories niet laden. (HTTP ${res.status})`
    )
  const repositories: Repository[] = await res.json()

  return (
    <div className="flex min-h-svh p-6">
      <RepositoryList repositories={repositories} />
    </div>
  )
}
