"use client"

import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { useCopy } from "@/hooks/use-copy"
import { cn } from "@/lib/utils"
import { Check, Copy, Eye, EyeOff, RefreshCw } from "lucide-react"
import * as React from "react"

function SecretInput({
  className,
  disabled,
  copyable,
  onGenerate,
  ...props
}: React.ComponentProps<typeof Input> & {
  copyable?: boolean
  onGenerate?: () => void
}) {
  const [show, setShow] = React.useState(false)
  const { copied, copy } = useCopy()

  const inputWrapper = (
    <div className={cn("relative", (copyable || onGenerate) && "flex-1")}>
      <Input
        type={show ? "text" : "password"}
        className={cn("pr-9", className)}
        disabled={disabled}
        {...props}
      />
      <Button
        type="button"
        variant="ghost"
        size="icon"
        onClick={() => setShow((v) => !v)}
        disabled={disabled}
        tabIndex={-1}
        aria-label={show ? "Verberg waarde" : "Toon waarde"}
        className="absolute top-1/2 right-1.5 size-6 -translate-y-1/2 text-muted-foreground hover:text-foreground"
      >
        {show ? <EyeOff className="size-4" /> : <Eye className="size-4" />}
      </Button>
    </div>
  )

  if (!copyable && !onGenerate) return inputWrapper

  return (
    <div className="flex gap-2">
      {inputWrapper}
      {onGenerate && (
        <Button
          type="button"
          variant="outline"
          size="icon"
          onClick={onGenerate}
          disabled={disabled}
          aria-label="Genereer waarde"
        >
          <RefreshCw className="size-4" />
        </Button>
      )}
      {copyable && (
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
      )}
    </div>
  )
}

export { SecretInput }
