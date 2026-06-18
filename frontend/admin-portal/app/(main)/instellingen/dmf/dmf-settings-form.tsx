// SPDX-License-Identifier: EUPL-1.2
// Copyright (C) 2026 Gemeente Utrecht

"use client"

import { Button } from "@/components/ui/button"
import { Checkbox } from "@/components/ui/checkbox"
import {
  Field,
  FieldContent,
  FieldDescription,
  FieldError,
  FieldLabel,
} from "@/components/ui/field"
import { Input } from "@/components/ui/input"
import { Check } from "lucide-react"
import { useState, type FormEvent } from "react"
import { type DmfSettingEntry, upsertDmfSetting } from "./actions"

function BytesField({
  id,
  label,
  description,
  value,
  onChange,
  disabled,
}: {
  id: string
  label: string
  description: string
  value: string
  onChange: (value: string) => void
  disabled: boolean
}) {
  return (
    <Field>
      <FieldLabel htmlFor={id}>{label}</FieldLabel>
      <div className="relative">
        <Input
          id={id}
          type="number"
          min={1}
          value={value}
          onChange={(e) => onChange(e.target.value)}
          className="pr-14"
          disabled={disabled}
          required
        />
        <span className="pointer-events-none absolute inset-y-0 right-3 flex items-center text-xs text-muted-foreground">
          bytes
        </span>
      </div>
      <FieldDescription>{description}</FieldDescription>
    </Field>
  )
}

export default function DmfSettingsForm({
  settings,
}: {
  settings: DmfSettingEntry[]
}) {
  const get = (key: string) => settings.find((s) => s.key === key)?.value ?? ""

  const [triggerSize, setTriggerSize] = useState(get("trigger_size_bytes"))
  const [chunkSize, setChunkSize] = useState(get("chunk_size_bytes"))
  const [validationEnabled, setValidationEnabled] = useState(
    get("validation_enabled") === "true"
  )

  const [isPending, setIsPending] = useState(false)
  const [saved, setSaved] = useState(false)
  const [error, setError] = useState<string | null>(null)

  async function handleSubmit(e: FormEvent<HTMLFormElement>) {
    e.preventDefault()
    setIsPending(true)
    setSaved(false)
    setError(null)
    try {
      await Promise.all([
        upsertDmfSetting("trigger_size_bytes", triggerSize),
        upsertDmfSetting("chunk_size_bytes", chunkSize),
        upsertDmfSetting(
          "validation_enabled",
          validationEnabled ? "true" : "false"
        ),
      ])
      setSaved(true)
    } catch {
      setError("Opslaan mislukt. Probeer het opnieuw.")
    } finally {
      setIsPending(false)
    }
  }

  return (
    <div className="flex w-full max-w-sm flex-col gap-6">
      <p className="text-sm text-muted-foreground">DMF systeeminstellingen.</p>
      <form onSubmit={handleSubmit} className="flex flex-col gap-4">
        <BytesField
          id="trigger-size"
          label="Trigger grootte"
          description="Minimale bestandsgrootte voordat een bestand wordt gesplitst. Minimaal 1 byte, standaard 4 GB."
          value={triggerSize}
          onChange={setTriggerSize}
          disabled={isPending}
        />

        <BytesField
          id="chunk-size"
          label="Chunk grootte"
          description="Grootte van elk fragment bij het splitsen van een bestand. Minimaal 1 byte, standaard 3 GB."
          value={chunkSize}
          onChange={setChunkSize}
          disabled={isPending}
        />

        <Field orientation="horizontal">
          <Checkbox
            id="validation-enabled"
            checked={validationEnabled}
            onCheckedChange={(checked) =>
              setValidationEnabled(checked === true)
            }
            disabled={isPending}
          />
          <FieldContent>
            <FieldLabel htmlFor="validation-enabled">
              Validatie ingeschakeld
            </FieldLabel>
            <FieldDescription>
              Schakel datavalidatie in of uit tijdens de verwerkingspijplijn.
            </FieldDescription>
          </FieldContent>
        </Field>

        <FieldError>{error}</FieldError>
        {saved && (
          <p className="text-sm text-green-600 dark:text-green-400">
            Instellingen opgeslagen.
          </p>
        )}

        <div>
          <Button type="submit" size="sm" disabled={isPending}>
            <Check />
            {isPending ? "Opslaan..." : "Opslaan"}
          </Button>
        </div>
      </form>
    </div>
  )
}
