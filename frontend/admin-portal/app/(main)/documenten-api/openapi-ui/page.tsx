import { ApiReference } from "./api-reference"

export default function Page() {
  return (
    <div className="flex flex-1 flex-col overflow-hidden">
      <ApiReference specUrl="/api/openapi" />
    </div>
  )
}
