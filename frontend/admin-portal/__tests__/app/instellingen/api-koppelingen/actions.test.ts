import { afterEach, beforeEach, describe, expect, it, vi } from "vitest"
import {
  createApiKoppeling,
  deleteApiKoppeling,
  deleteApiKoppelingen,
  updateApiKoppeling,
} from "@/app/(main)/instellingen/api-koppelingen/actions"
import { revalidatePath } from "next/cache"

vi.mock("@/auth", () => ({
  auth: vi.fn().mockResolvedValue({ accessToken: "test-token" }),
}))

vi.mock("next/cache", () => ({
  revalidatePath: vi.fn(),
}))

const originalFetch = global.fetch

const baseInput = {
  name: "OpenZaak",
  baseUrl: "https://api.example.com",
  clientId: "client-1",
  apiType: "ztc",
  authType: "zgw-auth",
  validationEnabled: true,
  enabled: true,
}

describe("API koppeling actions", () => {
  beforeEach(() => {
    global.fetch = vi.fn()
  })

  afterEach(() => {
    global.fetch = originalFetch
    vi.clearAllMocks()
  })

  describe("createApiKoppeling", () => {
    it("sends a POST request to /settings/api-connection-settings", async () => {
      vi.mocked(global.fetch).mockResolvedValueOnce(
        new Response(null, { status: 201 })
      )
      await createApiKoppeling(baseInput)
      const [url, options] = vi.mocked(global.fetch).mock.calls[0] as [
        string,
        RequestInit,
      ]
      expect(url).toMatch(/\/settings\/api-connection-settings$/)
      expect(options.method).toBe("POST")
      const body = JSON.parse(options.body as string)
      expect(body.name).toBe("OpenZaak")
      expect(body.apiType).toBe("ztc")
      expect(body.validationEnabled).toBe(true)
      expect(body.enabled).toBe(true)
    })

    it("includes clientSecret when provided", async () => {
      vi.mocked(global.fetch).mockResolvedValueOnce(
        new Response(null, { status: 201 })
      )
      await createApiKoppeling({ ...baseInput, clientSecret: "s3cr3t" })
      const [, options] = vi.mocked(global.fetch).mock.calls[0] as [
        string,
        RequestInit,
      ]
      expect(JSON.parse(options.body as string).clientSecret).toBe("s3cr3t")
    })

    it("includes Bearer token in the Authorization header", async () => {
      vi.mocked(global.fetch).mockResolvedValueOnce(
        new Response(null, { status: 201 })
      )
      await createApiKoppeling(baseInput)
      const [, options] = vi.mocked(global.fetch).mock.calls[0] as [
        string,
        RequestInit,
      ]
      expect(
        (options.headers as Record<string, string>)["Authorization"]
      ).toBe("Bearer test-token")
    })

    it("throws on non-ok response", async () => {
      vi.mocked(global.fetch).mockResolvedValueOnce(
        new Response(null, { status: 500 })
      )
      await expect(createApiKoppeling(baseInput)).rejects.toThrow("HTTP 500")
    })
  })

  describe("updateApiKoppeling", () => {
    it("sends a PUT request to /settings/api-connection-settings/:id", async () => {
      vi.mocked(global.fetch).mockResolvedValueOnce(
        new Response(null, { status: 200 })
      )
      await updateApiKoppeling("conn-1", { ...baseInput, name: "Updated" })
      const [url, options] = vi.mocked(global.fetch).mock.calls[0] as [
        string,
        RequestInit,
      ]
      expect(url).toMatch(/\/settings\/api-connection-settings\/conn-1$/)
      expect(options.method).toBe("PUT")
      expect(JSON.parse(options.body as string).name).toBe("Updated")
    })

    it("throws on non-ok response", async () => {
      vi.mocked(global.fetch).mockResolvedValueOnce(
        new Response(null, { status: 403 })
      )
      await expect(
        updateApiKoppeling("id", baseInput)
      ).rejects.toThrow("HTTP 403")
    })
  })

  describe("deleteApiKoppeling", () => {
    it("sends a DELETE request to /settings/api-connection-settings/:id", async () => {
      vi.mocked(global.fetch).mockResolvedValueOnce(
        new Response(null, { status: 204 })
      )
      await deleteApiKoppeling("conn-1")
      const [url, options] = vi.mocked(global.fetch).mock.calls[0] as [
        string,
        RequestInit,
      ]
      expect(url).toMatch(/\/settings\/api-connection-settings\/conn-1$/)
      expect(options.method).toBe("DELETE")
    })

    it("throws on non-ok response", async () => {
      vi.mocked(global.fetch).mockResolvedValueOnce(
        new Response(null, { status: 404 })
      )
      await expect(deleteApiKoppeling("id")).rejects.toThrow("HTTP 404")
    })
  })

  describe("deleteApiKoppelingen", () => {
    it("sends a DELETE request for each provided id", async () => {
      vi.mocked(global.fetch).mockResolvedValue(
        new Response(null, { status: 204 })
      )
      await deleteApiKoppelingen(["c-1", "c-2"])
      expect(global.fetch).toHaveBeenCalledTimes(2)
      const urls = vi
        .mocked(global.fetch)
        .mock.calls.map(([url]) => url as string)
      expect(urls).toEqual(
        expect.arrayContaining([
          expect.stringContaining("/settings/api-connection-settings/c-1"),
          expect.stringContaining("/settings/api-connection-settings/c-2"),
        ])
      )
    })

    it("throws with count when any DELETE fails", async () => {
      vi.mocked(global.fetch)
        .mockResolvedValueOnce(new Response(null, { status: 204 }))
        .mockResolvedValueOnce(new Response(null, { status: 500 }))
      await expect(deleteApiKoppelingen(["c-1", "c-2"])).rejects.toThrow(
        "1 van 2 koppelingen konden niet worden verwijderd."
      )
    })

    it("calls revalidatePath even when some deletes fail", async () => {
      vi.mocked(global.fetch)
        .mockResolvedValueOnce(new Response(null, { status: 204 }))
        .mockResolvedValueOnce(new Response(null, { status: 500 }))
      await expect(deleteApiKoppelingen(["c-1", "c-2"])).rejects.toThrow()
      expect(vi.mocked(revalidatePath)).toHaveBeenCalled()
    })
  })
})
