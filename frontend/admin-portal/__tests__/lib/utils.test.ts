import { describe, expect, it } from "vitest"
import { cn } from "@/lib/utils"

describe("cn", () => {
  it("returns a single class unchanged", () => {
    expect(cn("foo")).toBe("foo")
  })

  it("merges multiple classes", () => {
    expect(cn("foo", "bar")).toBe("foo bar")
  })

  it("ignores falsy values", () => {
    expect(cn("foo", false, undefined, null, "bar")).toBe("foo bar")
  })

  it("resolves tailwind conflicts, keeping the last value", () => {
    expect(cn("p-2", "p-4")).toBe("p-4")
  })

  it("handles conditional classes via objects", () => {
    expect(cn({ "text-red-500": true, "text-blue-500": false })).toBe(
      "text-red-500"
    )
  })

  it("handles arrays of classes", () => {
    expect(cn(["a", "b"], "c")).toBe("a b c")
  })

  it("returns empty string when given no classes", () => {
    expect(cn()).toBe("")
  })
})
