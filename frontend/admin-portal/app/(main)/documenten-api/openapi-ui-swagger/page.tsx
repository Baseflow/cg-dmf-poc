import { SwaggerUiReference } from "./swagger-ui-reference"

export const dynamic = "force-dynamic"

export default function Page() {
  return (
    <div className="flex flex-1 flex-col overflow-hidden">
      <SwaggerUiReference />
    </div>
  )
}
