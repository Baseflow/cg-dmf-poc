import { afterEach, beforeEach, describe, expect, it, vi } from "vitest"
import {
  createRepository,
  deleteRepositories,
  deleteRepository,
  updateRepository,
} from "@/app/(main)/instellingen/repositories/actions"

vi.mock("@/auth", () => ({
  auth: vi.fn().mockResolvedValue({ accessToken: "test-token" }),
}))

vi.mock("next/cache", () => ({
  revalidatePath: vi.fn(),
}))

const originalFetch = global.fetch

const baseInput = {
  name: "My Repo",
  storageType: "S3" as const,
  url: "https://s3.example.com",
  bucket: "my-bucket",
  isDefault: false,
  enabled: true,
}

describe("repository actions", () => {
  beforeEach(() => {
    global.fetch = vi.fn()
  })

  afterEach(() => {
    global.fetch = originalFetch
    vi.clearAllMocks()
  })

  describe("createRepository", () => {
    it("sends a POST request to /settings/storage-repositories", async () => {
      vi.mocked(global.fetch).mockResolvedValueOnce(new Response(null, { status: 201 }))
      await createRepository(baseInput)
      const [url, options] = vi.mocked(global.fetch).mock.calls[0] as [string, RequestInit]
      expect(url).toMatch(/\/admin\/storage-repositories$/)
      expect(options.method).toBe("POST")
      expect(JSON.parse(options.body as string)).toMatchObject(baseInput)
    })

    it("includes optional accessKey and secretKey when provided", async () => {
      vi.mocked(global.fetch).mockResolvedValueOnce(new Response(null, { status: 201 }))
      await createRepository({ ...baseInput, accessKey: "ak", secretKey: "sk" })
      const [, options] = vi.mocked(global.fetch).mock.calls[0] as [string, RequestInit]
      const body = JSON.parse(options.body as string)
      expect(body.accessKey).toBe("ak")
      expect(body.secretKey).toBe("sk")
    })

    it("includes Bearer token in the Authorization header", async () => {
      vi.mocked(global.fetch).mockResolvedValueOnce(new Response(null, { status: 201 }))
      await createRepository(baseInput)
      const [, options] = vi.mocked(global.fetch).mock.calls[0] as [string, RequestInit]
      expect((options.headers as Record<string, string>)["Authorization"]).toBe("Bearer test-token")
    })

    it("throws on non-ok response", async () => {
      vi.mocked(global.fetch).mockResolvedValueOnce(new Response(null, { status: 500 }))
      await expect(createRepository(baseInput)).rejects.toThrow("HTTP 500")
    })
  })

  describe("updateRepository", () => {
    it("sends a PUT request to /settings/storage-repositories/:id", async () => {
      vi.mocked(global.fetch).mockResolvedValueOnce(new Response(null, { status: 200 }))
      await updateRepository("repo-1", { ...baseInput, name: "Updated Repo" })
      const [url, options] = vi.mocked(global.fetch).mock.calls[0] as [string, RequestInit]
      expect(url).toMatch(/\/admin\/storage-repositories\/repo-1$/)
      expect(options.method).toBe("PUT")
      expect(JSON.parse(options.body as string).name).toBe("Updated Repo")
    })

    it("throws on non-ok response", async () => {
      vi.mocked(global.fetch).mockResolvedValueOnce(new Response(null, { status: 400 }))
      await expect(updateRepository("id", baseInput)).rejects.toThrow("HTTP 400")
    })
  })

  describe("deleteRepository", () => {
    it("sends a DELETE request to /settings/storage-repositories/:id", async () => {
      vi.mocked(global.fetch).mockResolvedValueOnce(new Response(null, { status: 200 }))
      await deleteRepository("repo-1")
      const [url, options] = vi.mocked(global.fetch).mock.calls[0] as [string, RequestInit]
      expect(url).toMatch(/\/admin\/storage-repositories\/repo-1$/)
      expect(options.method).toBe("DELETE")
    })

    it("throws on non-ok response", async () => {
      vi.mocked(global.fetch).mockResolvedValueOnce(new Response(null, { status: 404 }))
      await expect(deleteRepository("id")).rejects.toThrow("HTTP 404")
    })
  })

  describe("deleteRepositories", () => {
    it("sends a DELETE request for each provided id", async () => {
      vi.mocked(global.fetch).mockResolvedValue(new Response(null, { status: 200 }))
      await deleteRepositories(["r-1", "r-2", "r-3"])
      expect(global.fetch).toHaveBeenCalledTimes(3)
      const urls = vi.mocked(global.fetch).mock.calls.map(([url]) => url as string)
      expect(urls).toEqual(
        expect.arrayContaining([
          expect.stringContaining("/settings/storage-repositories/r-1"),
          expect.stringContaining("/settings/storage-repositories/r-2"),
          expect.stringContaining("/settings/storage-repositories/r-3"),
        ])
      )
    })

    it("throws when any DELETE fails", async () => {
      vi.mocked(global.fetch)
        .mockResolvedValueOnce(new Response(null, { status: 200 }))
        .mockResolvedValueOnce(new Response(null, { status: 500 }))
      await expect(deleteRepositories(["r-1", "r-2"])).rejects.toThrow("HTTP 500")
    })
  })
})
