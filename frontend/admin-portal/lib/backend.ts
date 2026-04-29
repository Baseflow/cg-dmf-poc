import { auth } from "@/auth"

export const BACKEND_URL = process.env.BACKEND_URL ?? "http://localhost:8080"

export async function apiFetch(
  path: string,
  init: RequestInit = {}
): Promise<Response> {
  const session = await auth()
  return fetch(`${BACKEND_URL}${path}`, {
    ...init,
    headers: {
      Authorization: `Bearer ${session?.accessToken ?? ""}`,
      ...(init.body !== undefined ? { "Content-Type": "application/json" } : {}),
      ...init.headers,
    },
  })
}
