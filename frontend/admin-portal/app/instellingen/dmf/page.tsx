import DmfSettingsForm from "./dmf-settings-form"

export default function Page() {
  return (
    <div className="flex min-h-svh p-6">
      <div className="flex w-full max-w-sm flex-col gap-6">
        <p className="text-sm text-muted-foreground">
          DMF-systeeminstellingen.
        </p>
        <DmfSettingsForm />
      </div>
    </div>
  )
}
