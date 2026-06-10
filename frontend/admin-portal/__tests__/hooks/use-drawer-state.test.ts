// SPDX-License-Identifier: EUPL-1.2
// Copyright (C) 2026 Gemeente Utrecht

import { act, renderHook } from "@testing-library/react"
import { describe, expect, it } from "vitest"
import { useDrawerState } from "@/hooks/use-drawer-state"
import { ValidationError } from "@/lib/errors"

describe("useDrawerState", () => {
  it("starts closed with no item", () => {
    const { result } = renderHook(() => useDrawerState())
    expect(result.current.open).toBe(false)
    expect(result.current.item).toBeNull()
    expect(result.current.readOnly).toBe(false)
    expect(result.current.error).toBeNull()
    expect(result.current.dirty).toBe(false)
  })

  describe("openAdd", () => {
    it("opens the drawer in write mode with no item", () => {
      const { result } = renderHook(() => useDrawerState())
      act(() => result.current.openAdd())
      expect(result.current.open).toBe(true)
      expect(result.current.item).toBeNull()
      expect(result.current.readOnly).toBe(false)
    })

    it("clears error and dirty state from a previous session", async () => {
      const { result } = renderHook(() => useDrawerState())
      act(() => result.current.openAdd())
      await act(async () =>
        result.current.save(() => Promise.reject(new Error("fout")))
      )
      act(() => result.current.setDirty(true))

      act(() => result.current.openAdd())

      expect(result.current.error).toBeNull()
      expect(result.current.dirty).toBe(false)
    })
  })

  describe("openEdit", () => {
    it("opens with the item in write mode by default", () => {
      const { result } = renderHook(() => useDrawerState<{ id: string }>())
      act(() => result.current.openEdit({ id: "1" }))
      expect(result.current.open).toBe(true)
      expect(result.current.item).toEqual({ id: "1" })
      expect(result.current.readOnly).toBe(false)
    })

    it("opens in read-only mode when isReadOnly is true", () => {
      const { result } = renderHook(() => useDrawerState<{ id: string }>())
      act(() => result.current.openEdit({ id: "1" }, true))
      expect(result.current.readOnly).toBe(true)
    })

    it("clears error and dirty state from a previous session", async () => {
      const { result } = renderHook(() => useDrawerState<{ id: string }>())
      act(() => result.current.openEdit({ id: "1" }))
      await act(async () =>
        result.current.save(() => Promise.reject(new Error("fout")))
      )
      act(() => result.current.setDirty(true))

      act(() => result.current.openEdit({ id: "2" }))

      expect(result.current.error).toBeNull()
      expect(result.current.dirty).toBe(false)
    })
  })

  describe("handleCloseAttempt", () => {
    it("closes the drawer directly when not dirty", () => {
      const { result } = renderHook(() => useDrawerState())
      act(() => result.current.openAdd())
      act(() => result.current.handleCloseAttempt())
      expect(result.current.open).toBe(false)
      expect(result.current.closeConfirmOpen).toBe(false)
    })

    it("opens the confirm dialog when dirty", () => {
      const { result } = renderHook(() => useDrawerState())
      act(() => result.current.openAdd())
      act(() => result.current.setDirty(true))
      act(() => result.current.handleCloseAttempt())
      expect(result.current.open).toBe(true)
      expect(result.current.closeConfirmOpen).toBe(true)
    })
  })

  describe("dismissCloseConfirm", () => {
    it("closes the confirm dialog but keeps the drawer open", () => {
      const { result } = renderHook(() => useDrawerState())
      act(() => result.current.openAdd())
      act(() => result.current.setDirty(true))
      act(() => result.current.handleCloseAttempt())
      act(() => result.current.dismissCloseConfirm())
      expect(result.current.open).toBe(true)
      expect(result.current.closeConfirmOpen).toBe(false)
    })
  })

  describe("confirmClose", () => {
    it("closes the drawer, clears dirty, and closes the confirm dialog", () => {
      const { result } = renderHook(() => useDrawerState())
      act(() => result.current.openAdd())
      act(() => result.current.setDirty(true))
      act(() => result.current.handleCloseAttempt())
      act(() => result.current.confirmClose())
      expect(result.current.open).toBe(false)
      expect(result.current.dirty).toBe(false)
      expect(result.current.closeConfirmOpen).toBe(false)
    })
  })

  describe("save", () => {
    it("closes the drawer and clears error on success", async () => {
      const { result } = renderHook(() => useDrawerState())
      act(() => result.current.openAdd())
      await act(async () => result.current.save(() => Promise.resolve()))
      expect(result.current.open).toBe(false)
      expect(result.current.error).toBeNull()
    })

    it("sets error and keeps drawer open on failure", async () => {
      const { result } = renderHook(() => useDrawerState())
      act(() => result.current.openAdd())
      await act(async () =>
        result.current.save(() => Promise.reject(new Error("Server fout")))
      )
      expect(result.current.open).toBe(true)
      expect(result.current.error).toBe("Server fout")
    })

    it("shows the ValidationError message as a field error", async () => {
      const { result } = renderHook(() => useDrawerState())
      act(() => result.current.openAdd())
      await act(async () =>
        result.current.save(() =>
          Promise.reject(new ValidationError("Veld is verplicht."))
        )
      )
      expect(result.current.open).toBe(true)
      expect(result.current.error).toBe("Veld is verplicht.")
    })

    it("falls back to a generic message for non-Error rejections", async () => {
      const { result } = renderHook(() => useDrawerState())
      act(() => result.current.openAdd())
      await act(async () =>
        result.current.save(() => Promise.reject("onbekend"))
      )
      expect(result.current.error).toBe("Opslaan mislukt. Probeer het opnieuw.")
    })

    it("clears a previous error before retrying", async () => {
      const { result } = renderHook(() => useDrawerState())
      act(() => result.current.openAdd())
      await act(async () =>
        result.current.save(() => Promise.reject(new Error("eerste fout")))
      )
      expect(result.current.error).toBe("eerste fout")

      await act(async () => result.current.save(() => Promise.resolve()))
      expect(result.current.error).toBeNull()
    })
  })
})