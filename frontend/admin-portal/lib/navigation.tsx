import { DockerIcon, GitHubIcon, OidcIcon } from "@/components/icons"
import {
  Database,
  FileText,
  FileTextIcon,
  Layers,
  LayoutGrid,
  LucideFileExclamationPoint,
} from "lucide-react"

export const navigation = {
  primary: {
    documents: [
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
    settings: [
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

  secondary: [
    {
      title: "Rapporteer problemen",
      url: "https://github.com/Baseflow/cg-dmf-poc/issues",
      icon: <LucideFileExclamationPoint />,
    },
  ],
}
