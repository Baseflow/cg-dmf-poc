-- SPDX-License-Identifier: EUPL-1.2
-- Copyright (C) 2026 Gemeente Utrecht

-- Undo migration for V6__ChangeWijzigingenToJson.sql
-- Revert `wijzigingen` column from JSON back to TEXT and `trefwoorden` back to TEXT[].
-- NOTE: This undo assumes the previous types were TEXT (for wijzigingen) and TEXT[] (for trefwoorden).
-- If your previous schema used different defaults or types, adjust accordingly before running.

ALTER TABLE audit_trails
    ALTER COLUMN wijzigingen TYPE TEXT USING wijzigingen::text,
    ALTER COLUMN wijzigingen SET DEFAULT ''::text;

ALTER TABLE eio_versions
    ALTER COLUMN trefwoorden TYPE TEXT[] USING trefwoorden::text[],
    ALTER COLUMN trefwoorden SET DEFAULT ARRAY[]::text[];
