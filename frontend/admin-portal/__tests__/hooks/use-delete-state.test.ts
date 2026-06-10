// SPDX-License-Identifier: EUPL-1.2
// Copyright (C) 2026 Gemeente Utrecht

import { act, renderHook } from "@testing-library/react"
import { describe, expect, it } from "vitest"
import { useDeleteState } from "@/hooks/use-delete-state"

describe("useDeleteState", () => {
  it("starts with no target, no bulk dialog, no error", () => {
    const { result } = renderHook(() => useDeleteState())
    expect(result.current.deleteTarget).toBeNull()
    expect(result.current.bulkDeleteOpen).toBe(false)
    expect(result.current.deleteError).toBeNull()
  })

  describe("deleteOne", () => {
    it("clears deleteTarget on success", async () => {
      const { result } = renderHook(() => useDeleteState<{ id: string }>())
      act(() => result.current.setDeleteTarget({ id: "1" }))
      await act(async () => result.current.deleteOne(() => Promise.resolve()))
      expect(result.current.deleteTarget).toBeNull()
      expect(result.current.deleteError).toBeNull()
    })

    it("sets deleteError and keeps target on failure", async () => {
      const { result } = renderHook(() => useDeleteState<{ id: string }>())
      act(() => result.current.setDeleteTarget({ id: "1" }))
      await act(async () =>
        result.current.deleteOne(() =>
          Promise.reject(new Error("Kan niet verwijderen"))
        )
      )
      expect(result.current.deleteTarget).not.toBeNull()
      expect(result.current.deleteError).toBe("Kan niet verwijderen")
    })

    it("falls back to a generic message for non-Error rejections", async () => {
      const { result } = renderHook(() => useDeleteState<{ id: string }>())
      act(() => result.current.setDeleteTarget({ id: "1" }))
      await act(async () =>
        result.current.deleteOne(() => Promise.reject("onbekend"))
      )
      expect(result.current.deleteError).toBe(
        "Verwijderen mislukt. Probeer het opnieuw."
      )
    })

    it("clears a previous error before retrying", async () => {
      const { result } = renderHook(() => useDeleteState<{ id: string }>())
      act(() => result.current.setDeleteTarget({ id: "1" }))
      await act(async () =>
        result.current.deleteOne(() => Promise.reject(new Error("eerste fout")))
      )
      expect(result.current.deleteError).toBe("eerste fout")

      await act(async () => result.current.deleteOne(() => Promise.resolve()))
      expect(result.current.deleteError).toBeNull()
    })
  })

  describe("deleteBulk", () => {
    it("closes the bulk dialog on success", async () => {
      const { result } = renderHook(() => useDeleteState())
      act(() => result.current.setBulkDeleteOpen(true))
      await act(async () => result.current.deleteBulk(() => Promise.resolve()))
      expect(result.current.bulkDeleteOpen).toBe(false)
      expect(result.current.deleteError).toBeNull()
    })

    it("sets deleteError and keeps dialog open on failure", async () => {
      const { result } = renderHook(() => useDeleteState())
      act(() => result.current.setBulkDeleteOpen(true))
      await act(async () =>
        result.current.deleteBulk(() =>
          Promise.reject(new Error("Bulk fout"))
        )
      )
      expect(result.current.bulkDeleteOpen).toBe(true)
      expect(result.current.deleteError).toBe("Bulk fout")
    })

    it("falls back to a generic message for non-Error rejections", async () => {
      const { result } = renderHook(() => useDeleteState())
      act(() => result.current.setBulkDeleteOpen(true))
      await act(async () =>
        result.current.deleteBulk(() => Promise.reject("onbekend"))
      )
      expect(result.current.deleteError).toBe(
        "Verwijderen mislukt. Probeer het opnieuw."
      )
    })
  })
})