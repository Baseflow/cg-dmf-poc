-- SPDX-License-Identifier: EUPL-1.2
-- Copyright (C) 2026 Gemeente Utrecht
--
-- U31: Undo V31 – revert integriteits_datum and ondertekenings_datum back to TIMESTAMP.

ALTER TABLE eio_versions
    ALTER COLUMN integriteits_datum TYPE TIMESTAMP USING integriteits_datum::TIMESTAMP,
    ALTER COLUMN ondertekenings_datum TYPE TIMESTAMP USING ondertekenings_datum::TIMESTAMP;
