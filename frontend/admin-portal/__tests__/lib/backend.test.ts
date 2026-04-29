import { afterEach, beforeEach, describe, expect, it, vi } from "vitest"
import { apiFetch, BACKEND_URL } from "@/lib/backend"

vi.mock("@/auth", () => ({
  auth: vi.fn().mockResolvedValue({ accessToken: "test-token" }),
}))

const originalFetch = global.fetch

describe("apiFetch", () => {
  beforeEach(() => {
    global.fetch = vi.fn()
  })

  afterEach(() => {
    global.fetch = originalFetch
    vi.clearAllMocks()
  })

  it("calls fetch with the correct full URL", async () => {
    vi.mocked(global.fetch).mockResolvedValueOnce(new Response(null, { status: 200 }))
    await apiFetch("/test/path")
    const [url] = vi.mocked(global.fetch).mock.calls[0] as [string, RequestInit]
    expect(url).toBe(`${BACKEND_URL}/test/path`)
  })

  it("includes the Bearer token in the Authorization header", async () => {
    vi.mocked(global.fetch).mockResolvedValueOnce(new Response(null, { status: 200 }))
    await apiFetch("/test")
    const [, options] = vi.mocked(global.fetch).mock.calls[0] as [string, RequestInit]
    const headers = options.headers as Record<string, string>
    expect(headers["Authorization"]).toBe("Bearer test-token")
  })

  it("uses an empty Bearer token when session has no accessToken", async () => {
    const { auth } = await import("@/auth")
    vi.mocked(auth).mockResolvedValueOnce(null)
    vi.mocked(global.fetch).mockResolvedValueOnce(new Response(null, { status: 200 }))
    await apiFetch("/test")
    const [, options] = vi.mocked(global.fetch).mock.calls[0] as [string, RequestInit]
    const headers = options.headers as Record<string, string>
    expect(headers["Authorization"]).toBe("Bearer ")
  })

  it("does not set Content-Type when no body is provided", async () => {
    vi.mocked(global.fetch).mockResolvedValueOnce(new Response(null, { status: 200 }))
    await apiFetch("/test")
    const [, options] = vi.mocked(global.fetch).mock.calls[0] as [string, RequestInit]
    const headers = options.headers as Record<string, string>
    expect(headers["Content-Type"]).toBeUndefined()
  })

  it("sets Content-Type to application/json when a body is provided", async () => {
    vi.mocked(global.fetch).mockResolvedValueOnce(new Response(null, { status: 200 }))
    await apiFetch("/test", { method: "POST", body: JSON.stringify({ foo: "bar" }) })
    const [, options] = vi.mocked(global.fetch).mock.calls[0] as [string, RequestInit]
    const headers = options.headers as Record<string, string>
    expect(headers["Content-Type"]).toBe("application/json")
  })

  it("allows caller-provided headers to override defaults", async () => {
    vi.mocked(global.fetch).mockResolvedValueOnce(new Response(null, { status: 200 }))
    await apiFetch("/test", {
      headers: { Authorization: "Bearer override", "X-Custom": "value" },
    })
    const [, options] = vi.mocked(global.fetch).mock.calls[0] as [string, RequestInit]
    const headers = options.headers as Record<string, string>
    expect(headers["Authorization"]).toBe("Bearer override")
    expect(headers["X-Custom"]).toBe("value")
  })

  it("forwards the HTTP method", async () => {
    vi.mocked(global.fetch).mockResolvedValueOnce(new Response(null, { status: 200 }))
    await apiFetch("/test", { method: "DELETE" })
    const [, options] = vi.mocked(global.fetch).mock.calls[0] as [string, RequestInit]
    expect(options.method).toBe("DELETE")
  })

  it("returns the fetch response", async () => {
    const mockResponse = new Response(JSON.stringify({ ok: true }), { status: 200 })
    vi.mocked(global.fetch).mockResolvedValueOnce(mockResponse)
    const response = await apiFetch("/test")
    expect(response).toBe(mockResponse)
  })
})
