"use client"

import * as React from "react"
import { Check, Eye, EyeOff, Pencil, X } from "lucide-react"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"

// Placeholder config — will be replaced by an API fetch
const PLACEHOLDER = {
  issuer: "https://auth.gzac.baseflow.com/realms/valtimo",
  clientId: "dmf-dashboard",
}

export default function Page() {
  const [editing, setEditing] = React.useState(false)
  const [showSecret, setShowSecret] = React.useState(false)

  const [issuer, setIssuer] = React.useState(PLACEHOLDER.issuer)
  const [clientId, setClientId] = React.useState(PLACEHOLDER.clientId)
  const [clientSecret, setClientSecret] = React.useState("")

  const [saved, setSaved] = React.useState({ issuer, clientId })

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

  function handleSave(e: React.FormEvent<HTMLFormElement>) {
    e.preventDefault()
    setSaved({ issuer, clientId })
    setClientSecret("")
    setShowSecret(false)
    setEditing(false)
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

        <form onSubmit={handleSave} className="flex flex-col gap-5">
          <Field label="Issuer">
            {editing ? (
              <Input
                value={issuer}
                onChange={(e) => setIssuer(e.target.value)}
                placeholder="https://auth.example.com/realms/my-realm"
                required
              />
            ) : (
              <Value>{issuer}</Value>
            )}
          </Field>

          <Field label="Client ID">
            {editing ? (
              <Input
                value={clientId}
                onChange={(e) => setClientId(e.target.value)}
                placeholder="my-client-id"
                required
              />
            ) : (
              <Value>{clientId}</Value>
            )}
          </Field>

          <Field label="Client secret">
            {editing ? (
              <div className="relative">
                <Input
                  type={showSecret ? "text" : "password"}
                  value={clientSecret}
                  onChange={(e) => setClientSecret(e.target.value)}
                  placeholder="Laat leeg om huidig secret te bewaren"
                  className="pr-9"
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
              <Value className="tracking-widest">••••••••••••</Value>
            )}
          </Field>

          {editing && (
            <div className="flex gap-2">
              <Button type="submit" size="sm">
                <Check />
                Opslaan
              </Button>
              <Button
                type="button"
                variant="outline"
                size="sm"
                onClick={handleCancel}
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
      className={`font-mono text-xs leading-6 text-muted-foreground ${className ?? ""}`}
    >
      {children}
    </p>
  )
}
