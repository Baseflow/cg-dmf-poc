import { DockerIcon, GitHubIcon } from "@/components/icons"
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
  verkenner: NavItem[]
} = {
  primary: [
    {
      id: "instellingen",
      label: "Instellingen",
      url: "/instellingen",
      requiresAuth: true,
      items: [
        {
          name: "Opslag repositories",
          url: "/instellingen/repositories",
          icon: <Database />,
          description:
            "Objectopslag configureren voor het opslaan van documenten",
        },
        {
          name: "DMF instellingen",
          url: "/instellingen/dmf",
          icon: <FileCog />,
          description: "Configureer systeeminstellingen",
        },
        {
          name: "API koppelingen",
          url: "/instellingen/api-koppelingen",
          icon: <Plug />,
          description: "Configureer verbindingen en toegang tot externe APIs",
        },
        {
          name: "Applicaties",
          url: "/instellingen/applicaties",
          icon: <AppWindow />,
          description:
            "Configureer externe applicaties die toegang tot dit systeem benodigd zijn",
        },
      ],
    },
    {
      id: "documenten",
      label: "Documentatie",
      items: [
        {
          name: "Handleiding",
          url: "/docs",
          icon: <BookOpen />,
          description:
            "Installatie, configuratie en ontwikkeling handleiding van de DMF DRC implementatie",
        },
        {
          name: "API verkenner",
          url: "/documenten-verkenner",
          icon: <FileCode />,
          description: "API browsers en OpenAPI specificaties",
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
  verkenner: [
    {
      name: "Documenten API",
      url: "/documenten-api/openapi-ui",
      icon: <FileCode />,
      description:
        "Verkenner van de Common Ground Documenten API zoals geïmplementeerd",
    },
    {
      name: "Documenten API (Swagger)",
      url: "/documenten-api/openapi-ui-swagger",
      icon: <FileCode />,
      description:
        "Swagger UI verkenner van Common Ground Documenten API zoals geïmplementeerd",
    },
    {
      name: "WOPI API",
      url: "/wopi/openapi-ui",
      icon: <FileCode />,
      description: "Scalar verkenner WOPI API",
    },
    {
      name: "Documenten API JSON",
      url: "/api/docs/openapi/documenten.json",
      icon: <Braces />,
      description:
        "OpenAPI JSON specificatie van de Common Ground Documenten API zoals geïmplementeerd",
    },
    {
      name: "Documenten API YAML",
      url: "/api/docs/openapi/documenten.yaml",
      icon: <Braces />,
      description:
        "OpenAPI YAML specificatie van de Common Ground Documenten API zoals geïmplementeerd",
    },
    {
      name: "WOPI API JSON",
      url: "/api/docs/openapi/wopi.json",
      icon: <Braces />,
      description: "OpenAPI JSON specificatie van de WOPI API",
    },
    {
      name: "WOPI API YAML",
      url: "/api/docs/openapi/wopi.yaml",
      icon: <Braces />,
      description: "OpenAPI YAML specificatie van de WOPI API",
    },
    {
      name: "Settings API",
      url: "/settings/openapi-ui",
      icon: <FileCode />,
      description: "Scalar verkenner van de interne Settings API",
    },
    {
      name: "Settings API JSON",
      url: "/api/docs/openapi/settings.json",
      icon: <Braces />,
      description: "OpenAPI JSON specificatie van de Settings API",
    },
    {
      name: "Settings API YAML",
      url: "/api/docs/openapi/settings.yaml",
      icon: <Braces />,
      description: "OpenAPI YAML specificatie van de Settings API",
    },
  ],
}
