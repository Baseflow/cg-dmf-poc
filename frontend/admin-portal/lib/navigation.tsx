import { DockerIcon, GitHubIcon, OidcIcon } from "@/components/icons"
import {
  DatabaseIcon,
  FanIcon,
  FileTextIcon,
  KeyIcon,
  LucideFileExclamationPoint,
} from "lucide-react"

export const navigation = {
  settings: [
    {
      name: "OIDC",
      url: "/instellingen/oidc",
      icon: <OidcIcon />,
    },
    {
      name: "ZGW API",
      url: "/instellingen/zgw-api",
      icon: <FanIcon />,
    },
    {
      name: "DMF",
      url: "/instellingen/dmf",
      icon: <FileTextIcon />,
    },
    {
      name: "Repositories",
      url: "/instellingen/repositories",
      icon: <DatabaseIcon />,
    },
    {
      name: "Applicaties",
      url: "/instellingen/applicatie-instellingen",
      icon: <KeyIcon />,
    },
  ],
  documents: [
    {
      name: "OpenAPI Spec",
      url: "https://cg-dmf.dev.baseflow.com/docs",
      icon: <FileTextIcon />,
    },
    {
      name: "Docker Image",
      url: "https://hub.docker.com/r/baseflow/cg-dmf-poc",
      icon: <DockerIcon />,
    },
    {
      name: "Link naar GitHub",
      url: "https://github.com/Baseflow/cg-dmf-poc",
      icon: <GitHubIcon />,
    },
  ],
  navSecondary: [
    {
      title: "Rapporteer problemen",
      url: "https://github.com/Baseflow/cg-dmf-poc/issues",
      icon: <LucideFileExclamationPoint />,
    },
  ],
}
