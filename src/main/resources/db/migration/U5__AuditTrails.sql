-- SPDX-License-Identifier: EUPL-1.2
-- Copyright (C) 2026 Gemeente Utrecht

ALTER TABLE eio_versions ALTER COLUMN trefwoorden TYPE TEXT[], ALTER COLUMN trefwoorden SET DEFAULT ARRAY[]::text[];
DROP TABLE IF EXISTS audit_trails;