import { ZgwTokenGenerator } from "./zgw-token-generator"

export default function Page() {
  return (
    <div className="flex flex-1 flex-col gap-6 p-6">
      <div>
        <h1 className="text-xl font-semibold">ZGW Token Generator</h1>
        <p className="mt-1 text-sm text-muted-foreground">
          Genereer een ZGW bearer token op basis van Client ID en Client Secret (HMAC-SHA256).
        </p>
      </div>
      <div className="max-w-lg">
        <ZgwTokenGenerator />
      </div>
    </div>
  )
}