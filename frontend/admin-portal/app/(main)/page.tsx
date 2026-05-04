import { BaseflowAvatar } from "@/components/icons"
import { LoginButton } from "./login-button"

export default function HomePage() {
  return (
    <div className="flex flex-1 flex-col items-center justify-center gap-6 p-8 text-center">
      <div className="[&>svg]:h-8 [&>svg]:w-8 flex h-12 w-12 items-center justify-center rounded-xl bg-foreground text-background">
        <BaseflowAvatar />
      </div>
      <div className="flex flex-col gap-2">
        <h1 className="text-2xl font-bold">CG DMF Admin Portal</h1>
        <p className="text-muted-foreground max-w-sm text-sm">
          Configureer en beheer Document Management Framework integraties
        </p>
      </div>
      <LoginButton />
    </div>
  )
}
