"use client"

import dynamic from "next/dynamic"
import type { SwaggerUIProps } from "swagger-ui-react"
import "swagger-ui-react/swagger-ui.css"

const SwaggerUI = dynamic<SwaggerUIProps>(() => import("swagger-ui-react"), {
  ssr: false,
  loading: () => null,
})

export function SwaggerUiReferenceClient({ spec }: { spec: object }) {
  return <SwaggerUI spec={spec} />
}
