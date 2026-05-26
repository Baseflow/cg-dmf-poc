"use client"

import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { cn } from "@/lib/utils"
import { Eye, EyeOff } from "lucide-react"
import * as React from "react"

function SecretInput({
  className,
  disabled,
  ...props
}: React.ComponentProps<typeof Input>) {
  const [show, setShow] = React.useState(false)
  return (
    <div className="relative">
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
}

export { SecretInput }
