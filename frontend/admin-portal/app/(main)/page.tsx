import { BaseflowAvatar } from "@/components/icons"
import { LoginButton } from "./login-button"
import Link from "next/link"
import { FileKey2, FolderOpen, KeyRound, Settings, Layers } from "lucide-react"

const navItems = [
  {
    href: "/instellingen/oidc",
    icon: KeyRound,
    label: "OIDC",
    description: "OpenID Connect authenticatieproviders beheren",
  },
  {
    href: "/instellingen/zgw-api",
    icon: Layers,
    label: "ZGW API",
    description: "ZGW API-koppelingsprofielen configureren",
  },
  {
    href: "/instellingen/repositories",
    icon: FolderOpen,
    label: "Repositories",
    description: "Object store repositories instellen",
  },
  {
    href: "/instellingen/dmf",
    icon: Settings,
    label: "DMF",
    description: "DMF-systeeminstellingen aanpassen",
  },
  {
    href: "/instellingen/applicaties",
    icon: FileKey2,
    label: "Applicaties",
    description: "Gekoppelde applicaties beheren",
  },
]

export default function Page() {
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
        <LoginButton />
      </div>

      <nav className="grid w-full max-w-2xl grid-cols-1 gap-3 sm:grid-cols-2 lg:grid-cols-3">
        {navItems.map(({ href, icon: Icon, label, description }) => (
          <Link
            key={href}
            href={href}
            className="group flex flex-col gap-2 rounded-lg border p-4 transition-colors hover:bg-muted/50"
          >
            <div className="flex items-center gap-2">
              <Icon className="h-4 w-4 text-muted-foreground transition-colors group-hover:text-foreground" />
              <span className="text-sm font-medium">{label}</span>
            </div>
            <p className="text-xs text-muted-foreground">{description}</p>
          </Link>
        ))}
      </nav>
    </div>
  )
}
