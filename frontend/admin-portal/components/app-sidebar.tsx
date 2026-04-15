"use client"

import * as React from "react"

import { NavDocuments } from "@/components/nav-documents"
import { NavMain } from "@/components/nav-main"
import { NavSecondary } from "@/components/nav-secondary"
import { NavUser } from "@/components/nav-user"
import {
  Sidebar,
  SidebarContent,
  SidebarFooter,
  SidebarHeader,
  SidebarMenu,
  SidebarMenuButton,
  SidebarMenuItem,
} from "@/components/ui/sidebar"
import { ListIcon, ChartBarIcon, FolderIcon, UsersIcon, FileTextIcon, LucideFileExclamationPoint } from "lucide-react"

function BaseflowAvatar() {
  return (
    <svg
      xmlns="http://www.w3.org/2000/svg"
      viewBox="0 0 24 24"
      fill="none"
      preserveAspectRatio="xMidYMid meet"
      aria-hidden="true"
      role="img"
    >
      <path d="M22.2307 5.66094C19.7812 5.8847 17.7199 6.55746 16.5374 7.46583C17.4473 6.28184 18.1185 4.22059 18.3423 1.77258C16.7449 0.77974 14.8985 0.151437 12.9187 0.00177002C12.9158 2.16378 12.569 4.13612 12.0015 5.64168C12.6742 7.42286 13.6582 8.54758 14.7547 8.54758C15.1311 8.54758 15.4942 8.41422 15.8365 8.16823C15.5905 8.51054 15.4571 8.87359 15.4571 9.24998C15.4571 10.3465 16.5804 11.3305 18.363 12.0032C19.8671 11.4357 21.8409 11.0889 24.0029 11.086C23.8533 9.10624 23.225 7.25986 22.2321 5.66243L22.2307 5.66094Z" fill="currentColor" />
      <path d="M11.0842 0.00148185C9.10445 0.151148 7.25807 0.779452 5.66064 1.77229C5.8844 4.22178 6.55716 6.28303 7.46553 7.46555C6.28153 6.55569 4.22028 5.88442 1.77227 5.66066C0.779435 7.25809 0.151131 9.10299 0.00146484 11.0842C2.16348 11.0872 4.13582 11.4339 5.64137 12.0015C7.42255 11.3287 8.54728 10.3448 8.54728 9.24821C8.54728 8.87182 8.41391 8.50877 8.16792 8.16646C8.51023 8.41245 8.87328 8.54581 9.24967 8.54581C10.3462 8.54581 11.3302 7.42257 12.0029 5.63991C11.4354 4.13584 11.0886 2.16202 11.0857 0L11.0842 0.00148185Z" fill="currentColor" />
      <path d="M24.0015 12.919C21.8395 12.9161 19.8671 12.5693 18.3616 12.0018C16.5804 12.6745 15.4557 13.6585 15.4557 14.755C15.4557 15.1314 15.589 15.4945 15.835 15.8368C15.4927 15.5908 15.1297 15.4574 14.7533 15.4574C13.6567 15.4574 12.6728 16.5807 12 18.3633C12.5675 19.8674 12.9143 21.8412 12.9173 24.0033C14.897 23.8536 16.7434 23.2253 18.3408 22.2324C18.1171 19.783 17.4443 17.7217 16.5359 16.5392C17.7199 17.449 19.7812 18.1203 22.2292 18.3441C23.222 16.7466 23.8503 14.9003 24 12.9205L24.0015 12.919Z" fill="currentColor" />
      <path d="M9.24821 15.4574C8.87182 15.4574 8.50728 15.5908 8.16646 15.8368C8.41244 15.4945 8.54581 15.1314 8.54581 14.755C8.54581 13.6585 7.42257 12.6745 5.63991 12.0018C4.13583 12.5693 2.16201 12.9161 0 12.919C0.149667 14.8988 0.77797 16.7452 1.77081 18.3426C4.2203 18.1188 6.28155 17.4461 7.46554 16.5362C6.55569 17.7202 5.88441 19.7815 5.65917 22.231C7.2566 23.2238 9.10299 23.8521 11.0827 24.0018C11.0857 21.8398 11.4324 19.8674 12 18.3619C11.3272 16.5807 10.3433 15.456 9.24672 15.456L9.24821 15.4574Z" fill="currentColor" />
    </svg>
  )
}

