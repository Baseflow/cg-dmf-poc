// SPDX-License-Identifier: EUPL-1.2
// Copyright (C) 2026 Gemeente Utrecht

import { useCallback, useState, useTransition } from "react"

export function useDrawerState<T>() {
  const [open, setOpen] = useState(false)
  const [item, setItem] = useState<T | null>(null)
  const [readOnly, setReadOnly] = useState(false)
  const [saving, startSave] = useTransition()
  const [error, setError] = useState<string | null>(null)
  const [dirty, setDirty] = useState(false)
  const [closeConfirmOpen, setCloseConfirmOpen] = useState(false)

  const handleCloseAttempt = useCallback(() => {
    if (saving) return
    if (dirty) {
      setCloseConfirmOpen(true)
    } else {
      setOpen(false)
    }
  }, [saving, dirty])

  const dismissCloseConfirm = useCallback(() => {
    setCloseConfirmOpen(false)
  }, [])

  const confirmClose = useCallback(() => {
    setCloseConfirmOpen(false)
    setOpen(false)
    setDirty(false)
  }, [])

  const openAdd = useCallback(() => {
    setItem(null)
    setReadOnly(false)
    setError(null)
    setDirty(false)
    setOpen(true)
  }, [])

  const openEdit = useCallback((newItem: T, isReadOnly = false) => {
    setItem(newItem)
    setReadOnly(isReadOnly)
    setError(null)
    setDirty(false)
    setOpen(true)
  }, [])

  const save = useCallback(
    (action: () => Promise<void>) => {
      setError(null)
      startSave(async () => {
        try {
          await action()
          setOpen(false)
        } catch (e) {
          setError(
            e instanceof Error
              ? e.message
              : "Opslaan mislukt. Probeer het opnieuw."
          )
        }
      })
    },
    [startSave]
  )

  return {
    open,
    setOpen,
    item,
    readOnly,
    saving,
    error,
    dirty,
    setDirty,
    closeConfirmOpen,
    dismissCloseConfirm,
    confirmClose,
    handleCloseAttempt,
    openAdd,
    openEdit,
    save,
  }
}
