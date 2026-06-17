import { NextRequest, NextResponse } from "next/server"

const BACKEND_URL = process.env.BACKEND_URL ?? "http://localhost:8080"

export async function GET(
  req: NextRequest,
  { params }: { params: Promise<{ path?: string[] }> }
) {
  const { path } = await params
  const { search } = new URL(req.url)
  const targetPath = path?.join("/") ?? ""
  const targetUrl = `${BACKEND_URL}/docs${targetPath ? `/${targetPath}` : ""}${search}`

  const headers = new Headers(req.headers)
  headers.delete("host")

  const res = await fetch(targetUrl, { headers })

  return new NextResponse(res.body, {
    status: res.status,
    headers: res.headers,
  })
}
