"use client"

import { Button } from "@/components/ui/button"

export default function Error({
  error,
  reset,
}: {
  error: Error & { digest?: string }
  reset: () => void
}) {
  return (
    <div className="flex min-h-svh flex-col items-center justify-center gap-4 p-6">
      <p className="text-muted-foreground text-sm">
        {error.message || "Er is een fout opgetreden."}
      </p>
      <Button variant="outline" size="sm" onClick={reset}>
        Opnieuw proberen
      </Button>
    </div>
  )
}
