"use client"

import dynamic from "next/dynamic"
import "swagger-ui-react/swagger-ui.css"

const SwaggerUI = dynamic(() => import("swagger-ui-react"), {
  ssr: false,
  loading: () => null,
})

export function SwaggerUiReferenceClient({ spec }: { spec: object }) {
  return <SwaggerUI spec={spec} />
}
