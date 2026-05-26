import { afterEach, beforeEach, describe, expect, it, vi } from "vitest"
import {
  createApplication,
  deleteApplication,
  deleteApplications,
  rotateApplicationSecret,
  updateApplication,
} from "@/app/(main)/instellingen/applicaties/actions"

vi.mock("@/auth", () => ({
  auth: vi.fn().mockResolvedValue({ accessToken: "test-token" }),
}))

vi.mock("next/cache", () => ({
  revalidatePath: vi.fn(),
}))

const originalFetch = global.fetch

describe("application actions", () => {
  beforeEach(() => {
    global.fetch = vi.fn()
  })

  afterEach(() => {
    global.fetch = originalFetch
    vi.clearAllMocks()
  })

  describe("createApplication", () => {
    it("sends a POST request to /settings/application-settings", async () => {
      vi.mocked(global.fetch).mockResolvedValueOnce(new Response(null, { status: 201 }))
      await createApplication({ name: "App", clientId: "client-1" })
      const [url, options] = vi.mocked(global.fetch).mock.calls[0] as [string, RequestInit]
      expect(url).toMatch(/\/settings\/application-settings$/)
      expect(options.method).toBe("POST")
      expect(JSON.parse(options.body as string)).toEqual({ name: "App", clientId: "client-1" })
    })

    it("includes clientSecret in the body when provided", async () => {
      vi.mocked(global.fetch).mockResolvedValueOnce(new Response(null, { status: 201 }))
      await createApplication({ name: "App", clientId: "c1", clientSecret: "secret" })
      const [, options] = vi.mocked(global.fetch).mock.calls[0] as [string, RequestInit]
      expect(JSON.parse(options.body as string).clientSecret).toBe("secret")
    })

    it("includes the Bearer token", async () => {
      vi.mocked(global.fetch).mockResolvedValueOnce(new Response(null, { status: 201 }))
      await createApplication({ name: "App", clientId: "c1" })
      const [, options] = vi.mocked(global.fetch).mock.calls[0] as [string, RequestInit]
      expect((options.headers as Record<string, string>)["Authorization"]).toBe("Bearer test-token")
    })

    it("throws on non-ok response", async () => {
      vi.mocked(global.fetch).mockResolvedValueOnce(new Response(null, { status: 500 }))
      await expect(createApplication({ name: "App", clientId: "c1" })).rejects.toThrow("HTTP 500")
    })
  })

  describe("updateApplication", () => {
    it("sends a PUT request to /settings/application-settings/:id", async () => {
      vi.mocked(global.fetch).mockResolvedValueOnce(new Response(null, { status: 200 }))
      await updateApplication("abc-123", { name: "Updated", clientId: "c2" })
      const [url, options] = vi.mocked(global.fetch).mock.calls[0] as [string, RequestInit]
      expect(url).toMatch(/\/settings\/application-settings\/abc-123$/)
      expect(options.method).toBe("PUT")
      expect(JSON.parse(options.body as string)).toEqual({ name: "Updated", clientId: "c2" })
    })

    it("throws on non-ok response", async () => {
      vi.mocked(global.fetch).mockResolvedValueOnce(new Response(null, { status: 404 }))
      await expect(updateApplication("id", { name: "X", clientId: "c" })).rejects.toThrow("HTTP 404")
    })
  })

  describe("deleteApplication", () => {
    it("sends a DELETE request to /settings/application-settings/:id", async () => {
      vi.mocked(global.fetch).mockResolvedValueOnce(new Response(null, { status: 200 }))
      await deleteApplication("abc-123")
      const [url, options] = vi.mocked(global.fetch).mock.calls[0] as [string, RequestInit]
      expect(url).toMatch(/\/settings\/application-settings\/abc-123$/)
      expect(options.method).toBe("DELETE")
    })

    it("throws on non-ok response", async () => {
      vi.mocked(global.fetch).mockResolvedValueOnce(new Response(null, { status: 404 }))
      await expect(deleteApplication("id")).rejects.toThrow("HTTP 404")
    })
  })

  describe("deleteApplications", () => {
    it("sends a DELETE request for each provided id", async () => {
      vi.mocked(global.fetch).mockResolvedValue(new Response(null, { status: 200 }))
      await deleteApplications(["id-1", "id-2", "id-3"])
      expect(global.fetch).toHaveBeenCalledTimes(3)
      const urls = vi.mocked(global.fetch).mock.calls.map(([url]) => url as string)
      expect(urls).toEqual(
        expect.arrayContaining([
          expect.stringContaining("/settings/application-settings/id-1"),
          expect.stringContaining("/settings/application-settings/id-2"),
          expect.stringContaining("/settings/application-settings/id-3"),
        ])
      )
    })

    it("throws when any DELETE fails", async () => {
      vi.mocked(global.fetch)
        .mockResolvedValueOnce(new Response(null, { status: 200 }))
        .mockResolvedValueOnce(new Response(null, { status: 500 }))
      await expect(deleteApplications(["id-1", "id-2"])).rejects.toThrow("HTTP 500")
    })
  })

  describe("rotateApplicationSecret", () => {
    it("sends a POST to /settings/application-settings/:id/rotate-secret and returns the secret", async () => {
      vi.mocked(global.fetch).mockResolvedValueOnce(
        new Response(JSON.stringify({ secret: "new-secret-value" }), { status: 200 })
      )
      const result = await rotateApplicationSecret("abc-123")
      const [url, options] = vi.mocked(global.fetch).mock.calls[0] as [string, RequestInit]
      expect(url).toMatch(/\/settings\/application-settings\/abc-123\/rotate-secret$/)
      expect(options.method).toBe("POST")
      expect(result).toBe("new-secret-value")
    })

    it("includes newSecret in the body when provided", async () => {
      vi.mocked(global.fetch).mockResolvedValueOnce(
        new Response(JSON.stringify({ secret: "custom" }), { status: 200 })
      )
      await rotateApplicationSecret("abc-123", "custom-secret")
      const [, options] = vi.mocked(global.fetch).mock.calls[0] as [string, RequestInit]
      expect(JSON.parse(options.body as string)).toEqual({ newSecret: "custom-secret" })
    })

    it("sends an empty body when newSecret is not provided", async () => {
      vi.mocked(global.fetch).mockResolvedValueOnce(
        new Response(JSON.stringify({ secret: "auto" }), { status: 200 })
      )
      await rotateApplicationSecret("abc-123")
      const [, options] = vi.mocked(global.fetch).mock.calls[0] as [string, RequestInit]
      expect(JSON.parse(options.body as string)).toEqual({})
    })

    it("throws on non-ok response", async () => {
      vi.mocked(global.fetch).mockResolvedValueOnce(new Response(null, { status: 500 }))
      await expect(rotateApplicationSecret("id")).rejects.toThrow("HTTP 500")
    })
  })
})
