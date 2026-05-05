import { auth } from "@/auth"
import { BaseflowAvatar } from "@/components/icons"
import { Button } from "@/components/ui/button"
import { navigation } from "@/lib/navigation"
import Link from "next/link"
import { login } from "./actions"

export default async function Page() {
  const session = await auth()

  return (
    <div className="flex flex-1 flex-col items-center justify-center gap-8 p-8">
      <div className="flex flex-col items-center gap-4 text-center">
        <div className="flex h-12 w-12 items-center justify-center rounded-xl bg-foreground text-background [&>svg]:h-8 [&>svg]:w-8">
          <BaseflowAvatar />
        </div>
        <div className="flex flex-col gap-1">
          <h1 className="text-2xl font-bold">CG DMF Admin Portal</h1>
          <p className="max-w-sm text-sm text-muted-foreground">
            Configureer en beheer Document Management Framework integraties
          </p>
        </div>
        {!session && (
          <form action={login}>
            <Button type="submit">Log in</Button>
          </form>
        )}
      </div>

      {session && (
        <nav className="grid w-full max-w-2xl grid-cols-1 gap-3 sm:grid-cols-2 lg:grid-cols-3">
          {navigation.primary.settings.map(
            ({ url, icon, name, description }) => (
              <Link
                key={url}
                href={url}
                className="group flex flex-col gap-2 rounded-lg border p-4 transition-colors hover:bg-muted/50"
              >
                <div className="flex items-center gap-2">
                  <span className="text-muted-foreground transition-colors group-hover:text-foreground [&>svg]:h-4 [&>svg]:w-4">
                    {icon}
                  </span>
                  <span className="text-sm font-medium">{name}</span>
                </div>
                <p className="text-xs text-muted-foreground">{description}</p>
              </Link>
            )
          )}
        </nav>
      )}
    </div>
  )
}
