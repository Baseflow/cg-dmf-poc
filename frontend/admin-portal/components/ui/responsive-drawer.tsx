// SPDX-License-Identifier: EUPL-1.2
// Copyright (C) 2026 Gemeente Utrecht

"use client"

import { Drawer, DrawerContent } from "@/components/ui/drawer"
import { useIsMobile } from "@/hooks/use-mobile"
import type { ReactNode } from "react"

export function ResponsiveDrawer({
  open,
  onOpenChange,
  children,
}: {
  open: boolean
  onOpenChange: (open: boolean) => void
  children: ReactNode
}) {
  const isMobile = useIsMobile()
  const direction = isMobile ? "bottom" : "right"

  return (
    <Drawer
      key={direction}
      open={open}
      onOpenChange={onOpenChange}
      direction={direction}
    >
      <DrawerContent>{children}</DrawerContent>
    </Drawer>
  )
}
