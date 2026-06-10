import { NextResponse } from "next/server"

export async function GET() {
  const baseUrl = process.env.BACKEND_URL
  if (!baseUrl) {
    return NextResponse.json(
      { error: "BACKEND_URL is not defined" },
      { status: 500 }
    )
  }

  const res = await fetch(`${baseUrl}/docs/openapi/documenten.json`, {
    cache: "no-store",
  })

  if (!res.ok) {
    return NextResponse.json(
      { error: `Failed to fetch OpenAPI spec: ${res.status}` },
      { status: res.status }
    )
  }

  const spec = await res.json()
  return NextResponse.json(spec)
}