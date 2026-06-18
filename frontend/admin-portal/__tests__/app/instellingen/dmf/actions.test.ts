import { afterEach, beforeEach, describe, expect, it, vi } from "vitest"
import { upsertDmfSetting } from "@/app/(main)/instellingen/dmf/actions"

vi.mock("@/auth", () => ({
  auth: vi.fn().mockResolvedValue({ accessToken: "test-token" }),
}))

vi.mock("next/cache", () => ({
  revalidatePath: vi.fn(),
}))

const originalFetch = global.fetch

describe("upsertDmfSetting", () => {
  beforeEach(() => {
    global.fetch = vi.fn()
  })

  afterEach(() => {
    global.fetch = originalFetch
    vi.clearAllMocks()
  })

  it("sends a PUT request to the correct endpoint with the value", async () => {
    vi.mocked(global.fetch).mockResolvedValueOnce(
      new Response(null, { status: 200 })
    )

    await upsertDmfSetting("trigger_size_bytes", "4294967296")

    expect(global.fetch).toHaveBeenCalledOnce()
    const [url, options] = vi.mocked(global.fetch).mock.calls[0] as [
      string,
      RequestInit,
    ]
    expect(url).toMatch(/\/settings\/dmf-settings\/trigger_size_bytes$/)
    expect(options.method).toBe("PUT")
    expect(JSON.parse(options.body as string)).toEqual({ value: "4294967296" })
  })

  it("includes the Bearer token in the Authorization header", async () => {
    vi.mocked(global.fetch).mockResolvedValueOnce(
      new Response(null, { status: 200 })
    )

    await upsertDmfSetting("trigger_size_bytes", "1024")

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
      upsertDmfSetting("trigger_size_bytes", "1024")
    ).rejects.toThrow("HTTP 500")
  })
})
