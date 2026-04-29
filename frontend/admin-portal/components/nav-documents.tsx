"use client"

import Link from "next/link"
import {
  SidebarGroup,
  SidebarGroupLabel,
  SidebarMenu,
  SidebarMenuButton,
  SidebarMenuItem,
} from "@/components/ui/sidebar"

export function NavDocuments({
  items,
}: {
  items: {
    name: string
    url: string
    icon: React.ReactNode
  }[]
}) {
  return (
    <SidebarGroup className="group-data-[collapsible=icon]:hidden">
      <SidebarGroupLabel>Documenten</SidebarGroupLabel>
      <SidebarMenu>
        {items.map((item) => {
          const isExternal = item.url.startsWith("http")
          return (
            <SidebarMenuItem key={item.name}>
              <SidebarMenuButton asChild>
                {isExternal ? (
                  <a href={item.url} target="_blank" rel="noopener noreferrer">
                    {item.icon}
                    <span>{item.name}</span>
                  </a>
                ) : (
                  <Link href={item.url}>
                    {item.icon}
                    <span>{item.name}</span>
                  </Link>
                )}
              </SidebarMenuButton>
            </SidebarMenuItem>
          )
        })}
      </SidebarMenu>
    </SidebarGroup>
  )
}