function GitHubIcon() {
  return (
    <svg
      xmlns="http://www.w3.org/2000/svg"
      viewBox="0 0 24 24"
      fill="none"
      preserveAspectRatio="xMidYMid meet"
      aria-hidden="true"
      role="img"
    >
      <path d="M12 0.297852C5.373 0.297852 0 5.67085 0 12.2979C0 17.6689 3.438 22.2999 8.205 23.9979C8.805 24.0999 9.025 23.7379 9.025 23.4179C9.025 23.1179 9.015 22.3879 9.01 21.4979C5.6725 22.1979 4.9675 20.0079 4.9675 20.0079C4.4225 18.6379 3.6325 18.2679 3.6325 18.2679C2.545 17.5779 3.7175 17.5939 3.7175 17.5939C4.9225 17.6839 5.555 18.8479 5.555 18.8479C6.645 20.6279 8.4225 20.1179 9.105 19.7979C9.205 19.0179 9.525 18.4979 9.875 18.1879C7.205 17.8779 4.415 16.7979 4.415 11.9979C4.415 10.6179 4.885 9.49785 5.665 8.6679C5.545 8.35789 5.125 7.03789 5.785 5.29789C5.785 5.29789 6.805 4.97789 8.995 6.66789C9.995 6.36789 11.045 6.21789 12.095 6.21289C13.145 6.21789 14.195 6.36789 15.195 6.66789C17.385 4.97789 18.405 5.29789 18.405 5.29789C19.065 7.03789 18.645 8.35789 18.525 8.6679C19.305 9.4979 19.775 10.6179 19.775 11.9979C19.775 16.8079 17.985 17.8779 15.315 18.1879C15.725 18.5479 16.125 19.2979 16.125 20.4979C16.125 22.1979 16.115 23.2879 16.115 23.6679C16.115 23.9879 16.335 24.3579 16.935 23.9979C21.7025 22.2999 25.1405 17.6689 25.1405 12.2979C25.1405 5.67085 19.7675 0.297852 13.1405 0.297852H12Z" fill="currentColor" />
    </svg>
  )
}

