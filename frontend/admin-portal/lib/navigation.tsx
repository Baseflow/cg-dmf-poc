import { DockerIcon, GitHubIcon, OidcIcon } from "@/components/icons"
import {
  AppWindow,
  BookOpen,
  Braces,
  Database,
  FileCode,
  FileCog,
  LucideFileExclamationPoint,
  Plug,
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
  url?: string
  requiresAuth?: boolean
  items: NavItem[]
}

export const navigation: {
  primary: NavGroup[]
  secondary: NavItem[]
} = {
  primary: [
    {
      id: "instellingen",
      label: "Instellingen",
      url: "/instellingen",
      requiresAuth: true,
      items: [
        {
          name: "Repositories",
          url: "/instellingen/repositories",
          icon: <Database />,
          description: "Object store repositories",
        },
        {
          name: "DMF",
          url: "/instellingen/dmf",
          icon: <FileCog />,
          description: "DMF systeeminstellingen",
        },
        {
          name: "ZGW API",
          url: "/instellingen/zgw-api",
          icon: <Plug />,
          description: "ZGW API koppelingsprofielen",
        },
        {
          name: "Applicaties",
          url: "/instellingen/applicaties",
          icon: <AppWindow />,
          description: "Gekoppelde applicaties",
        },
        {
          name: "OIDC",
          url: "/instellingen/oidc",
          icon: <OidcIcon />,
          description: "OpenID Connect authenticatieproviders",
        },
      ],
    },
    {
      id: "documenten",
      label: "Documentatie",
      items: [
        {
          name: "OpenAPI UI",
          url: "/documenten-api/openapi-ui",
          icon: <BookOpen />,
        },
        {
          name: "OpenAPI (Swagger UI)",
          url: "/documenten-api/openapi-ui-swagger",
          icon: <FileCode />,
        },
        {
          name: "OpenAPI JSON",
          url: "/api/docs/openapi/documenten.json",
          icon: <Braces />,
        },
        {
          name: "OpenAPI YAML",
          url: "/api/docs/openapi/documenten.yaml",
          icon: <Braces />,
        },
      ],
    },
    {
      id: "links",
      label: "Links",
      items: [
        {
          name: "GitHub Repository",
          url: "https://github.com/Baseflow/cg-dmf-poc",
          icon: <GitHubIcon />,
        },
        {
          name: "Docker Image",
          url: "https://hub.docker.com/r/baseflow/cg-dmf-poc",
          icon: <DockerIcon />,
        },
      ],
    },
  ],
  secondary: [
    {
      name: "Problemen melden",
      url: "https://github.com/Baseflow/cg-dmf-poc/issues",
      icon: <LucideFileExclamationPoint />,
    },
  ],
}
