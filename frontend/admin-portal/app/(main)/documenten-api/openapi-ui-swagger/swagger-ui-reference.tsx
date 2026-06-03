import { SwaggerUiReferenceClient } from "./swagger-ui-reference-client"

export async function SwaggerUiReference() {
  const baseUrl = process.env.BACKEND_URL
  if (!baseUrl) throw new Error("BACKEND_URL is not defined")
  const specUrl = `${baseUrl}/docs/openapi/documenten.json`

  const res = await fetch(specUrl, { cache: "no-store" })
  if (!res.ok) {
    throw new Error(
      `Failed to fetch Documenten API OpenAPI spec: ${res.status}`
    )
  }
  const spec = await res.json()

  return <SwaggerUiReferenceClient spec={spec} />
}
