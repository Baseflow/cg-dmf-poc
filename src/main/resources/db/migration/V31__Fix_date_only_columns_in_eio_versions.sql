-- SPDX-License-Identifier: EUPL-1.2
-- Copyright (C) 2026 Gemeente Utrecht
--
-- integriteits_datum and ondertekenings_datum are date-only fields (ISO 8601 'date' format)
-- per the Documenten API 1.6.0 spec. They were incorrectly stored as TIMESTAMP.
-- Values were always written as midnight (00:00:00), so DATE(column) is lossless.

ALTER TABLE eio_versions
    ALTER COLUMN integriteits_datum TYPE DATE USING integriteits_datum::DATE,
    ALTER COLUMN ondertekenings_datum TYPE DATE USING ondertekenings_datum::DATE;
