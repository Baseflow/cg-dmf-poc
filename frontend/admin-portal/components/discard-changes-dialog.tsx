// SPDX-License-Identifier: EUPL-1.2
// Copyright (C) 2026 Gemeente Utrecht

"use client"

import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
} from "@/components/ui/alert-dialog"

export function DiscardChangesDialog({
  open,
  onDismiss,
  onConfirm,
}: {
  open: boolean
  onDismiss: () => void
  onConfirm: () => void
}) {
  return (
    <AlertDialog open={open} onOpenChange={(open) => !open && onDismiss()}>
      <AlertDialogContent>
        <AlertDialogHeader>
          <AlertDialogTitle>Wijzigingen verlaten?</AlertDialogTitle>
          <AlertDialogDescription>
            Je hebt niet-opgeslagen wijzigingen. Weet je zeker dat je wilt
            sluiten?
          </AlertDialogDescription>
        </AlertDialogHeader>
        <AlertDialogFooter>
          <AlertDialogCancel>Terug</AlertDialogCancel>
          <AlertDialogAction variant="destructive" onClick={onConfirm}>
            Sluiten
          </AlertDialogAction>
        </AlertDialogFooter>
      </AlertDialogContent>
    </AlertDialog>
  )
}
