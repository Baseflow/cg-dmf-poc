import { auth } from "@/auth"

export const BACKEND_URL = process.env.BACKEND_URL ?? "http://localhost:8080"

export async function apiFetch(
  path: string,
  init: RequestInit = {}
): Promise<Response> {
  const session = await auth()
  const authHeaders: Record<string, string> = session?.accessToken
    ? { Authorization: `Bearer ${session.accessToken}` }
    : {}

  return fetch(`${BACKEND_URL}${path}`, {
    cache: "no-store",
    ...init,
    headers: {
      ...authHeaders,
      ...(init.body !== undefined
        ? { "Content-Type": "application/json" }
        : {}),
      ...init.headers,
    },
  })
}
