-- SPDX-License-Identifier: EUPL-1.2
-- Copyright (C) 2026 Gemeente Utrecht
--
-- V10: Remove pgcrypto dependency introduced by the original V7.
-- The application generates UUIDs client-side (Exposed UUIDTable),
-- so the DEFAULT gen_random_uuid() on blob_storage_repositories.id
-- was unnecessary and required the pgcrypto extension.
--
-- This migration is idempotent: it safely handles both systems that
-- ran the original V7 (with pgcrypto) and those that ran the amended V7.

-- Remove the DEFAULT if it exists (no-op if already absent)
ALTER TABLE blob_storage_repositories ALTER COLUMN id DROP DEFAULT;
