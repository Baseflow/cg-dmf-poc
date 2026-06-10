"use client"

import dynamic from "next/dynamic"
import "swagger-ui-react/swagger-ui.css"

// swagger-ui-react v5 uses @swagger-api/apidom-core (ESM), which re-exports `refract` from
// minim — a CJS-only package. Turbopack cannot resolve named exports from CJS modules in ESM
// context, causing `ih.refract is not a function` at runtime. The build script forces --webpack
// (same as `next dev --webpack`) to sidestep this until apidom/minim ship proper ESM.
const SwaggerUI = dynamic(() => import("swagger-ui-react"), {
  ssr: false,
  loading: () => null,
})

export function SwaggerUiReferenceClient({ specUrl }: { specUrl: string }) {
  return (
    <div className="bg-white text-[#3b4151]" style={{ colorScheme: "light" }}>
      <SwaggerUI url={specUrl} />
    </div>
  )
}
