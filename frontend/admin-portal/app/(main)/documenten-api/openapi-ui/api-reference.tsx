import { ApiReferenceClient } from "./api-reference-client"

export async function ApiReference() {
  const SPEC_URL = process.env.DOCUMENTEN_API_OPENAPI_SPEC_URL
  if (!SPEC_URL) throw new Error("Documenten API OpenAPI spec URL is not defined")

  const res = await fetch(SPEC_URL, { cache: "no-store" })
  if (!res.ok) {
    throw new Error(
      `Failed to fetch Documenten API OpenAPI spec: ${res.status}`
    )
  }
  const content = await res.text()

  return <ApiReferenceClient content={content} />
}
