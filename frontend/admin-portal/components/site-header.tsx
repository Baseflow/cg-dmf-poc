"use client"

import { Separator } from "@/components/ui/separator"
import { SidebarTrigger } from "@/components/ui/sidebar"
import { usePathname } from "next/navigation"
import { useAuth } from "@/contexts/auth-context"

export function SiteHeader() {
  const pathname = usePathname()
  const { user, authenticated } = useAuth()

  let title: string

  const getTitleFromPath = (pathname: string) => {
    console.log("Current pathname:", pathname)

    if (pathname.startsWith("/instellingen/oidc")) return "OIDC instellingen"
    if (pathname.startsWith("/instellingen/zgw-api"))
      return "ZGW API instellingen"
    if (pathname.startsWith("/instellingen")) return "Instellingen"

    return `Onbekende pagina (${pathname})`
  }

  if (!authenticated || !user) {
    title = "Welkom bij het CG DMF Admin Portal"
  } else if (pathname === "/") {
    title = `Welkom, ${user.name}`
  } else {
    title = getTitleFromPath(pathname)
  }

  return (
    <header className="flex h-(--header-height) shrink-0 items-center gap-2 border-b transition-[width,height] ease-linear group-has-data-[collapsible=icon]/sidebar-wrapper:h-(--header-height)">
      <div className="flex w-full items-center gap-1 px-4 lg:gap-2 lg:px-6">
        <SidebarTrigger className="-ml-1" />
        <Separator
          orientation="vertical"
          className="mx-2 data-[orientation=vertical]:h-4"
        />
        <h1 className="text-base font-medium">{title}</h1>
      </div>
    </header>
  )
}
