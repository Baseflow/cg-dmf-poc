"use client"

import { Button } from "@/components/ui/button"
import { useCopy } from "@/hooks/use-copy"
import { cn } from "@/lib/utils"
import { Check, Copy } from "lucide-react"
import * as React from "react"

function Input({
  className,
  type,
  copyable,
  ...props
}: React.ComponentProps<"input"> & { copyable?: boolean }) {
  const { copied, copy } = useCopy()

  const input = (
    <input
      type={type}
      data-slot="input"
      className={cn(
        "h-8 w-full min-w-0 rounded-lg border border-input bg-transparent px-2.5 py-1 text-base transition-colors outline-none file:inline-flex file:h-6 file:border-0 file:bg-transparent file:text-sm file:font-medium file:text-foreground placeholder:text-muted-foreground focus-visible:border-ring focus-visible:ring-3 focus-visible:ring-ring/50 disabled:pointer-events-none disabled:cursor-not-allowed disabled:bg-input/50 disabled:opacity-50 aria-invalid:border-destructive aria-invalid:ring-3 aria-invalid:ring-destructive/20 md:text-sm dark:bg-input/30 dark:disabled:bg-input/80 dark:aria-invalid:border-destructive/50 dark:aria-invalid:ring-destructive/40",
        className
      )}
      {...props}
    />
  )

  if (!copyable) return input

  return (
    <div className="flex gap-2">
      <div className="flex-1">{input}</div>
      <Button
        type="button"
        variant="outline"
        size="icon"
        onClick={() => typeof props.value === "string" && copy(props.value)}
        disabled={typeof props.value !== "string" || !props.value}
        aria-label="Kopieer waarde"
      >
        {copied ? (
          <Check className="size-4 text-green-600" />
        ) : (
          <Copy className="size-4" />
        )}
      </Button>
    </div>
  )
}

export { Input }
