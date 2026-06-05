"use client"

import { useCallback, useEffect, useRef, useState } from "react"

export function useCopy() {
  const [copied, setCopied] = useState(false)
  const timerRef = useRef<ReturnType<typeof setTimeout> | null>(null)

  useEffect(() => {
    return () => {
      if (timerRef.current) clearTimeout(timerRef.current)
    }
  }, [])

  const copy = useCallback(async (value: string) => {
    if (timerRef.current) clearTimeout(timerRef.current)
    await navigator.clipboard.writeText(value)
    setCopied(true)
    timerRef.current = setTimeout(() => setCopied(false), 2000)
  }, [])

  return { copied, copy }
}
