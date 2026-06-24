-- SPDX-License-Identifier: EUPL-1.2
-- Copyright (C) 2026 Gemeente Utrecht
--
-- V33: Add readonly column to blob_storage_repository_settings table.
--      Env-configured repositories are marked readonly = true to prevent accidental modification.

ALTER TABLE blob_storage_repository_settings
    ADD COLUMN IF NOT EXISTS readonly BOOLEAN NOT NULL DEFAULT FALSE;