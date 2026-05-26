import { act, renderHook } from "@testing-library/react"
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest"
import { useIsMobile } from "@/hooks/use-mobile"

function mockWindowInnerWidth(width: number) {
  Object.defineProperty(window, "innerWidth", {
    writable: true,
    configurable: true,
    value: width,
  })
}

describe("useIsMobile", () => {
  let listeners: Array<() => void> = []

  beforeEach(() => {
    listeners = []
    window.matchMedia = vi.fn().mockImplementation((query: string) => ({
      matches: false,
      media: query,
      addEventListener: (_: string, cb: () => void) => {
        listeners.push(cb)
      },
      removeEventListener: vi.fn(),
      dispatchEvent: vi.fn(),
    }))
  })

  afterEach(() => {
    vi.restoreAllMocks()
  })

  it("returns false when window.innerWidth >= 768", () => {
    mockWindowInnerWidth(1024)
    const { result } = renderHook(() => useIsMobile())
    expect(result.current).toBe(false)
  })

  it("returns true when window.innerWidth < 768", () => {
    mockWindowInnerWidth(375)
    const { result } = renderHook(() => useIsMobile())
    expect(result.current).toBe(true)
  })

  it("updates when the matchMedia change event fires", () => {
    mockWindowInnerWidth(1024)
    const { result } = renderHook(() => useIsMobile())
    expect(result.current).toBe(false)

    act(() => {
      mockWindowInnerWidth(375)
      listeners.forEach((cb) => cb())
    })

    expect(result.current).toBe(true)
  })

  it("returns false at exactly the breakpoint (768px)", () => {
    mockWindowInnerWidth(768)
    const { result } = renderHook(() => useIsMobile())
    expect(result.current).toBe(false)
  })
})
