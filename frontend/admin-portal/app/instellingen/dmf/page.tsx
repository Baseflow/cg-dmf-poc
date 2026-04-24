"use client"

import * as React from "react"
import { z } from "zod"
import { Check } from "lucide-react"
import { Button } from "@/components/ui/button"
import { Checkbox } from "@/components/ui/checkbox"
import { Input } from "@/components/ui/input"
import { Skeleton } from "@/components/ui/skeleton"
import { useAuth } from "@/contexts/auth-context"

const API_URL = process.env.NEXT_PUBLIC_API_URL ?? ""

interface DmfSettings {
  triggerSize: number
  chunkSize: number
  validationEnabled: boolean
}

const settingsSchema = z.object({
  triggerSize: z.coerce
    .number()
    .int("Moet een geheel getal zijn.")
    .min(1, "Moet minimaal 1 byte zijn."),
  chunkSize: z.coerce
    .number()
    .int("Moet een geheel getal zijn.")
    .min(1, "Moet minimaal 1 byte zijn."),
  validationEnabled: z.boolean(),
})

type SettingsFields = z.infer<typeof settingsSchema>
type FieldErrors = Partial<Record<keyof SettingsFields, string>>

export default function Page() {
  const { keycloak } = useAuth()

  const [loading, setLoading] = React.useState(true)
  const [error, setError] = React.useState<string | null>(null)
  const [saving, setSaving] = React.useState(false)
  const [saveError, setSaveError] = React.useState<string | null>(null)
  const [saved, setSaved] = React.useState(false)
  const [fieldErrors, setFieldErrors] = React.useState<FieldErrors>({})

  const [triggerSize, setTriggerSize] = React.useState("")
  const [chunkSize, setChunkSize] = React.useState("")
  const [validationEnabled, setValidationEnabled] = React.useState(false)

  React.useEffect(() => {
    async function fetchSettings() {
      try {
        await keycloak.updateToken(30)
        const res = await fetch(`${API_URL}/admin/dmf-settings`, {
          headers: { Authorization: `Bearer ${keycloak.token ?? ""}` },
        })
        if (!res.ok) throw new Error(`HTTP ${res.status}`)
        const data: DmfSettings = await res.json()
        setTriggerSize(String(data.triggerSize))
        setChunkSize(String(data.chunkSize))
        setValidationEnabled(data.validationEnabled)
      } catch {
        setError("Kon de DMF-instellingen niet laden.")
      } finally {
        setLoading(false)
      }
    }
    fetchSettings()
  }, [keycloak])

  React.useEffect(() => {
    if (!saved) return
    const timer = setTimeout(() => setSaved(false), 3000)
    return () => clearTimeout(timer)
  }, [saved])

  async function handleSubmit(e: React.FormEvent<HTMLFormElement>) {
    e.preventDefault()
    setSaveError(null)
    setSaved(false)
    setFieldErrors({})

    const result = settingsSchema.safeParse({
      triggerSize,
      chunkSize,
      validationEnabled,
    })

    if (!result.success) {
      const errors: FieldErrors = {}
      for (const issue of result.error.issues) {
        const key = issue.path[0] as keyof SettingsFields
        if (!errors[key]) errors[key] = issue.message
      }
      setFieldErrors(errors)
      return
    }

    setSaving(true)
    try {
      await keycloak.updateToken(30)
      const res = await fetch(`${API_URL}/admin/dmf-settings`, {
        method: "PUT",
        headers: {
          Authorization: `Bearer ${keycloak.token ?? ""}`,
          "Content-Type": "application/json",
        },
        body: JSON.stringify(result.data),
      })
      if (!res.ok) throw new Error(`HTTP ${res.status}`)
      setSaved(true)
    } catch {
      setSaveError("Opslaan mislukt. Probeer het opnieuw.")
    } finally {
      setSaving(false)
    }
  }

  if (loading) {
    return (
      <div className="flex min-h-svh p-6">
        <div className="flex w-full max-w-sm flex-col gap-6">
          <Skeleton className="h-4 w-64" />
          <div className="flex flex-col gap-4">
            <FormFieldSkeleton />
            <FormFieldSkeleton />
            <div className="flex items-center gap-2">
              <Skeleton className="size-4 rounded" />
              <Skeleton className="h-4 w-40" />
            </div>
          </div>
          <Skeleton className="h-8 w-24" />
        </div>
      </div>
    )
  }

  return (
    <div className="flex min-h-svh p-6">
      <div className="flex w-full max-w-sm flex-col gap-6">
        <p className="text-sm text-muted-foreground">
          DMF-systeeminstellingen.
        </p>

        {error && <p className="text-sm text-destructive">{error}</p>}

        <form onSubmit={handleSubmit} className="flex flex-col gap-4">
          <Field
            label="Trigger grootte (bestandsdelen, in bytes)"
            htmlFor="trigger-size"
            error={fieldErrors.triggerSize}
          >
            <Input
              id="trigger-size"
              type="number"
              min={1}
              value={triggerSize}
              onChange={(e) => {
                setTriggerSize(e.target.value)
                setSaved(false)
              }}
              placeholder="standaard: 4294967296"
              disabled={saving}
            />
          </Field>

          <Field
            label="Chunk grootte (in bytes)"
            htmlFor="chunk-size"
            error={fieldErrors.chunkSize}
          >
            <Input
              id="chunk-size"
              type="number"
              min={1}
              value={chunkSize}
              onChange={(e) => {
                setChunkSize(e.target.value)
                setSaved(false)
              }}
              placeholder="standaard: 3221225472"
              disabled={saving}
            />
          </Field>

          <div className="flex items-center gap-2">
            <Checkbox
              id="validation-enabled"
              checked={validationEnabled}
              onCheckedChange={(checked) => {
                setValidationEnabled(checked === true)
                setSaved(false)
              }}
              disabled={saving}
            />
            <label
              htmlFor="validation-enabled"
              className="text-xs leading-none font-medium peer-disabled:cursor-not-allowed peer-disabled:opacity-70"
            >
              Validatie ingeschakeld
            </label>
          </div>

          {saveError && <p className="text-sm text-destructive">{saveError}</p>}
          {saved && (
            <p className="text-sm text-primary">Instellingen opgeslagen.</p>
          )}

          <div>
            <Button type="submit" size="sm" disabled={saving}>
              <Check />
              {saving ? "Opslaan..." : "Opslaan"}
            </Button>
          </div>
        </form>
      </div>
    </div>
  )
}

function Field({
  label,
  htmlFor,
  error,
  children,
}: {
  label: string
  htmlFor?: string
  error?: string
  children: React.ReactNode
}) {
  return (
    <div className="flex flex-col gap-1.5">
      <label htmlFor={htmlFor} className="text-xs font-medium">
        {label}
      </label>
      {children}
      {error && <p className="text-xs text-destructive">{error}</p>}
    </div>
  )
}

function FormFieldSkeleton() {
  return (
    <div className="flex flex-col gap-1.5">
      <Skeleton className="h-3.5 w-32" />
      <Skeleton className="h-9 w-full" />
    </div>
  )
}
