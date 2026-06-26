// SPDX-License-Identifier: EUPL-1.2
// Copyright (C) 2026 Gemeente Utrecht

const nlDateFormatter = new Intl.DateTimeFormat("nl-NL", {
  day: "numeric",
  month: "short",
  year: "numeric",
})

export function formatNlDate(dateString: string): string {
  return nlDateFormatter.format(new Date(dateString))
}

const BYTES_PER_MB = 1_048_576

export function bytesToMB(bytes: string): string {
  const n = Number(bytes)
  if (!Number.isFinite(n) || bytes === "") return ""
  return String(Math.round(n / BYTES_PER_MB))
}

export function mbToBytes(mb: string): string {
  const n = Number(mb)
  if (!Number.isFinite(n) || mb === "") return ""
  return String(Math.round(n * BYTES_PER_MB))
}
