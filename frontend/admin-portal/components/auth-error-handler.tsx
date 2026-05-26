"use client"

import { signOut, useSession } from "next-auth/react"
import { useEffect } from "react"

export function AuthErrorHandler() {
  const { data: session } = useSession()

  useEffect(() => {
    if (session?.error === "RefreshAccessTokenError") {
      signOut({ callbackUrl: "/" })
    }
  }, [session?.error])

  return null
}
