import { DockerIcon, GitHubIcon, OidcIcon } from "@/components/icons"
import {
  Database,
  FileText,
  FileTextIcon,
  Layers,
  LayoutGrid,
  LucideFileExclamationPoint,
} from "lucide-react"
import type { ReactNode } from "react"

export type NavItem = {
  name: string
  url: string
  icon: ReactNode
  description?: string
}

export type NavGroup = {
  id: string
  label: string
  requiresAuth?: boolean
  items: NavItem[]
}

export const navigation: {
  primary: NavGroup[]
  secondary: NavItem[]
} = {
  primary: [
    {
      id: "documenten",
      label: "Documenten",
      items: [
        {
          name: "OpenAPI Spec",
          url: "/api-docs",
          icon: <FileTextIcon />,
        },
        {
          name: "Docker Image",
          url: "https://hub.docker.com/r/baseflow/cg-dmf-poc",
          icon: <DockerIcon />,
        },
        {
          name: "GitHub Repository",
          url: "https://github.com/Baseflow/cg-dmf-poc",
          icon: <GitHubIcon />,
        },
      ],
    },
    {
      id: "instellingen",
      label: "Instellingen",
      requiresAuth: true,
      items: [
        {
          name: "OIDC",
          url: "/instellingen/oidc",
          icon: <OidcIcon />,
          description: "OpenID Connect authenticatieproviders",
        },
        {
          name: "ZGW API",
          url: "/instellingen/zgw-api",
          icon: <Layers />,
          description: "ZGW API-koppelingsprofielen",
        },
        {
          name: "DMF",
          url: "/instellingen/dmf",
          icon: <FileText />,
          description: "DMF-systeeminstellingen",
        },
        {
          name: "Repositories",
          url: "/instellingen/repositories",
          icon: <Database />,
          description: "Object store repositories",
        },
        {
          name: "Applicaties",
          url: "/instellingen/applicaties",
          icon: <LayoutGrid />,
          description: "Gekoppelde applicaties",
        },
      ],
    },
  ],
  secondary: [
    {
      name: "Rapporteer problemen",
      url: "https://github.com/Baseflow/cg-dmf-poc/issues",
      icon: <LucideFileExclamationPoint />,
    },
  ],
}
