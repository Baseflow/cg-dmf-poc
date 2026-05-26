import { afterEach, beforeEach, describe, expect, it, vi } from "vitest"
import {
  createZgwApiSetting,
  deleteZgwApiSetting,
  deleteZgwApiSettings,
  updateZgwApiSetting,
} from "@/app/(main)/instellingen/zgw-api/actions"

vi.mock("@/auth", () => ({
  auth: vi.fn().mockResolvedValue({ accessToken: "test-token" }),
}))

vi.mock("next/cache", () => ({
  revalidatePath: vi.fn(),
}))

const originalFetch = global.fetch

describe("ZGW API setting actions", () => {
  beforeEach(() => {
    global.fetch = vi.fn()
  })

  afterEach(() => {
    global.fetch = originalFetch
    vi.clearAllMocks()
  })

  describe("createZgwApiSetting", () => {
    it("sends a POST request to /settings/zgw-api-settings", async () => {
      vi.mocked(global.fetch).mockResolvedValueOnce(new Response(null, { status: 201 }))
      await createZgwApiSetting({ name: "ZGW", baseUrl: "https://api.example", clientId: "c1" })
      const [url, options] = vi.mocked(global.fetch).mock.calls[0] as [string, RequestInit]
      expect(url).toMatch(/\/settings\/zgw-api-settings$/)
      expect(options.method).toBe("POST")
      expect(JSON.parse(options.body as string)).toEqual({
        name: "ZGW",
        baseUrl: "https://api.example",
        clientId: "c1",
      })
    })

    it("includes clientSecret when provided", async () => {
      vi.mocked(global.fetch).mockResolvedValueOnce(new Response(null, { status: 201 }))
      await createZgwApiSetting({
        name: "Z",
        baseUrl: "https://api.example",
        clientId: "c",
        clientSecret: "s3cr3t",
      })
      const [, options] = vi.mocked(global.fetch).mock.calls[0] as [string, RequestInit]
      expect(JSON.parse(options.body as string).clientSecret).toBe("s3cr3t")
    })

    it("includes Bearer token in the Authorization header", async () => {
      vi.mocked(global.fetch).mockResolvedValueOnce(new Response(null, { status: 201 }))
      await createZgwApiSetting({ name: "Z", baseUrl: "https://api.example", clientId: "c" })
      const [, options] = vi.mocked(global.fetch).mock.calls[0] as [string, RequestInit]
      expect((options.headers as Record<string, string>)["Authorization"]).toBe("Bearer test-token")
    })

    it("throws on non-ok response", async () => {
      vi.mocked(global.fetch).mockResolvedValueOnce(new Response(null, { status: 500 }))
      await expect(
        createZgwApiSetting({ name: "Z", baseUrl: "https://x", clientId: "c" })
      ).rejects.toThrow("HTTP 500")
    })
  })

  describe("updateZgwApiSetting", () => {
    it("sends a PUT request to /settings/zgw-api-settings/:id", async () => {
      vi.mocked(global.fetch).mockResolvedValueOnce(new Response(null, { status: 200 }))
      await updateZgwApiSetting("zgw-1", {
        name: "Updated ZGW",
        baseUrl: "https://api.example",
        clientId: "c2",
      })
      const [url, options] = vi.mocked(global.fetch).mock.calls[0] as [string, RequestInit]
      expect(url).toMatch(/\/settings\/zgw-api-settings\/zgw-1$/)
      expect(options.method).toBe("PUT")
      expect(JSON.parse(options.body as string).name).toBe("Updated ZGW")
    })

    it("throws on non-ok response", async () => {
      vi.mocked(global.fetch).mockResolvedValueOnce(new Response(null, { status: 404 }))
      await expect(
        updateZgwApiSetting("id", { name: "X", baseUrl: "https://x", clientId: "c" })
      ).rejects.toThrow("HTTP 404")
    })
  })

  describe("deleteZgwApiSetting", () => {
    it("sends a DELETE request to /settings/zgw-api-settings/:id", async () => {
      vi.mocked(global.fetch).mockResolvedValueOnce(new Response(null, { status: 200 }))
      await deleteZgwApiSetting("zgw-1")
      const [url, options] = vi.mocked(global.fetch).mock.calls[0] as [string, RequestInit]
      expect(url).toMatch(/\/settings\/zgw-api-settings\/zgw-1$/)
      expect(options.method).toBe("DELETE")
    })

    it("throws on non-ok response", async () => {
      vi.mocked(global.fetch).mockResolvedValueOnce(new Response(null, { status: 403 }))
      await expect(deleteZgwApiSetting("id")).rejects.toThrow("HTTP 403")
    })
  })

  describe("deleteZgwApiSettings", () => {
    it("sends a DELETE request for each provided id", async () => {
      vi.mocked(global.fetch).mockResolvedValue(new Response(null, { status: 200 }))
      await deleteZgwApiSettings(["z-1", "z-2"])
      expect(global.fetch).toHaveBeenCalledTimes(2)
      const urls = vi.mocked(global.fetch).mock.calls.map(([url]) => url as string)
      expect(urls).toEqual(
        expect.arrayContaining([
          expect.stringContaining("/settings/zgw-api-settings/z-1"),
          expect.stringContaining("/settings/zgw-api-settings/z-2"),
        ])
      )
    })

    it("throws when any DELETE fails", async () => {
      vi.mocked(global.fetch)
        .mockResolvedValueOnce(new Response(null, { status: 200 }))
        .mockResolvedValueOnce(new Response(null, { status: 500 }))
      await expect(deleteZgwApiSettings(["z-1", "z-2"])).rejects.toThrow("HTTP 500")
    })
  })
})
