const SPEC_URL =
  process.env.NEXT_PUBLIC_OPENAPI_URL ??
  "https://cg-dmf.dev.baseflow.com/docs/openapi/documenten.json"

export async function GET() {
  const res = await fetch(SPEC_URL)
  if (!res.ok) {
    return new Response(`Failed to fetch OpenAPI spec: ${res.status}`, {
      status: res.status,
    })
  }

  const body = await res.text()
  const contentType = res.headers.get("content-type") ?? "application/json"

  return new Response(body, {
    headers: { "content-type": contentType },
  })
}
