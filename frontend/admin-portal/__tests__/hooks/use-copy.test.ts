import { act, renderHook } from "@testing-library/react"
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest"
import { useCopy } from "@/hooks/use-copy"

describe("useCopy", () => {
  beforeEach(() => {
    vi.useFakeTimers()
    Object.assign(navigator, {
      clipboard: { writeText: vi.fn().mockResolvedValue(undefined) },
    })
  })

  afterEach(() => {
    vi.restoreAllMocks()
    vi.useRealTimers()
  })

  it("starts with copied=false", () => {
    const { result } = renderHook(() => useCopy())
    expect(result.current.copied).toBe(false)
  })

  it("sets copied=true after copy()", async () => {
    const { result } = renderHook(() => useCopy())
    await act(() => result.current.copy("hello"))
    expect(result.current.copied).toBe(true)
    expect(navigator.clipboard.writeText).toHaveBeenCalledWith("hello")
  })

  it("resets copied to false after 2000ms", async () => {
    const { result } = renderHook(() => useCopy())
    await act(() => result.current.copy("hello"))
    expect(result.current.copied).toBe(true)
    act(() => vi.advanceTimersByTime(2000))
    expect(result.current.copied).toBe(false)
  })

  it("resets the timer when copy() is called again before the timeout", async () => {
    const { result } = renderHook(() => useCopy())
    await act(() => result.current.copy("first"))
    act(() => vi.advanceTimersByTime(1000))
    await act(() => result.current.copy("second"))
    act(() => vi.advanceTimersByTime(1999))
    expect(result.current.copied).toBe(true)
    act(() => vi.advanceTimersByTime(1))
    expect(result.current.copied).toBe(false)
  })

  it("does not set copied when clipboard.writeText rejects", async () => {
    vi.mocked(navigator.clipboard.writeText).mockRejectedValue(
      new Error("denied")
    )
    const { result } = renderHook(() => useCopy())
    await act(() => result.current.copy("hello"))
    expect(result.current.copied).toBe(false)
  })

  it("clears pending timer on unmount without setState error", async () => {
    const { result, unmount } = renderHook(() => useCopy())
    await act(() => result.current.copy("hello"))
    unmount()
    act(() => vi.advanceTimersByTime(2000))
  })
})
