"use client"

import { Button } from "@/components/ui/button"
import { signIn, useSession } from "next-auth/react"

export function LoginButton() {
  const { status } = useSession()

  if (status === "authenticated") return null

  return (
    <Button onClick={() => signIn("keycloak")} disabled={status === "loading"}>
      Log in
    </Button>
  )
}
