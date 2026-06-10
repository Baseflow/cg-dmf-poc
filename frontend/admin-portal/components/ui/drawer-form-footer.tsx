// SPDX-License-Identifier: EUPL-1.2
// Copyright (C) 2026 Gemeente Utrecht

"use client"

import { Button } from "@/components/ui/button"
import { DrawerFooter } from "@/components/ui/drawer"
import { Check, X } from "lucide-react"

export function DrawerFormFooter({
  readOnly,
  saving,
  formId,
  onCancel,
}: {
  readOnly: boolean
  saving: boolean
  formId: string
  onCancel: () => void
}) {
  if (readOnly) {
    return (
      <DrawerFooter>
        <Button type="button" variant="outline" size="sm" onClick={onCancel}>
          Sluiten
        </Button>
      </DrawerFooter>
    )
  }

  return (
    <DrawerFooter>
      <Button type="submit" form={formId} size="sm" disabled={saving}>
        <Check />
        {saving ? "Opslaan..." : "Opslaan"}
      </Button>
      <Button
        type="button"
        variant="outline"
        size="sm"
        onClick={onCancel}
        disabled={saving}
      >
        <X />
        Annuleren
      </Button>
    </DrawerFooter>
  )
}
