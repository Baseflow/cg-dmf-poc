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
