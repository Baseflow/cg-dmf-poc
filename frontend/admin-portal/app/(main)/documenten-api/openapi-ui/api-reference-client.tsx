"use client"

import { ApiReferenceReact } from "@scalar/api-reference-react"
import "@scalar/api-reference-react/style.css"

export function ApiReferenceClient({ content }: { content: string }) {
  return (
    <ApiReferenceReact
      configuration={{
        content,
        layout: "modern",
        defaultOpenFirstTag: false,
        hideSearch: true,
      }}
    />
  )
}
