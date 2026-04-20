"use client"

import * as React from "react"
import { Check, Eye, EyeOff, Pencil, X } from "lucide-react"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { Skeleton } from "@/components/ui/skeleton"
import { useAuth } from "@/contexts/auth-context"
import { cn } from "@/lib/utils"

const API_URL = process.env.NEXT_PUBLIC_API_URL ?? ""

interface OidcSettings {
  issuer: string
  clientId: string
  hasSecret: boolean
  updatedAt: string
}

export default function Page() {
  const { keycloak } = useAuth()

  const [loading, setLoading] = React.useState(true)
  const [error, setError] = React.useState<string | null>(null)
  const [editing, setEditing] = React.useState(false)
  const [saving, setSaving] = React.useState(false)
  const [showSecret, setShowSecret] = React.useState(false)

  const [issuer, setIssuer] = React.useState("")
  const [clientId, setClientId] = React.useState("")
  const [clientSecret, setClientSecret] = React.useState("")
  const [hasSecret, setHasSecret] = React.useState(false)

  const [saved, setSaved] = React.useState({ issuer: "", clientId: "" })

  React.useEffect(() => {
    async function fetchSettings() {
      try {
        await keycloak.updateToken(30)
        const res = await fetch(`${API_URL}/admin/oidc-settings`, {
          headers: { Authorization: `Bearer ${keycloak.token ?? ""}` },
        })
        if (res.status === 404) {
          setLoading(false)
          return
        }
        if (!res.ok) throw new Error(`HTTP ${res.status}`)
        const data: OidcSettings = await res.json()
        setIssuer(data.issuer)
        setClientId(data.clientId)
        setHasSecret(data.hasSecret)
      } catch {
        setError("Kon de OIDC-instellingen niet laden.")
      } finally {
        setLoading(false)
      }
    }
    fetchSettings()
  }, [keycloak, keycloak.token])

  function handleEdit() {
    setSaved({ issuer, clientId })
    setEditing(true)
  }

  function handleCancel() {
    setIssuer(saved.issuer)
    setClientId(saved.clientId)
    setClientSecret("")
    setShowSecret(false)
    setEditing(false)
  }

  async function handleSave(e: React.FormEvent<HTMLFormElement>) {
    e.preventDefault()
    setSaving(true)
    setError(null)
    try {
      await keycloak.updateToken(30)
      const body: { issuer: string; clientId: string; clientSecret?: string } =
        { issuer, clientId }
      if (clientSecret) body.clientSecret = clientSecret
      const res = await fetch(`${API_URL}/admin/oidc-settings`, {
        method: "PUT",
        headers: {
          Authorization: `Bearer ${keycloak.token ?? ""}`,
          "Content-Type": "application/json",
        },
        body: JSON.stringify(body),
      })
      if (!res.ok) throw new Error(`HTTP ${res.status}`)
      const data: OidcSettings = await res.json()
      setIssuer(data.issuer)
      setClientId(data.clientId)
      setHasSecret(data.hasSecret)
      setSaved({ issuer: data.issuer, clientId: data.clientId })
      setClientSecret("")
      setShowSecret(false)
      setEditing(false)
    } catch {
      setError("Opslaan mislukt. Probeer het opnieuw.")
    } finally {
      setSaving(false)
    }
  }

  if (loading) {
    return (
      <div className="flex min-h-svh p-6">
        <div className="flex w-full max-w-sm flex-col gap-6">
          <Skeleton className="h-4 w-3/4" />
          <div className="flex flex-col gap-5">
            <FieldSkeleton />
            <FieldSkeleton />
            <FieldSkeleton />
          </div>
        </div>
      </div>
    )
  }

  return (
    <div className="flex min-h-svh p-6">
      <div className="flex w-full max-w-sm flex-col gap-6">
        <div className="flex items-start justify-between">
          <p className="text-sm text-muted-foreground">
            Configuratie voor OpenID Connect authenticatie.
          </p>
          {!editing && (
            <Button variant="outline" size="sm" onClick={handleEdit}>
              <Pencil />
              Bewerken
            </Button>
          )}
        </div>

        {error && <p className="text-sm text-destructive">{error}</p>}

        <form onSubmit={handleSave} className="flex flex-col gap-5">
          <Field label="Issuer">
            {editing ? (
              <Input
                value={issuer}
                onChange={(e) => setIssuer(e.target.value)}
                placeholder="https://auth.example.com/realms/my-realm"
                required
                disabled={saving}
              />
            ) : (
              <Value>
                {issuer || <span className="italic">Niet geconfigureerd</span>}
              </Value>
            )}
          </Field>

          <Field label="Client ID">
            {editing ? (
              <Input
                value={clientId}
                onChange={(e) => setClientId(e.target.value)}
                placeholder="my-client-id"
                required
                disabled={saving}
              />
            ) : (
              <Value>
                {clientId || (
                  <span className="italic">Niet geconfigureerd</span>
                )}
              </Value>
            )}
          </Field>

          <Field label="Client secret">
            {editing ? (
              <div className="relative">
                <Input
                  type={showSecret ? "text" : "password"}
                  value={clientSecret}
                  onChange={(e) => setClientSecret(e.target.value)}
                  placeholder={
                    hasSecret
                      ? "Laat leeg om huidig secret te bewaren"
                      : "Voer het client secret in"
                  }
                  className="pr-9"
                  disabled={saving}
                />
                <button
                  type="button"
                  onClick={() => setShowSecret((v) => !v)}
                  className="absolute top-1/2 right-2.5 -translate-y-1/2 text-muted-foreground transition-colors hover:text-foreground"
                  aria-label={showSecret ? "Verberg secret" : "Toon secret"}
                >
                  {showSecret ? (
                    <EyeOff className="size-4" />
                  ) : (
                    <Eye className="size-4" />
                  )}
                </button>
              </div>
            ) : (
              <Value className="tracking-widest">
                {hasSecret ? (
                  "••••••••••••"
                ) : (
                  <span className="tracking-normal italic">
                    Niet geconfigureerd
                  </span>
                )}
              </Value>
            )}
          </Field>

          {editing && (
            <div className="flex gap-2">
              <Button type="submit" size="sm" disabled={saving}>
                <Check />
                {saving ? "Opslaan..." : "Opslaan"}
              </Button>
              <Button
                type="button"
                variant="outline"
                size="sm"
                onClick={handleCancel}
                disabled={saving}
              >
                <X />
                Annuleren
              </Button>
            </div>
          )}
        </form>
      </div>
    </div>
  )
}

function Field({
  label,
  children,
}: {
  label: string
  children: React.ReactNode
}) {
  return (
    <div className="flex flex-col gap-1.5">
      <span className="text-xs font-medium">{label}</span>
      {children}
    </div>
  )
}

function Value({
  children,
  className,
}: {
  children: React.ReactNode
  className?: string
}) {
  return (
    <p
      className={cn(
        "font-mono text-xs leading-6 text-muted-foreground",
        className
      )}
    >
      {children}
    </p>
  )
}

function FieldSkeleton() {
  return (
    <div className="flex flex-col gap-1.5">
      <Skeleton className="h-3 w-16" />
      <Skeleton className="h-4 w-48" />
    </div>
  )
}
