import NextAuth from "next-auth"
import type { JWT } from "next-auth/jwt"
import Keycloak from "next-auth/providers/keycloak"

const keycloakClientId = process.env.KEYCLOAK_CLIENT_ID!
const keycloakClientSecret = process.env.KEYCLOAK_CLIENT_SECRET!
const keycloakUrl = process.env.KEYCLOAK_URL!
const keycloakRealm = process.env.KEYCLOAK_REALM!

export const { handlers, auth, signIn, signOut } = NextAuth({
  trustHost: true,
  basePath: "/admin/api/auth",
  providers: [
    Keycloak({
      clientId: keycloakClientId,
      clientSecret: keycloakClientSecret,
      issuer: `${keycloakUrl}/realms/${keycloakRealm}`,
    }),
  ],
  callbacks: {
    async jwt({ token, account }) {
      if (account) {
        return {
          ...token,
          accessToken: account.access_token,
          refreshToken: account.refresh_token,
          expiresAt: account.expires_at
            ? account.expires_at * 1000
            : Date.now() + 3600 * 1000,
        }
      }
      if (Date.now() < (token.expiresAt ?? 0)) return token
      return refreshAccessToken(token)
    },
    async session({ session, token }) {
      session.accessToken = token.accessToken
      session.error = token.error
      return session
    },
  },
  events: {
    async signOut(message) {
      if (!("token" in message) || !message.token?.refreshToken) return
      const { token } = message
      await fetch(
        `${keycloakUrl}/realms/${keycloakRealm}/protocol/openid-connect/logout`,
        {
          method: "POST",
          headers: { "Content-Type": "application/x-www-form-urlencoded" },
          body: new URLSearchParams({
            client_id: keycloakClientId,
            client_secret: keycloakClientSecret,
            refresh_token: token.refreshToken!,
          }),
        }
      )
    },
  },
  debug: process.env.NODE_ENV === "development",
})

async function refreshAccessToken(token: JWT): Promise<JWT> {
  try {
    const response = await fetch(
      `${keycloakUrl}/realms/${keycloakRealm}/protocol/openid-connect/token`,
      {
        method: "POST",
        headers: { "Content-Type": "application/x-www-form-urlencoded" },
        body: new URLSearchParams({
          client_id: keycloakClientId,
          client_secret: keycloakClientSecret,
          grant_type: "refresh_token",
          refresh_token: token.refreshToken!,
        }),
      }
    )
    const refreshed = await response.json()
    if (!response.ok) throw refreshed
    return {
      ...token,
      accessToken: refreshed.access_token,
      refreshToken: refreshed.refresh_token ?? token.refreshToken,
      expiresAt: Date.now() + refreshed.expires_in * 1000,
      error: undefined,
    }
  } catch {
    return { ...token, error: "RefreshAccessTokenError" }
  }
}
