import { afterEach, beforeEach, describe, expect, it, vi } from "vitest"
import { saveDmfSettings } from "@/app/(main)/instellingen/dmf/actions"

vi.mock("@/auth", () => ({
  auth: vi.fn().mockResolvedValue({ accessToken: "test-token" }),
}))

vi.mock("next/cache", () => ({
  revalidatePath: vi.fn(),
}))

const originalFetch = global.fetch

function makeFormData(values: {
  triggerSize: string
  chunkSize: string
  validationEnabled?: "on"
}) {
  const fd = new FormData()
  fd.append("triggerSize", values.triggerSize)
  fd.append("chunkSize", values.chunkSize)
  if (values.validationEnabled) fd.append("validationEnabled", values.validationEnabled)
  return fd
}

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

    await saveDmfSettings(
      {},
      makeFormData({ triggerSize: "1024", chunkSize: "512", validationEnabled: "on" })
    )

    expect(global.fetch).toHaveBeenCalledOnce()
    const [url, options] = vi.mocked(global.fetch).mock.calls[0] as [
      string,
      RequestInit,
    ]
    expect(url).toMatch(/\/settings\/dmf-settings$/)
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

    await saveDmfSettings(
      {},
      makeFormData({ triggerSize: "1024", chunkSize: "512" })
    )

    const [, options] = vi.mocked(global.fetch).mock.calls[0] as [
      string,
      RequestInit,
    ]
    const headers = options.headers as Record<string, string>
    expect(headers["Authorization"]).toBe("Bearer test-token")
  })

  it("returns field errors without calling fetch when validation fails", async () => {
    const state = await saveDmfSettings(
      {},
      makeFormData({ triggerSize: "0", chunkSize: "512" })
    )
    expect(state.errors?.triggerSize).toBeTruthy()
    expect(global.fetch).not.toHaveBeenCalled()
  })

  it("returns error state when the response is not ok", async () => {
    vi.mocked(global.fetch).mockResolvedValueOnce(
      new Response(null, { status: 500 })
    )

    const state = await saveDmfSettings(
      {},
      makeFormData({ triggerSize: "1", chunkSize: "1" })
    )
    expect(state.error).toBeTruthy()
  })

  it("returns saved: true on success", async () => {
    vi.mocked(global.fetch).mockResolvedValueOnce(
      new Response(null, { status: 200 })
    )

    const state = await saveDmfSettings(
      {},
      makeFormData({ triggerSize: "1024", chunkSize: "512" })
    )
    expect(state.saved).toBe(true)
  })
})
