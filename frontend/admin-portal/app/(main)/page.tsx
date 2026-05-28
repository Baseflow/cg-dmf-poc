import { auth } from "@/auth"
import { BaseflowAvatar } from "@/components/icons"
import { Button } from "@/components/ui/button"
import { navigation } from "@/lib/navigation"
import { ExternalLink } from "lucide-react"
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
            Configureer en beheer Document Management Framework integraties.
          </p>
        </div>
        {!session && (
          <div className="flex flex-col items-center gap-2">
            <p className="text-sm text-muted-foreground">
              Log in om de instellingen te bekijken en te wijzigen.
            </p>
            <form action={login}>
              <Button type="submit">Inloggen</Button>
            </form>
          </div>
        )}
      </div>

      <div className="flex w-full max-w-2xl flex-col gap-8">
        {session && (
          <section className="flex flex-col gap-3">
            <h2 className="text-xs font-medium tracking-wider text-muted-foreground uppercase">
              {navigation.primary.find((g) => g.id === "instellingen")?.label}
            </h2>
            <nav className="grid grid-cols-1 gap-3 sm:grid-cols-2 lg:grid-cols-3">
              {(
                navigation.primary.find((g) => g.id === "instellingen")
                  ?.items ?? []
              ).map(({ url, icon, name, description }) => (
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
              ))}
            </nav>
          </section>
        )}

        {["documenten", "links"].map((id) => {
          const group = navigation.primary.find((g) => g.id === id)
          if (!group) return null
          return (
            <section key={id} className="flex flex-col gap-3">
              <h2 className="text-xs font-medium tracking-wider text-muted-foreground uppercase">
                {group.label}
              </h2>
              <nav className="grid grid-cols-1 gap-3 sm:grid-cols-2 lg:grid-cols-3">
                {group.items.map(({ url, icon, name, description }) => {
                  const isExternal = url.startsWith("http")
                  const cardClass =
                    "group flex flex-col gap-2 rounded-lg border p-4 transition-colors hover:bg-muted/50"
                  const cardContent = (
                    <>
                      <div className="flex items-center gap-2">
                        <span className="text-muted-foreground transition-colors group-hover:text-foreground [&>svg]:h-4 [&>svg]:w-4">
                          {icon}
                        </span>
                        <span className="flex-1 text-sm font-medium">{name}</span>
                        {isExternal && (
                          <ExternalLink className="size-3 shrink-0 text-muted-foreground/60" />
                        )}
                      </div>
                      {description && (
                        <p className="text-xs text-muted-foreground">
                          {description}
                        </p>
                      )}
                    </>
                  )
                  return isExternal ? (
                    <a
                      key={url}
                      href={url}
                      target="_blank"
                      rel="noopener noreferrer"
                      className={cardClass}
                    >
                      {cardContent}
                    </a>
                  ) : (
                    <Link key={url} href={url} className={cardClass}>
                      {cardContent}
                    </Link>
                  )
                })}
              </nav>
            </section>
          )
        })}
      </div>
    </div>
  )
}
