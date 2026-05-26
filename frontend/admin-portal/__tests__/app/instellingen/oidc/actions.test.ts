import { afterEach, beforeEach, describe, expect, it, vi } from "vitest"
import {
  createOidcProvider,
  deleteOidcProvider,
  deleteOidcProviders,
  updateOidcProvider,
} from "@/app/(main)/instellingen/oidc/actions"

vi.mock("@/auth", () => ({
  auth: vi.fn().mockResolvedValue({ accessToken: "test-token" }),
}))

vi.mock("next/cache", () => ({
  revalidatePath: vi.fn(),
}))

const originalFetch = global.fetch

describe("OIDC provider actions", () => {
  beforeEach(() => {
    global.fetch = vi.fn()
  })

  afterEach(() => {
    global.fetch = originalFetch
    vi.clearAllMocks()
  })

  describe("createOidcProvider", () => {
    it("sends a POST request to /settings/oidc-providers", async () => {
      vi.mocked(global.fetch).mockResolvedValueOnce(new Response(null, { status: 201 }))
      await createOidcProvider({ name: "Provider", issuer: "https://issuer.example", clientId: "c1" })
      const [url, options] = vi.mocked(global.fetch).mock.calls[0] as [string, RequestInit]
      expect(url).toMatch(/\/settings\/oidc-providers$/)
      expect(options.method).toBe("POST")
      expect(JSON.parse(options.body as string)).toEqual({
        name: "Provider",
        issuer: "https://issuer.example",
        clientId: "c1",
      })
    })

    it("includes clientSecret when provided", async () => {
      vi.mocked(global.fetch).mockResolvedValueOnce(new Response(null, { status: 201 }))
      await createOidcProvider({
        name: "P",
        issuer: "https://i.example",
        clientId: "c",
        clientSecret: "s3cr3t",
      })
      const [, options] = vi.mocked(global.fetch).mock.calls[0] as [string, RequestInit]
      expect(JSON.parse(options.body as string).clientSecret).toBe("s3cr3t")
    })

    it("throws on non-ok response", async () => {
      vi.mocked(global.fetch).mockResolvedValueOnce(new Response(null, { status: 422 }))
      await expect(
        createOidcProvider({ name: "P", issuer: "https://i.example", clientId: "c" })
      ).rejects.toThrow("HTTP 422")
    })
  })

  describe("updateOidcProvider", () => {
    it("sends a PUT request to /settings/oidc-providers/:id", async () => {
      vi.mocked(global.fetch).mockResolvedValueOnce(new Response(null, { status: 200 }))
      await updateOidcProvider("provider-1", {
        name: "Updated",
        issuer: "https://new-issuer.example",
        clientId: "c2",
      })
      const [url, options] = vi.mocked(global.fetch).mock.calls[0] as [string, RequestInit]
      expect(url).toMatch(/\/settings\/oidc-providers\/provider-1$/)
      expect(options.method).toBe("PUT")
      expect(JSON.parse(options.body as string).name).toBe("Updated")
    })

    it("throws on non-ok response", async () => {
      vi.mocked(global.fetch).mockResolvedValueOnce(new Response(null, { status: 404 }))
      await expect(
        updateOidcProvider("id", { name: "X", issuer: "https://x.example", clientId: "c" })
      ).rejects.toThrow("HTTP 404")
    })
  })

  describe("deleteOidcProvider", () => {
    it("sends a DELETE request to /settings/oidc-providers/:id", async () => {
      vi.mocked(global.fetch).mockResolvedValueOnce(new Response(null, { status: 200 }))
      await deleteOidcProvider("provider-1")
      const [url, options] = vi.mocked(global.fetch).mock.calls[0] as [string, RequestInit]
      expect(url).toMatch(/\/settings\/oidc-providers\/provider-1$/)
      expect(options.method).toBe("DELETE")
    })

    it("throws on non-ok response", async () => {
      vi.mocked(global.fetch).mockResolvedValueOnce(new Response(null, { status: 403 }))
      await expect(deleteOidcProvider("id")).rejects.toThrow("HTTP 403")
    })
  })

  describe("deleteOidcProviders", () => {
    it("sends a DELETE request for each provided id", async () => {
      vi.mocked(global.fetch).mockResolvedValue(new Response(null, { status: 200 }))
      await deleteOidcProviders(["p-1", "p-2"])
      expect(global.fetch).toHaveBeenCalledTimes(2)
      const urls = vi.mocked(global.fetch).mock.calls.map(([url]) => url as string)
      expect(urls).toEqual(
        expect.arrayContaining([
          expect.stringContaining("/settings/oidc-providers/p-1"),
          expect.stringContaining("/settings/oidc-providers/p-2"),
        ])
      )
    })

    it("throws when any DELETE fails", async () => {
      vi.mocked(global.fetch)
        .mockResolvedValueOnce(new Response(null, { status: 200 }))
        .mockResolvedValueOnce(new Response(null, { status: 500 }))
      await expect(deleteOidcProviders(["p-1", "p-2"])).rejects.toThrow("HTTP 500")
    })
  })
})
