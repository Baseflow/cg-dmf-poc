-- SPDX-License-Identifier: EUPL-1.2
-- Copyright (C) 2026 Gemeente Utrecht
--
-- V14: Add admin-managed fields to blob_storage_repositories so secrets can be
--      stored encrypted (revealable) and repositories can be toggled on/off.

ALTER TABLE blob_storage_repositories
    ADD COLUMN access_key_encrypted  TEXT,
    ADD COLUMN secret_key_encrypted  TEXT,
    ADD COLUMN storage_account_name  VARCHAR(255),
    ADD COLUMN enabled               BOOLEAN NOT NULL DEFAULT TRUE;
