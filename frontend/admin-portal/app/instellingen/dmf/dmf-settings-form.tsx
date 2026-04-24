"use client"

import { Button } from "@/components/ui/button"
import { Checkbox } from "@/components/ui/checkbox"
import { Field, FieldError, FieldLabel } from "@/components/ui/field"
import { Input } from "@/components/ui/input"
import { Check } from "lucide-react"
import { useActionState } from "react"
import { type DmfSettings, type FormState, saveDmfSettings } from "./actions"

export default function DmfSettingsForm({
  initialSettings,
}: {
  initialSettings: DmfSettings
}) {
  const [state, formAction, isPending] = useActionState<FormState, FormData>(
    saveDmfSettings,
    {}
  )

  return (
    <form action={formAction} className="flex flex-col gap-4">
      <Field>
        <FieldLabel htmlFor="trigger-size">Trigger grootte</FieldLabel>
        <Input
          id="trigger-size"
          name="triggerSize"
          type="number"
          min={1}
          defaultValue={initialSettings.triggerSize}
          placeholder="standaard: 4294967296"
          disabled={isPending}
        />
        <FieldError>{state.errors?.triggerSize}</FieldError>
      </Field>

      <Field>
        <FieldLabel htmlFor="chunk-size">Chunk grootte</FieldLabel>
        <Input
          id="chunk-size"
          name="chunkSize"
          type="number"
          min={1}
          defaultValue={initialSettings.chunkSize}
          placeholder="standaard: 3221225472"
          disabled={isPending}
        />
        <FieldError>{state.errors?.chunkSize}</FieldError>
      </Field>

      <Field orientation="horizontal">
        <Checkbox
          id="validation-enabled"
          name="validationEnabled"
          defaultChecked={initialSettings.validationEnabled}
          disabled={isPending}
        />
        <FieldLabel htmlFor="validation-enabled">
          Validatie ingeschakeld
        </FieldLabel>
      </Field>

      {state.error && (
        <p className="text-sm text-destructive">{state.error}</p>
      )}
      {state.saved && (
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
