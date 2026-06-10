"use client"

import dynamic from "next/dynamic"
import "@scalar/api-reference-react/style.css"
import { useMemo } from "react"

const ApiReferenceReact = dynamic(
  () =>
    import("@scalar/api-reference-react").then((m) => m.ApiReferenceReact),
  { ssr: false }
)

const CUSTOM_CSS = `
  :root {
    --app-header-offset: calc(16 * var(--spacing));
    --scalar-sidebar-sticky-offset: var(--app-header-offset);
    --refs-viewport-offset: var(--app-header-offset);
    --scalar-custom-header-height: var(--app-header-offset);
    --scalar-font: var(--font-sans);
    --scalar-font-code: var(--font-mono);
  }
  .scalar-api-reference {
    --refs-sidebar-height: calc(100dvh - var(--app-header-offset));
  }
`

export function ApiReferenceClient({ content }: { content: string }) {
  const configuration = useMemo(
    () => ({
      content,
      layout: "modern" as const,
      defaultOpenFirstTag: false,
      hideSearch: true,
      withDefaultFonts: false,
      agent: { disabled: true },
      telemetry: false,
      customCss: CUSTOM_CSS,
    }),
    [content]
  )

  return <ApiReferenceReact configuration={configuration} />
}