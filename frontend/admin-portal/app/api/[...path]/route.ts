import { NextRequest, NextResponse } from "next/server"

const BACKEND_URL = process.env.BACKEND_URL ?? "http://localhost:8080"

async function proxy(req: NextRequest, params: Promise<{ path: string[] }>) {
  const { path } = await params
  const { search } = new URL(req.url)
  const targetUrl = `${BACKEND_URL}/${path.join("/")}${search}`

  const headers = new Headers(req.headers)
  headers.delete("host")

  const hasBody = req.method !== "GET" && req.method !== "HEAD"

  const res = await fetch(targetUrl, {
    method: req.method,
    headers,
    body: hasBody ? req.body : undefined,
    // @ts-expect-error — duplex is required for streaming request bodies but missing from lib.dom types
    duplex: "half",
  })

  return new NextResponse(res.body, {
    status: res.status,
    headers: res.headers,
  })
}

export const GET = (
  req: NextRequest,
  { params }: { params: Promise<{ path: string[] }> }
) => proxy(req, params)
export const POST = (
  req: NextRequest,
  { params }: { params: Promise<{ path: string[] }> }
) => proxy(req, params)
export const PUT = (
  req: NextRequest,
  { params }: { params: Promise<{ path: string[] }> }
) => proxy(req, params)
export const PATCH = (
  req: NextRequest,
  { params }: { params: Promise<{ path: string[] }> }
) => proxy(req, params)
export const DELETE = (
  req: NextRequest,
  { params }: { params: Promise<{ path: string[] }> }
) => proxy(req, params)
