// SPDX-License-Identifier: EUPL-1.2
// Copyright (C) 2026 Gemeente Utrecht

export class ValidationError extends Error {
  constructor(message: string) {
    super(message)
    this.name = "ValidationError"
  }
}

/** Thrown when a fetch fails due to a network problem (no connection, DNS failure, etc.). */
export class NetworkError extends Error {
  constructor() {
    super(
      "Geen verbinding met de server. Controleer uw internetverbinding en probeer het opnieuw."
    )
    this.name = "NetworkError"
  }
}

/**
 * Read the `detail` field from an RFC 7807 problem+json response body.
 * Returns null if the body cannot be parsed or has no detail.
 */
export async function readDetail(res: Response): Promise<string | null> {
  try {
    const body = await res.json()
    return typeof body?.detail === "string" ? body.detail : null
  } catch {
    return null
  }
}

/**
 * Read the error detail from a non-ok Response and throw with a Dutch message.
 *
 * - `on409`: receives the backend `detail` string; return the message to throw.
 * - `on403`: message for Forbidden (e.g. read-only resource).
 * - `on404`: message for Not Found (defaults to generic).
 *
 * Falls back to the backend `detail` text, or a generic Dutch message.
 */
export async function throwOnError(
  res: Response,
  options: {
    on409?: (detail: string | null) => string
    on403?: string
    on404?: string
  } = {}
): Promise<never> {
  const detail = await readDetail(res)
  if (res.status === 409) {
    throw new Error(
      options.on409?.(detail) ??
        detail ??
        "Er bestaat al een item met deze gegevens."
    )
  }
  if (res.status === 403) {
    throw new Error(options.on403 ?? "Toegang geweigerd.")
  }
  if (res.status === 404) {
    throw new Error(
      options.on404 ?? "Niet gevonden. Ververs de pagina en probeer opnieuw."
    )
  }
  throw new Error(detail ?? "Er is een fout opgetreden. Probeer het opnieuw.")
}

/**
 * Parse an error message thrown by a server action.
 *
 * Actions encode field-level errors as `"field:<fieldName>:<message>"`.
 * Everything else is treated as a general (form-level) error.
 */
export function parseActionError(message: string | null): {
  field: string | null
  message: string
} {
  if (!message) return { field: null, message: "" }
  if (message.startsWith("field:")) {
    const second = message.indexOf(":", 6)
    if (second > 6) {
      return {
        field: message.slice(6, second),
        message: message.slice(second + 1),
      }
    }
  }
  return { field: null, message }
}
