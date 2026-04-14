"use client"

import { useState } from "react"
import { Button } from "@/components/ui/button"
import { useTheme } from "next-themes"

export function Counter() {
  const [count, setCount] = useState(0)

  const { resolvedTheme, setTheme } = useTheme()

  function toggleTheme() {
    setTheme(resolvedTheme === "dark" ? "light" : "dark")
  }

  return (
    <div className="mt-6 flex items-center gap-3">
      <Button variant="outline" onClick={() => { setCount((c) => c - 1); toggleTheme(); }}>
        -1
      </Button>
      <p className="min-w-16 text-center font-medium">Count: {count}</p>
      <Button onClick={() => { setCount((c) => c + 1); toggleTheme(); }}>+1</Button>
    </div>
  )
}