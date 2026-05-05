import { ApiReferenceClient } from "./api-reference-client"

const SPEC_URL = process.env.DOCUMENTEN_API_OPENAPI_SPEC_URL

if (!SPEC_URL) throw new Error("Documenten API OpenAPI spec URL is not defined")

export async function ApiReference() {
  const res = await fetch(SPEC_URL!)
  if (!res.ok) {
    throw new Error(
      `Failed to fetch Documenten API OpenAPI spec: ${res.status}`
    )
  }
  const content = await res.text()

  return <ApiReferenceClient content={content} />
}
