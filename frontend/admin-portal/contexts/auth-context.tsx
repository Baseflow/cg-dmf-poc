"use client"

import * as React from "react"
import Keycloak from "keycloak-js"
import {
  KEYCLOAK_URL,
  KEYCLOAK_REALM,
  KEYCLOAK_CLIENT_ID,
} from "@/lib/keycloak-config"

export interface User {
  name: string
  email: string
  username: string
  initials: string
}

export interface AuthContextValue {
  authenticated: boolean
  isLoading: boolean
  user: User | null
  keycloak: Keycloak
}

const AuthContext = React.createContext<AuthContextValue | null>(null)

export function AuthProvider({ children }: { children: React.ReactNode }) {
  // useState initializer only runs client-side (this is a "use client" component)
  const [keycloak] = React.useState(
    () =>
      new Keycloak({
        url: KEYCLOAK_URL,
        realm: KEYCLOAK_REALM,
        clientId: KEYCLOAK_CLIENT_ID,
      })
  )
  const [authenticated, setAuthenticated] = React.useState(false)
  const [isLoading, setIsLoading] = React.useState(true)
  const [user, setUser] = React.useState<User | null>(null)

  // Guard against React strict mode double-invocation
  const initialized = React.useRef(false)

  React.useEffect(() => {
    if (initialized.current) return
    initialized.current = true

    keycloak
      .init({
        onLoad: "check-sso",
        silentCheckSsoRedirectUri:
          window.location.origin + "/silent-check-sso.html",
      })
      .then((auth) => {
        setAuthenticated(auth)
        if (auth && keycloak.tokenParsed) {
          const parsed = keycloak.tokenParsed
          const givenName = typeof parsed?.given_name === "string" ? parsed.given_name : ""
          const familyName = typeof parsed?.family_name === "string" ? parsed.family_name : ""
          setUser({
            name: typeof parsed?.name === "string" ? parsed.name : (parsed?.preferred_username ?? ""),
            email: typeof parsed?.email === "string" ? parsed.email : "",
            username: typeof parsed?.preferred_username === "string" ? parsed.preferred_username : "",
            initials:
              (givenName[0] ?? "").toUpperCase() +
              (familyName[0] ?? "").toUpperCase(),
          })
        }
        setIsLoading(false)
      })
      .catch((err) => {
        if (process.env.NODE_ENV !== "production") {
          console.error("[AuthProvider] keycloak.init failed:", err)
        }
        setIsLoading(false)
      })

    return () => {
      initialized.current = false
    }
  }, [keycloak])

  return (
    <AuthContext.Provider value={{ authenticated, isLoading, user, keycloak }}>
      {children}
    </AuthContext.Provider>
  )
}

export function useAuth(): AuthContextValue {
  const context = React.useContext(AuthContext)
  if (!context) {
    throw new Error("useAuth must be used within an AuthProvider")
  }
  return context
}
