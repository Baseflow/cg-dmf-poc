import { ApiReference } from "./api-reference"
export const dynamic = "force-dynamic"
export default function Page() {
  return (
    <div className="flex flex-1 flex-col">
      <ApiReference />
    </div>
  )
}