function DockerIcon() {
  return (
    <svg
      xmlns="http://www.w3.org/2000/svg"
      viewBox="0 0 24 24"
      fill="none"
      preserveAspectRatio="xMidYMid meet"
      aria-hidden="true"
      role="img">

      <path
        fill="currentColor"
        d="M13.983 11.078h2.119a.186.186 0 0 0 .186-.185V9.006a.186.186 0 0 0-.186-.186h-2.119a.185.185 0 0 0-.185.185v1.888c0 .102.083.185.185.185m-2.954-5.43h2.118a.186.186 0 0 0 .186-.186V3.574a.186.186 0 0 0-.186-.185h-2.118a.185.185 0 0 0-.185.185v1.888c0 .102.082.185.185.185m0 2.716h2.118a.187.187 0 0 0 .186-.186V6.29a.186.186 0 0 0-.186-.185h-2.118a.185.185 0 0 0-.185.185v1.887c0 .102.082.185.185.186m-2.93 0h2.12a.186.186 0 0 0 .184-.186V6.29a.185.185 0 0 0-.185-.185H8.1a.185.185 0 0 0-.185.185v1.887c0 .102.083.185.185.186m-2.964 0h2.119a.186.186 0 0 0 .185-.186V6.29a.185.185 0 0 0-.185-.185H5.136a.186.186 0 0 0-.186.185v1.887c0 .102.084.185.186.186m5.893 2.715h2.118a.186.186 0 0 0 .186-.185V9.006a.186.186 0 0 0-.186-.186h-2.118a.185.185 0 0 0-.185.185v1.888c0 .102.082.185.185.185m-2.93 0h2.12a.185.185 0 0 0 .184-.185V9.006a.185.185 0 0 0-.184-.186h-2.12a.185.185 0 0 0-.184.185v1.888c0 .102.083.185.185.185m-2.964 0h2.119a.185.185 0 0 0 .185-.185V9.006a.185.185 0 0 0-.184-.186h-2.12a.186.186 0 0 0-.186.186v1.887c0 .102.084.185.186.185m-2.92 0h2.12a.185.185 0 0 0 .184-.185V9.006a.185.185 0 0 0-.184-.186h-2.12a.185.185 0 0 0-.184.185v1.888c0 .102.082.185.185.185M23.763 9.89c-.065-.051-.672-.51-1.954-.51q-.508.001-1.01.087c-.248-1.7-1.653-2.53-1.716-2.566l-.344-.199l-.226.327c-.284.438-.49.922-.612 1.43c-.23.97-.09 1.882.403 2.661c-.595.332-1.55.413-1.744.42H.751a.75.75 0 0 0-.75.748a11.4 11.4 0 0 0 .692 4.062c.545 1.428 1.355 2.48 2.41 3.124c1.18.723 3.1 1.137 5.275 1.137a15.7 15.7 0 0 0 2.93-.266a12.3 12.3 0 0 0 3.823-1.389a10.5 10.5 0 0 0 2.61-2.136c1.252-1.418 1.998-2.997 2.553-4.4h.221c1.372 0 2.215-.549 2.68-1.009c.309-.293.55-.65.707-1.046l.098-.288Z" />

    </svg>
  )
}

const data = {
  navMain: [
    {
      title: "Lifecycle",
      url: "#",
      icon: (
        <ListIcon
        />
      ),
    },
    {
      title: "Analytics",
      url: "#",
      icon: (
        <ChartBarIcon
        />
      ),
    },
    {
      title: "Projects",
      url: "#",
      icon: (
        <FolderIcon
        />
      ),
    },
    {
      title: "Team",
      url: "#",
      icon: (
        <UsersIcon
        />
      ),
    },
  ],
  documents: [
    {
      name: "OpenAPI Spec",
      url: "https://cg-dmf.dev.baseflow.com/docs",
      icon: (
        <FileTextIcon />
      ),
    },
    {
      name: "Docker Image",
      url: "https://hub.docker.com/r/baseflow/cg-dmf-poc",
      icon: <DockerIcon />
    },
    {
      name: "Link to GitHub",
      url: "https://github.com/Baseflow/cg-dmf-poc",
      icon: (
        <GitHubIcon />
      ),
    },
  ],
  NavSecondary: [
    {
      title: "Rapporteer problemen",
      url: "https://github.com/Baseflow/cg-dmf-poc/issues",
      icon: (
        <LucideFileExclamationPoint />
      ),
    },
  ],    
}

export function AppSidebar({ ...props }: React.ComponentProps<typeof Sidebar>) {
  return (
    <Sidebar collapsible="offcanvas" {...props}>
      <SidebarHeader>
        <SidebarMenu>
          <SidebarMenuItem>
            <SidebarMenuButton
              asChild
              className="data-[slot=sidebar-menu-button]:p-1.5!"
            >
              <a href="#" className="flex items-center gap-2">
                <span className="size-5 text-foreground [&>svg]:size-full">
                  <BaseflowAvatar />
                </span>
                <span className="text-base font-semibold">Baseflow</span>
              </a>
            </SidebarMenuButton>
          </SidebarMenuItem>
        </SidebarMenu>
      </SidebarHeader>
      <SidebarContent>
        <NavMain items={data.navMain} />
        <NavDocuments items={data.documents} />
        <NavSecondary items={data.NavSecondary} className="mt-auto"/>
      </SidebarContent>
      <SidebarFooter>
        <NavUser />
      </SidebarFooter>
    </Sidebar>
  )
}
