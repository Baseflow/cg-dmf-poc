"use client"

import { Button } from "@/components/ui/button"
import { Eye, EyeOff } from "lucide-react"
import { useState } from "react"

export function SecretCell({
  value,
  hasSecret,
}: {
  value: string | null
  hasSecret?: boolean
}) {
  const [revealed, setRevealed] = useState(false)

  if (!value) {
    return (
      <span className="text-xs text-muted-foreground">
        {hasSecret ? "Ingesteld" : "—"}
      </span>
    )
  }

  return (
    <div className="flex items-center gap-0.5">
      <span className="font-mono text-xs text-muted-foreground">
        {revealed ? value : "••••••••"}
      </span>
      <Button
        variant="ghost"
        size="icon"
        className="size-6 shrink-0 text-muted-foreground hover:text-foreground"
        onClick={() => setRevealed((v) => !v)}
        aria-label={revealed ? "Verberg waarde" : "Toon waarde"}
      >
        {revealed ? (
          <EyeOff className="size-3.5" />
        ) : (
          <Eye className="size-3.5" />
        )}
      </Button>
    </div>
  )
}
