import { afterEach, beforeEach, describe, expect, it, vi } from "vitest"
import { saveDmfSettings } from "./actions"

// Mock the auth module so we can control the token
vi.mock("@/auth", () => ({
  auth: vi.fn().mockResolvedValue({ accessToken: "test-token" }),
}))

const originalFetch = global.fetch

describe("saveDmfSettings", () => {
  beforeEach(() => {
    global.fetch = vi.fn()
  })

  afterEach(() => {
    global.fetch = originalFetch
    vi.clearAllMocks()
  })

  it("sends a PUT request to the correct endpoint with the settings", async () => {
    vi.mocked(global.fetch).mockResolvedValueOnce(
      new Response(null, { status: 200 })
    )

    await saveDmfSettings({
      triggerSize: 1024,
      chunkSize: 512,
      validationEnabled: true,
    })

    expect(global.fetch).toHaveBeenCalledOnce()
    const [url, options] = vi.mocked(global.fetch).mock.calls[0] as [
      string,
      RequestInit,
    ]
    expect(url).toMatch(/\/admin\/dmf-settings$/)
    expect(options.method).toBe("PUT")
    expect(JSON.parse(options.body as string)).toEqual({
      triggerSize: 1024,
      chunkSize: 512,
      validationEnabled: true,
    })
  })

  it("includes the Bearer token in the Authorization header", async () => {
    vi.mocked(global.fetch).mockResolvedValueOnce(
      new Response(null, { status: 200 })
    )

    await saveDmfSettings({
      triggerSize: 1024,
      chunkSize: 512,
      validationEnabled: false,
    })

    const [, options] = vi.mocked(global.fetch).mock.calls[0] as [
      string,
      RequestInit,
    ]
    const headers = options.headers as Record<string, string>
    expect(headers["Authorization"]).toBe("Bearer test-token")
  })

  it("throws when the response is not ok", async () => {
    vi.mocked(global.fetch).mockResolvedValueOnce(
      new Response(null, { status: 500 })
    )

    await expect(
      saveDmfSettings({ triggerSize: 1, chunkSize: 1, validationEnabled: true })
    ).rejects.toThrow("HTTP 500")
  })
})
