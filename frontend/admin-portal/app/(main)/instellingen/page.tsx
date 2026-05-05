import { navigation } from "@/lib/navigation"
import Link from "next/link"

export default function Page() {
  const group = navigation.primary.find((g) => g.id === "instellingen")

  return (
    <div className="flex flex-1 flex-col gap-6 p-6">
      <h1 className="text-2xl font-semibold">Instellingen</h1>
      <nav className="grid grid-cols-1 gap-3 sm:grid-cols-2 lg:grid-cols-3">
        {(group?.items ?? []).map(({ url, icon, name, description }) => (
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
            {description && (
              <p className="text-xs text-muted-foreground">{description}</p>
            )}
          </Link>
        ))}
      </nav>
    </div>
  )
}
