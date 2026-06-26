"use client"

import { useState } from "react"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { SecretInput } from "@/components/ui/secret-input"
import {
  Field,
  FieldGroup,
  FieldLabel,
  FieldSet,
  FieldError,
} from "@/components/ui/field"
import { useCopy } from "@/hooks/use-copy"
import { Check, Copy, KeyRound } from "lucide-react"

function base64url(bytes: Uint8Array): string {
  let binary = ""
  for (let i = 0; i < bytes.length; i++) binary += String.fromCharCode(bytes[i])
  return btoa(binary).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "")
}

async function createToken(
  clientId: string,
  secret: string,
  userId: string,
  userRepresentation: string
): Promise<string> {
  const enc = new TextEncoder()
  const header = base64url(enc.encode(JSON.stringify({ alg: "HS256", typ: "JWT" })))
  const payload = base64url(
    enc.encode(
      JSON.stringify({
        iss: clientId,
        iat: Math.floor(Date.now() / 1000),
        client_id: clientId,
        user_id: userId,
        user_representation: userRepresentation || userId,
      })
    )
  )
  const signingInput = `${header}.${payload}`
  const key = await crypto.subtle.importKey(
    "raw",
    enc.encode(secret),
    { name: "HMAC", hash: "SHA-256" },
    false,
    ["sign"]
  )
  const sig = new Uint8Array(await crypto.subtle.sign("HMAC", key, enc.encode(signingInput)))
  return `${signingInput}.${base64url(sig)}`
}

export function ZgwTokenGenerator() {
  const [clientId, setClientId] = useState("")
  const [secret, setSecret] = useState("")
  const [userId, setUserId] = useState("")
  const [userRepr, setUserRepr] = useState("")
  const [token, setToken] = useState("")
  const [error, setError] = useState("")
  const { copied, copy } = useCopy()

  async function generate() {
    setError("")
    setToken("")
    if (!clientId.trim()) { setError("Client ID is verplicht."); return }
    if (!secret) { setError("Client Secret is verplicht."); return }
    if (!crypto?.subtle) {
      setError("De browser ondersteunt de Web Crypto API niet in deze context. Gebruik HTTPS of localhost.")
      return
    }
    try {
      const t = await createToken(clientId.trim(), secret, userId.trim(), userRepr.trim())
      setToken(t)
    } catch {
      setError("Fout bij genereren van token.")
    }
  }

  return (
    <div className="flex flex-col gap-6">
      <FieldSet>
        <FieldGroup>
          <Field>
            <FieldLabel htmlFor="zgw-client-id">Client ID</FieldLabel>
            <Input
              id="zgw-client-id"
              value={clientId}
              onChange={(e) => setClientId(e.target.value)}
              placeholder="mijn-applicatie"
              autoComplete="off"
            />
          </Field>
          <Field>
            <FieldLabel htmlFor="zgw-secret">Client Secret</FieldLabel>
            <SecretInput
              id="zgw-secret"
              value={secret}
              onChange={(e) => setSecret(e.target.value)}
              placeholder="geheime sleutel"
              autoComplete="new-password"
            />
          </Field>
          <Field>
            <FieldLabel htmlFor="zgw-user-id">User ID</FieldLabel>
            <Input
              id="zgw-user-id"
              value={userId}
              onChange={(e) => setUserId(e.target.value)}
              placeholder="gebruiker@example.com"
            />
          </Field>
          <Field>
            <FieldLabel htmlFor="zgw-user-repr">
              User Representation{" "}
              <span className="font-normal text-muted-foreground">(optioneel)</span>
            </FieldLabel>
            <Input
              id="zgw-user-repr"
              value={userRepr}
              onChange={(e) => setUserRepr(e.target.value)}
              placeholder="Weergavenaam (bijv. Jan de Vries)"
            />
          </Field>
        </FieldGroup>
      </FieldSet>

      {error && <FieldError>{error}</FieldError>}

      <div>
        <Button onClick={generate}>
          <KeyRound className="size-4" />
          Genereer token
        </Button>
      </div>

      {token && (
        <Field>
          <FieldLabel>Gegenereerd token</FieldLabel>
          <div className="flex gap-2">
            <textarea
              readOnly
              value={token}
              rows={4}
              className="flex-1 resize-none rounded-md border bg-muted px-3 py-2 font-mono text-xs"
            />
            <Button
              type="button"
              variant="outline"
              size="icon"
              onClick={() => copy(token)}
              aria-label="Kopieer token"
            >
              {copied ? (
                <Check className="size-4 text-green-600" />
              ) : (
                <Copy className="size-4" />
              )}
            </Button>
          </div>
        </Field>
      )}
    </div>
  )
}