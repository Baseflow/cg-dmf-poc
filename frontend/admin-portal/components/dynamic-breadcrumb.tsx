"use client"

import {
  Breadcrumb,
  BreadcrumbItem,
  BreadcrumbLink,
  BreadcrumbList,
  BreadcrumbPage,
  BreadcrumbSeparator,
} from "@/components/ui/breadcrumb"
import { navigation } from "@/lib/navigation"
import { usePathname } from "next/navigation"
import React from "react"

type Crumb =
  | { type: "label"; label: string }
  | { type: "link"; label: string; href: string }
  | { type: "page"; label: string }

function buildCrumbs(pathname: string): Crumb[] {
  for (const group of navigation.primary) {
    const match = group.items.find(
      (item) => pathname === item.url || pathname.startsWith(item.url + "/")
    )
    if (!match) continue

    const crumbs: Crumb[] = [
      group.url
        ? { type: "link", label: group.label, href: group.url }
        : { type: "label", label: group.label },
    ]

    const isExact = pathname === match.url
    if (isExact) {
      crumbs.push({ type: "page", label: match.name })
    } else {
      crumbs.push({ type: "link", label: match.name, href: match.url })
      const remaining = pathname
        .slice(match.url.length)
        .split("/")
        .filter(Boolean)
      remaining.forEach((seg, i) => {
        const href = match.url + "/" + remaining.slice(0, i + 1).join("/")
        const isLast = i === remaining.length - 1
        const label = seg.charAt(0).toUpperCase() + seg.slice(1)
        crumbs.push(
          isLast ? { type: "page", label } : { type: "link", label, href }
        )
      })
    }

    return crumbs
  }

  // Fallback for paths not in the navigation object
  const segments = pathname.split("/").filter(Boolean)
  return segments.map((seg, i) => {
    const href = "/" + segments.slice(0, i + 1).join("/")
    // Check if segment matches a nav item URL segment — use its name if so
    const navItem = navigation.primary
      .flatMap((g) => g.items)
      .find((item) => item.url === href)
    const label =
      navItem?.name ??
      seg
        .split("-")
        .map((word) => word.charAt(0).toUpperCase() + word.slice(1))
        .join(" ")
    return i === segments.length - 1
      ? { type: "page", label }
      : { type: "link", label, href }
  })
}

export function DynamicBreadcrumb() {
  const pathname = usePathname()
  const crumbs = buildCrumbs(pathname)

  return (
    <Breadcrumb>
      <BreadcrumbList>
        {crumbs.map((crumb, index) => (
          <React.Fragment key={index}>
            {index > 0 && <BreadcrumbSeparator />}
            <BreadcrumbItem>
              {crumb.type === "label" && (
                <span className="text-muted-foreground">{crumb.label}</span>
              )}
              {crumb.type === "link" && (
                <BreadcrumbLink href={crumb.href}>{crumb.label}</BreadcrumbLink>
              )}
              {crumb.type === "page" && (
                <BreadcrumbPage>{crumb.label}</BreadcrumbPage>
              )}
            </BreadcrumbItem>
          </React.Fragment>
        ))}
      </BreadcrumbList>
    </Breadcrumb>
  )
}
