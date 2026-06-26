import { type NavItem, navigation } from "@/lib/navigation"
import { Button } from "@/components/ui/button"
import { ROUTES } from "@/lib/routes"
import { ExternalLink, KeyRound } from "lucide-react"
import Link from "next/link"

function NavCard({ url, icon, name, description }: NavItem) {
  const isExternal = url.startsWith("http")
  const cardClass =
    "group flex flex-col gap-2 rounded-lg border p-4 transition-colors hover:bg-muted/50"
  const content = (
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
        <p className="text-xs text-muted-foreground">{description}</p>
      )}
    </>
  )
  return isExternal ? (
    <a
      href={url}
      target="_blank"
      rel="noopener noreferrer"
      className={cardClass}
    >
      {content}
    </a>
  ) : (
    <Link href={url} className={cardClass}>
      {content}
    </Link>
  )
}

const sections = [
  {
    label: "Documenten API",
    items: navigation.verkenner.filter((item) =>
      item.url.includes("documenten")
    ),
  },
  {
    label: "WOPI API",
    items: navigation.verkenner.filter((item) => item.url.includes("wopi")),
  },
]

export default function Page() {
  return (
    <div className="flex flex-1 flex-col gap-8 p-6">
      <div className="flex items-start justify-between gap-4">
        <div>
          <h1 className="text-xl font-semibold">API verkenner</h1>
          <p className="mt-1 text-sm text-muted-foreground">
            API browsers en OpenAPI specificaties
          </p>
        </div>
        <Button variant="outline" asChild>
          <Link href={ROUTES.tools.zgwToken}>
            <KeyRound className="size-4" />
            ZGW Token Generator
          </Link>
        </Button>
      </div>
      {sections.map(({ label, items }) => (
        <section key={label} className="flex flex-col gap-3">
          <h2 className="text-xs font-medium tracking-wider text-muted-foreground uppercase">
            {label}
          </h2>
          <nav className="grid grid-cols-1 gap-3 sm:grid-cols-2 lg:grid-cols-3">
            {items.map((item) => (
              <NavCard key={item.url} {...item} />
            ))}
          </nav>
        </section>
      ))}
    </div>
  )
}
