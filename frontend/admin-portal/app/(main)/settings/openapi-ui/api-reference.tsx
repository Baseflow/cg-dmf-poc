import { ApiReferenceClient } from "@/app/(main)/documenten-api/openapi-ui/api-reference-client"

export async function ApiReference() {
  const baseUrl = process.env.BACKEND_URL
  if (!baseUrl) throw new Error("BACKEND_URL is not defined")
  const SPEC_URL = `${baseUrl}/docs/openapi/settings.json`

  const res = await fetch(SPEC_URL, { cache: "no-store" })
  if (!res.ok) {
    throw new Error(`Failed to fetch Settings API OpenAPI spec: ${res.status}`)
  }
  const content = await res.text()

  return <ApiReferenceClient content={content} />
}