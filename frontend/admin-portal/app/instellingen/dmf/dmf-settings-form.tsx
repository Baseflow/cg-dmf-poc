"use client"

import { Button } from "@/components/ui/button"
import { Checkbox } from "@/components/ui/checkbox"
import { Field } from "@/components/ui/field"
import { Input } from "@/components/ui/input"
import { Skeleton } from "@/components/ui/skeleton"
import { Check } from "lucide-react"
import { useSession } from "next-auth/react"
import { useEffect, useState, useTransition } from "react"
import { z } from "zod"
import { fetchDmfSettings, saveDmfSettings } from "./actions"

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

export default function DmfSettingsForm() {
  const { data: session } = useSession()

  const [loading, setLoading] = useState(true)
  const [fetchError, setFetchError] = useState<string | null>(null)
  const [isPending, startTransition] = useTransition()
  const [saveError, setSaveError] = useState<string | null>(null)
  const [saved, setSaved] = useState(false)
  const [fieldErrors, setFieldErrors] = useState<FieldErrors>({})

  const [triggerSize, setTriggerSize] = useState("")
  const [chunkSize, setChunkSize] = useState("")
  const [validationEnabled, setValidationEnabled] = useState(false)

  useEffect(() => {
    let cancelled = false
    async function load() {
      try {
        const data = await fetchDmfSettings(session?.accessToken ?? "")
        if (cancelled) return
        setTriggerSize(String(data.triggerSize))
        setChunkSize(String(data.chunkSize))
        setValidationEnabled(data.validationEnabled)
      } catch {
        if (!cancelled) setFetchError("Kon de DMF-instellingen niet laden.")
      } finally {
        if (!cancelled) setLoading(false)
      }
    }
    load()
    return () => {
      cancelled = true
    }
  }, [session])

  useEffect(() => {
    if (!saved) return
    const timer = setTimeout(() => setSaved(false), 3000)
    return () => clearTimeout(timer)
  }, [saved])

  function handleSubmit(e: React.FormEvent<HTMLFormElement>) {
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

    const data = result.data
    startTransition(async () => {
      try {
        await saveDmfSettings(session?.accessToken ?? "", data)
        setSaved(true)
      } catch {
        setSaveError("Opslaan mislukt. Probeer het opnieuw.")
      }
    })
  }

  if (loading) {
    return (
      <div className="flex flex-col gap-4">
        <div className="flex flex-col gap-1.5">
          <Skeleton className="h-3.5 w-32" />
          <Skeleton className="h-9 w-full" />
        </div>
        <div className="flex flex-col gap-1.5">
          <Skeleton className="h-3.5 w-32" />
          <Skeleton className="h-9 w-full" />
        </div>
        <div className="flex items-center gap-2">
          <Skeleton className="size-4 rounded" />
          <Skeleton className="h-4 w-40" />
        </div>
        <Skeleton className="h-8 w-24" />
      </div>
    )
  }

  return (
    <form onSubmit={handleSubmit} className="flex flex-col gap-4">
      <Field>
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
          disabled={isPending}
        />
      </Field>

      <Field>
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
          disabled={isPending}
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
          disabled={isPending}
        />
        <label
          htmlFor="validation-enabled"
          className="text-xs leading-none font-medium peer-disabled:cursor-not-allowed peer-disabled:opacity-70"
        >
          Validatie ingeschakeld
        </label>
      </div>

      {fetchError && <p className="text-sm text-destructive">{fetchError}</p>}
      {saveError && <p className="text-sm text-destructive">{saveError}</p>}
      {saved && (
        <p className="text-sm text-primary">Instellingen opgeslagen.</p>
      )}

      <div>
        <Button type="submit" size="sm" disabled={isPending}>
          <Check />
          {isPending ? "Opslaan..." : "Opslaan"}
        </Button>
      </div>
    </form>
  )
}
