-- SPDX-License-Identifier: EUPL-1.2
-- Copyright (C) 2026 Gemeente Utrecht
--
-- V28: Add readonly column to application_settings table.
--      Env-imported credentials are marked readonly = true to prevent accidental modification.

ALTER TABLE application_settings
    ADD COLUMN IF NOT EXISTS readonly BOOLEAN NOT NULL DEFAULT FALSE;

