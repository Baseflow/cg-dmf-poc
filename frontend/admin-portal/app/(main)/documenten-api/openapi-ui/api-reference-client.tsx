"use client"

import { ApiReferenceReact } from "@scalar/api-reference-react"
import "@scalar/api-reference-react/style.css"
import { useTheme } from "next-themes"

export function ApiReferenceClient({ content }: { content: string }) {
  const { theme } = useTheme()

  return (
    <ApiReferenceReact
      configuration={{
        content,
        darkMode: theme === "dark",
        layout: "modern",
        defaultOpenFirstTag: false,
        hideSearch: true,
      }}
    />
  )
}
