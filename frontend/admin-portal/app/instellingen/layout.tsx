"use client"

import * as React from "react"
import { useRouter } from "next/navigation"
import { useSession } from "next-auth/react"

export default function InstellingenLayout({
  children,
}: {
  children: React.ReactNode
}) {
  const { status } = useSession()
  const router = useRouter()

  React.useEffect(() => {
    if (status === "unauthenticated") {
      router.replace("/")
    }
  }, [status, router])

  if (status !== "authenticated") {
    return null
  }

  return <>{children}</>
}
