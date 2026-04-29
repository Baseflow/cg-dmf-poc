"use client"

import { ApiReferenceReact } from "@scalar/api-reference-react"
import "@scalar/api-reference-react/style.css"
import { useTheme } from "next-themes"

export function ApiReference({ specUrl }: { specUrl: string }) {
  const { resolvedTheme } = useTheme()

  return (
    <ApiReferenceReact
      configuration={{
        url: specUrl,
        darkMode: resolvedTheme === "dark",
        layout: "modern",
      }}
    />
  )
}
