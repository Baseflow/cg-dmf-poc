export function SwaggerUiReference() {
  return (
    <iframe
      src={`${process.env.BACKEND_URL}/docs/swaggerui/documenten-api.html`}
      className="min-h-0 w-full flex-1 border-0"
      title="Documenten API – Swagger UI"
    />
  )
}
