"use client"

import * as React from "react"
import { useRouter } from "next/navigation"
import { useAuth } from "@/contexts/auth-context"

export default function InstellingenLayout({
  children,
}: {
  children: React.ReactNode
}) {
  const { authenticated, isLoading } = useAuth()
  const router = useRouter()

  React.useEffect(() => {
    if (!isLoading && !authenticated) {
      router.replace("/")
    }
  }, [isLoading, authenticated, router])

  if (isLoading || !authenticated) {
    return null
  }

  return <>{children}</>
}
