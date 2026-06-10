// SPDX-License-Identifier: EUPL-1.2
// Copyright (C) 2026 Gemeente Utrecht

import { useCallback, useState, useTransition } from "react"

export function useDeleteState<T>() {
  const [deleteTarget, setDeleteTarget] = useState<T | null>(null)
  const [bulkDeleteIds, setBulkDeleteIds] = useState<string[]>([])
  const [bulkDeleteOpen, setBulkDeleteOpen] = useState(false)
  const [isDeleting, startDelete] = useTransition()
  const [deleteError, setDeleteError] = useState<string | null>(null)

  const deleteOne = useCallback(
    (action: () => Promise<void>) => {
      setDeleteError(null)
      startDelete(async () => {
        try {
          await action()
          setDeleteTarget(null)
        } catch (e) {
          setDeleteError(
            e instanceof Error
              ? e.message
              : "Verwijderen mislukt. Probeer het opnieuw."
          )
        }
      })
    },
    [startDelete]
  )

  const deleteBulk = useCallback(
    (action: () => Promise<void>) => {
      setDeleteError(null)
      startDelete(async () => {
        try {
          await action()
          setBulkDeleteOpen(false)
        } catch (e) {
          setDeleteError(
            e instanceof Error
              ? e.message
              : "Verwijderen mislukt. Probeer het opnieuw."
          )
        }
      })
    },
    [startDelete]
  )

  return {
    deleteTarget,
    setDeleteTarget,
    bulkDeleteIds,
    setBulkDeleteIds,
    bulkDeleteOpen,
    setBulkDeleteOpen,
    isDeleting,
    deleteError,
    setDeleteError,
    deleteOne,
    deleteBulk,
  }
}
