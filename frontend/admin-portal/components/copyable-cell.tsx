"use client"

import { Button } from "@/components/ui/button"
import { useCopy } from "@/hooks/use-copy"
import { Check, Copy } from "lucide-react"

export function CopyableCell({ value }: { value: string }) {
  const { copied, copy } = useCopy()

  return (
    <div className="flex items-center gap-0.5">
      <span className="font-mono text-xs text-muted-foreground">{value}</span>
      <Button
        variant="ghost"
        size="icon"
        className="size-6 shrink-0 text-muted-foreground hover:text-foreground"
        onClick={() => copy(value)}
        aria-label="Kopieer waarde"
      >
        {copied ? (
          <Check className="size-3.5 text-green-600" />
        ) : (
          <Copy className="size-3.5" />
        )}
      </Button>
    </div>
  )
}
